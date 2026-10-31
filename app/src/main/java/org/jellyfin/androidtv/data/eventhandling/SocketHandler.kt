package org.jellyfin.androidtv.data.eventhandling

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jellyfin.androidtv.data.model.DataRefreshService
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.playback.PlaybackControllerContainer
import org.jellyfin.androidtv.util.apiclient.PlaybackHelper
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.sockets.SocketApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.api.sockets.subscribeGeneralCommands
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.LibraryUpdateInfo
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMessage
import org.jellyfin.sdk.model.api.PlayRequest
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.PlaystateRequest
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.extensions.getValue
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID

class SocketHandler(
	private val context: Context,
	private val api: ApiClient,
	private val socket: SocketApi,
	private val dataRefreshService: DataRefreshService,
	private val mediaManager: MediaManager,
	private val playbackControllerContainer: PlaybackControllerContainer,
) {
	private val coroutineScope = CoroutineScope(Dispatchers.IO)

	suspend fun updateSession() {
		try {
			api.sessionApi.postCapabilities(
				playableMediaTypes = listOf(MediaType.VIDEO, MediaType.AUDIO),
				supportsMediaControl = true,
				supportedCommands = listOf(
					GeneralCommandType.DISPLAY_MESSAGE,
					GeneralCommandType.SEND_STRING,
				),
			)
		} catch (err: ApiClientException) {
			Timber.e(err, "Unable to update capabilities")
			return
		}
	}

	init {
		// Library
		socket.subscribe<LibraryChangedMessage>()
			.mapNotNull { it.data }
			.onEach(::onLibraryChanged)
			.launchIn(coroutineScope)

		// Media playback
		socket.subscribe<PlayMessage>()
			.mapNotNull { it.data }
			.onEach(::onPlayMessage)
			.launchIn(coroutineScope)
		socket.subscribe<PlaystateMessage>()
			.mapNotNull { it.data }
			.onEach { onPlayStateMessage(it) }
			.launchIn(coroutineScope)

		// General commands
		socket.subscribeGeneralCommands(setOf(GeneralCommandType.DISPLAY_CONTENT))
			.onEach { message ->
				val itemId by message
				val itemUuid = itemId?.toUUIDOrNull()

				if (itemUuid != null) onDisplayContent(itemUuid)
			}
			.launchIn(coroutineScope)
		socket.subscribeGeneralCommands(setOf(GeneralCommandType.DISPLAY_MESSAGE, GeneralCommandType.SEND_STRING))
			.onEach { message ->
				val header by message
				val text by message
				val string by message

				onDisplayMessage(header, text ?: string)
			}
			.launchIn(coroutineScope)
	}

	private fun onLibraryChanged(info: LibraryUpdateInfo) {
		Timber.d(buildString {
			appendLine("Library changed.")
			appendLine("Added ${info.itemsAdded.size} items")
			appendLine("Removed ${info.itemsRemoved.size} items")
			appendLine("Updated ${info.itemsUpdated.size} items")
		})

		if (info.itemsAdded.any() || info.itemsRemoved.any())
			dataRefreshService.lastLibraryChange = System.currentTimeMillis()
	}

	private fun onPlayMessage(request: PlayRequest) {
		val itemId = request.itemIds?.firstOrNull() ?: return

		PlaybackHelper.retrieveAndPlay(
			itemId.toString(),
			false,
			request.startPositionTicks,
			context
		)
	}

	@Suppress("ComplexMethod")
	private fun onPlayStateMessage(request: PlaystateRequest) = coroutineScope.launch(Dispatchers.Main) {
		Timber.i("Received PlayStateMessage with command ${request.command}")
		val playbackController = playbackControllerContainer.playbackController
		// Audio playback uses the mediaManager, video playback and live tv use the playbackController
		if (mediaManager.isAudioPlayerInitialized) when (request.command) {
			PlaystateCommand.STOP -> mediaManager.stopAudio(true)
			PlaystateCommand.PAUSE, PlaystateCommand.UNPAUSE, PlaystateCommand.PLAY_PAUSE -> mediaManager.playPauseAudio()
			PlaystateCommand.NEXT_TRACK -> mediaManager.nextAudioItem()
			PlaystateCommand.PREVIOUS_TRACK -> mediaManager.prevAudioItem()
			// Not implemented
			PlaystateCommand.SEEK,
			PlaystateCommand.REWIND,
			PlaystateCommand.FAST_FORWARD -> Unit
		} else when (request.command) {
			PlaystateCommand.STOP -> playbackController?.endPlayback(true)
			PlaystateCommand.PAUSE, PlaystateCommand.UNPAUSE, PlaystateCommand.PLAY_PAUSE -> playbackController?.playPause()
			PlaystateCommand.NEXT_TRACK -> playbackController?.next()
			PlaystateCommand.PREVIOUS_TRACK -> playbackController?.prev()
			PlaystateCommand.SEEK -> playbackController?.seek(
				(request.seekPositionTicks ?: 0) / TICKS_TO_MS
			)
			// FIXME get rewind/forward amount from displayprefs
			PlaystateCommand.REWIND -> playbackController?.skip(REWIND_MS)
			PlaystateCommand.FAST_FORWARD -> playbackController?.skip(FORWARD_MS)
		}
	}

	// FIXME: Add an ItemLauncher function that accepts SDK BaseItemDto
	// Add "GeneralCommandType.DISPLAY_CONTENT" to supportedCommands in capabilities to receive
	// these messages. Otherwise this function is never called.
	private fun onDisplayContent(itemId: UUID) {
		val playbackController = playbackControllerContainer.playbackController

		if (playbackController?.isPlaying == true || playbackController?.isPaused == true) {
			Timber.i("Not launching $itemId: playback in progress")
			return
		}

		Timber.i("Launching $itemId")

		coroutineScope.launch {
			val item by api.userLibraryApi.getItem(itemId = itemId)
			// ItemLauncher.launch(item)
		}
	}

	private fun onDisplayMessage(header: String?, text: String?) {
		val toastMessage = buildString {
			if (!header.isNullOrBlank()) append(header, ": ")
			append(text)
		}

		runBlocking(Dispatchers.Main) {
			Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
		}
	}

	companion object {
		const val TICKS_TO_MS = 10000L
		const val REWIND_MS = -11000
		const val FORWARD_MS = 30000
	}
}

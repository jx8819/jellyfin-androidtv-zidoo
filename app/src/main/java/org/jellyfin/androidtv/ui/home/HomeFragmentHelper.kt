package org.jellyfin.androidtv.ui.home

import android.content.Context
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.ChangeTriggerType
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.data.querying.StdItemQuery
import org.jellyfin.androidtv.data.querying.ViewQuery
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.jellyfin.androidtv.preference.UserSettingPreferences.Companion.cardInfoType
import org.jellyfin.androidtv.ui.browsing.BrowseRowDef
import org.jellyfin.androidtv.util.SmallListRow
import org.jellyfin.androidtv.util.SmallListRowAbs
import org.jellyfin.androidtv.util.ThumbListRow
import org.jellyfin.apiclient.model.entities.LocationType
import org.jellyfin.apiclient.model.entities.SortOrder
import org.jellyfin.apiclient.model.livetv.RecommendedProgramQuery
import org.jellyfin.apiclient.model.livetv.RecordingQuery
import org.jellyfin.apiclient.model.querying.ItemFields
import org.jellyfin.apiclient.model.querying.ItemFilter
import org.jellyfin.apiclient.model.querying.ItemsResult
import org.jellyfin.apiclient.model.querying.NextUpQuery
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType

class HomeFragmentHelper(
	private val context: Context,
	private val userRepository: UserRepository,
	private val userSettingPreferences: UserSettingPreferences
) {
	fun loadRecentlyAdded(views: ItemsResult): HomeFragmentRow {
		return HomeFragmentLatestRow(context, userRepository, userSettingPreferences, views)
	}

	fun loadLibraryTiles(isButtonRow: Boolean = false): HomeFragmentRow {
		val query = ViewQuery()
		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_my_media), query), if(isButtonRow) SmallListRowAbs::class else SmallListRow::class)
	}

	fun loadResume(title: String, includeMediaTypes: Array<String>): HomeFragmentRow {
		val query = StdItemQuery().apply {
			mediaTypes = includeMediaTypes
			recursive = true
			imageTypeLimit = 1
			enableTotalRecordCount = false
			collapseBoxSetItems = false
			excludeLocationTypes = arrayOf(LocationType.Virtual)
			limit = ITEM_LIMIT_RESUME
			filters = arrayOf(ItemFilter.IsResumable)
			sortBy = arrayOf(ItemSortBy.DATE_PLAYED.serialName)
			sortOrder = SortOrder.Descending
		}

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(title, query, DEFAULT_TRIGGERS), ThumbListRow::class, userSettingPreferences[cardInfoType], ImageType.THUMB)
	}

	fun loadResumeVideo(): HomeFragmentRow {
		return loadResume(context.getString(R.string.lbl_continue_watching), arrayOf(MediaType.VIDEO.serialName))
	}

	fun loadResumeAudio(): HomeFragmentRow {
		return loadResume(context.getString(R.string.lbl_continue_watching), arrayOf(MediaType.AUDIO.serialName))
	}

	fun loadLatestLiveTvRecordings(): HomeFragmentRow {
		val query = RecordingQuery().apply {
			fields = arrayOf(
				ItemFields.Overview,
				ItemFields.PrimaryImageAspectRatio,
				ItemFields.ChildCount
			)

			userId = userRepository.currentUser.value!!.id.toString()
			enableImages = true
			limit = ITEM_LIMIT_RECORDINGS
		}

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_recordings), query), cardInfoType = userSettingPreferences[cardInfoType])
	}

	fun loadNextUp(): HomeFragmentRow {
		val query = NextUpQuery().apply {
			userId = userRepository.currentUser.value!!.id.toString()
			imageTypeLimit = 1
			limit = ITEM_LIMIT_NEXT_UP
			fields = arrayOf(
				ItemFields.PrimaryImageAspectRatio,
				ItemFields.Overview,
				ItemFields.ChildCount
			)
		}

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_next_up), query, DEFAULT_TRIGGERS), ThumbListRow::class, userSettingPreferences[cardInfoType], ImageType.THUMB)
	}

	fun loadOnNow(): HomeFragmentRow {
		val query = RecommendedProgramQuery().apply {
			isAiring = true
			fields = arrayOf(
				ItemFields.Overview,
				ItemFields.PrimaryImageAspectRatio,
				ItemFields.ChannelInfo,
				ItemFields.ChildCount
			)
			userId = userRepository.currentUser.value!!.id.toString()
			imageTypeLimit = 1
			enableTotalRecordCount = false
			limit = ITEM_LIMIT_ON_NOW
		}

		return HomeFragmentBrowseRowDefRow(BrowseRowDef(context.getString(R.string.lbl_on_now), query), cardInfoType = userSettingPreferences[cardInfoType])
	}

	companion object {
		// Maximum amount of items loaded for a row
		private const val ITEM_LIMIT_RESUME = 50
		private const val ITEM_LIMIT_RECORDINGS = 40
		private const val ITEM_LIMIT_NEXT_UP = 50
		private const val ITEM_LIMIT_ON_NOW = 20
		internal val DEFAULT_TRIGGERS = arrayOf(ChangeTriggerType.VideoQueueChange, ChangeTriggerType.TvPlayback, ChangeTriggerType.MoviePlayback, ChangeTriggerType.LibraryUpdated)
	}
}

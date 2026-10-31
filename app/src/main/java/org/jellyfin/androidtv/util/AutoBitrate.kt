package org.jellyfin.androidtv.util

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import timber.log.Timber

/**
 * Small utility class to store and automatically detect a preferred bitrate.
 */
class AutoBitrate(
	private val api: ApiClient
) {
	var bitrate: Long? = null
		private set

	@Suppress("MagicNumber")
	suspend fun detect() {
		try {
			// SDK 1.5 removed its detector; retain the original 4/6/8/10 MB probe sequence.
			var testBytes = 4_000_000
			var receivedBytes: Long
			var durationNanos: Long
			do {
				val started = System.nanoTime()
				val response = api.mediaInfoApi.getBitrateTestBytes(size = testBytes)
				// Consume the entire download, not just the first available chunk.
				receivedBytes = response.content.discard(Long.MAX_VALUE)
				durationNanos = System.nanoTime() - started
				testBytes += 2_000_000
			} while (durationNanos <= 1_000_000_000L && testBytes <= 10_000_000)

			bitrate = receivedBytes * 8_000_000_000L / durationNanos
			Timber.i("Auto bitrate set to: %d", bitrate)
		} catch (err: ApiClientException) {
			Timber.e(err, "Failed to detect bitrate")
		} catch (err: IllegalArgumentException) {
			// Session probably ended before the detection completed
			Timber.e(err, "Failed to detect bitrate")
		}
	}
}

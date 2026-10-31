package org.jellyfin.androidtv.util

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.request.target.Target.SIZE_ORIGINAL
import me.carleslc.kotlinextensions.standard.letIfTrue
import org.jellyfin.androidtv.JellyfinApplication
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.*
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.*

class ImageHelper(private val api: ApiClient)
{
	companion object {
		private val imageCache: MutableSet<Int> = HashSet(200)

		val MAX_IMAGE_HEIGHT get() = JellyfinApplication.appContext.resources.displayMetrics.heightPixels
		val MAX_AUTO_IMAGE_HEIGHT get() = JellyfinApplication.appContext.resources.displayMetrics.heightPixels.div(2)
		val MAX_PRIMARY_IMAGE_HEIGHT get() = JellyfinApplication.appContext.resources.displayMetrics.heightPixels.div(2)

		const val DEFAULT_IMAGE_QUALITY = 90
		const val DEFAULT_BACKDROP_IMAGE_QUALITY = 80 // up-to 30-80% reduced size, server converts org. jpg to webp (NOTE: sometimes jpg is smaller....)

		fun checkImageUrl(url: String?): String? {
			if (url.isNullOrEmpty())
				return null

			var checkedUrl = url
			if (!checkedUrl.contains("Height=", true) && !checkedUrl.contains("Width=", true))
				checkedUrl += ("&maxHeight=$MAX_AUTO_IMAGE_HEIGHT")
			if (!checkedUrl.contains("Quality=", true))
				checkedUrl += ("&Quality=$DEFAULT_IMAGE_QUALITY")

			return checkedUrl
		}
	}

	fun getPrimaryImageUrl(item: BaseItemPerson, maxHeight: Int?): String? {
		if (item.primaryImageTag == null) return null

		return api.imageApi.getItemImageUrl(
			itemId = item.id,
			imageType = ImageType.PRIMARY,
			maxHeight = maxHeight?.coerceAtMost(MAX_IMAGE_HEIGHT),
			tag = item.primaryImageTag,
			quality = DEFAULT_IMAGE_QUALITY
		)
	}

	fun getPrimaryImageUrl(item: UserDto): String? {
		if (item.primaryImageTag == null) return null

		return api.imageApi.getUserImageUrl(
			userId = item.id,
			tag = item.primaryImageTag,
			maxHeight = MAX_PRIMARY_IMAGE_HEIGHT,
			quality = DEFAULT_IMAGE_QUALITY
		)
	}

	fun getPrimaryImageUrl(item: BaseItemDto): String? {
		return getImageUrl(item, ImageType.PRIMARY, true, MAX_PRIMARY_IMAGE_HEIGHT, null, false, false, false)
	}

	fun getPrimaryImageUrl(item: BaseItemDto, maxHeight: Int?): String? {
		return getImageUrl(item, ImageType.PRIMARY, true, maxHeight ?: MAX_PRIMARY_IMAGE_HEIGHT, null, false, false, false)
	}

	fun getPrimaryImageUrl(item: BaseItemDto, maxHeight: Int?, maxWidth: Int?): String? {
		return getImageUrl(item, ImageType.PRIMARY, true, maxHeight ?: MAX_PRIMARY_IMAGE_HEIGHT, maxWidth, false, false, false)
	}

	fun getImageUrl(itemId: String, imageType: ImageType, imageTag: String, maxHeight: Int?): String? = itemId.toUUIDOrNull()?.let { itemUuid ->
		return getImageUrl(itemUuid, imageType, imageTag, maxHeight)
	}

	fun getImageUrl(itemId: UUID, imageType: ImageType, imageTag: String, maxHeight: Int?): String {
		return api.imageApi.getItemImageUrl(
			itemId = itemId,
			imageType = imageType,
			tag = imageTag,
			maxHeight = maxHeight?.coerceAtMost(MAX_IMAGE_HEIGHT),
			quality = DEFAULT_IMAGE_QUALITY
		)
	}

	fun getImageUrl(item: BaseItemDto, imageType: ImageType, requireImageTag: Boolean, maxHeight: Int?, maxWidth: Int?): String? {
		return getImageUrl(item, imageType, requireImageTag, maxHeight, maxWidth, false, false, false)
	}

	fun getImageUrl(item: BaseItemDto, imageType: ImageType, requireImageTag: Boolean, maxHeight: Int?, maxWidth: Int?,
					allowParent: Boolean, preferSeries: Boolean, preferSeason: Boolean): String? {
		var itemId = item.id
		var imageTag = item.imageTags?.get(imageType)
		var tryParent = allowParent

		if (ImageType.BACKDROP == imageType && imageTag.isNullOrEmpty()) {
			imageTag = item.backdropImageTags?.getOrNull(0)
		}
		// handle episodes
		if (preferSeason && item.type == BaseItemKind.EPISODE && item.seasonId != null) {
			imageTag = null
			tryParent = true
		} else if (preferSeries && item.type == BaseItemKind.EPISODE && item.seriesId != null) {
			itemId = item.seriesId!!
			imageTag = when (imageType) {
				ImageType.PRIMARY -> item.seriesPrimaryImageTag
				ImageType.THUMB -> item.seriesThumbImageTag
				else -> null
			}
			tryParent = true
		}

		// special type handling
		if (imageTag.isNullOrEmpty() && ImageType.PRIMARY == imageType) {
			if (item.type == BaseItemKind.AUDIO && item.albumId != null && item.albumPrimaryImageTag != null) {
				itemId = item.albumId!!
				imageTag = item.albumPrimaryImageTag
			} else if (item.type == BaseItemKind.CHANNEL && item.channelId != null && item.channelPrimaryImageTag != null) {
				itemId = item.channelId!!
				imageTag = item.channelPrimaryImageTag
			}
		}
		// parent fallback
		if (tryParent && imageTag.isNullOrEmpty()) {
			when (imageType) {
				ImageType.PRIMARY -> item.parentPrimaryImageItemId?.let {
					itemId = it.toUUID()
					imageTag = item.parentPrimaryImageTag
				}
				ImageType.ART -> item.parentArtItemId?.let {
					itemId = it
					imageTag = item.parentArtImageTag
				}
				ImageType.BACKDROP -> item.parentBackdropItemId?.let {
					itemId = it
					imageTag = item.parentBackdropImageTags?.getOrNull(0)
				}
				ImageType.LOGO -> item.parentLogoItemId?.let {
					itemId = it
					imageTag = item.parentLogoImageTag
				}
				ImageType.THUMB -> item.parentThumbItemId?.let {
					itemId = it
					imageTag = item.parentThumbImageTag
				}
				else -> {}
			}
		}
		if (imageTag.isNullOrEmpty()) {
			// extra episode->series fallback
			if (tryParent && item.type == BaseItemKind.EPISODE && item.seriesId != null) {
				itemId = item.seriesId!!
				imageTag = when (imageType) {
					ImageType.PRIMARY -> item.seriesPrimaryImageTag
					ImageType.THUMB -> item.seriesThumbImageTag
					else -> null
				}
			} else if (item.type == BaseItemKind.AUDIO && !item.albumArtists.isNullOrEmpty()) {
				// extra audio->albumArtists fallback
				itemId = item.albumArtists!!.first().id
			}
		}

		if (requireImageTag && imageTag.isNullOrEmpty()) {
			return null
		}

		return api.imageApi.getItemImageUrl(
			itemId = itemId,
			imageType = imageType,
			tag = imageTag,
			maxWidth = maxWidth,
			maxHeight = maxHeight?.coerceAtMost(MAX_IMAGE_HEIGHT),
			quality = DEFAULT_IMAGE_QUALITY
		)
	}

	fun getImageUrl(item: BaseItemDto, imageType: ImageType, imageFormat: ImageFormat? = ImageFormat.WEBP, blur: Int? = 24, maxHeight: Int? = null): String {
		var itemId = item.id
		if (item.type == BaseItemKind.EPISODE || item.type == BaseItemKind.SEASON) {
			itemId = item.seriesId ?: item.id
		}

		return api.imageApi.getItemImageUrl(
			itemId = itemId,
			imageType = imageType,
			maxHeight = maxHeight?.coerceAtMost(MAX_IMAGE_HEIGHT),
			format = imageFormat,
			blur = blur,
			quality = DEFAULT_IMAGE_QUALITY
		)
	}

	// NOTE: we only want to make sure the data are loaded at least into diskcache
	fun preCacheImages(context: Context, urls: Iterable<String>, height: Int = SIZE_ORIGINAL, width: Int = SIZE_ORIGINAL) {
		val loadList = buildList {
			urls.forEach {
				checkImageUrl(it)?.let { checkedUrl ->
					if (!imageCache.contains(checkedUrl.hashCode())) {
						this.add(checkedUrl)
						imageCache.add(checkedUrl.hashCode())
					}
				}
			}
		}

		loadList.isNotEmpty().letIfTrue {
			loadList.forEach {
				Timber.d("Preload url: <%s>", it)
				Glide.with(context)
					.load(it)
					.priority(Priority.LOW)
					.centerCrop()
					.preload(width, height)
			}
		}
	}
}

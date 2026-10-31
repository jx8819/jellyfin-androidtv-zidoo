package org.jellyfin.androidtv.preference

import me.carleslc.kotlinextensions.number.clamp
import org.jellyfin.androidtv.constant.CardInfoType
import org.jellyfin.androidtv.constant.CardSpacing
import org.jellyfin.androidtv.constant.GridDirection
import org.jellyfin.androidtv.constant.ImageType
import org.jellyfin.androidtv.preference.constant.LanguagesAudio
import org.jellyfin.androidtv.preference.constant.RatingType
import org.jellyfin.androidtv.preference.store.DisplayPreferencesStore
import org.jellyfin.preference.booleanPreference
import org.jellyfin.preference.enumPreference
import org.jellyfin.preference.intPreference
import org.jellyfin.preference.stringPreference
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.ItemSortBy

class LibraryPreferences(
	displayPreferencesId: String,
	api: ApiClient,
) : DisplayPreferencesStore(
	displayPreferencesId = displayPreferencesId,
	api = api,
) {
	companion object {
		val gridSize = intPreference("GridSize", 7)
		val imageType = enumPreference("ImageType", ImageType.POSTER)
		val gridDirection = enumPreference("GridDirection", GridDirection.VERTICAL)
		val enableSmartScreen = booleanPreference("SmartScreen", false)
		val ratingType = enumPreference("RatingType", RatingType.RATING_HIDDEN)
		val cardInfoType = enumPreference("CardInfoType", CardInfoType.NO_INFO)
		val cardSpacing = enumPreference("CardSpacing", CardSpacing.NORMAL)

		// Filters
		val filterFavoritesOnly = booleanPreference("FilterFavoritesOnly", false)
		val filterUnwatchedOnly = booleanPreference("FilterUnwatchedOnly", false)

		// Item sorting
		val sortBy = stringPreference("SortBy", ItemSortBy.SORT_NAME.serialName)
		val sortOrder = enumPreference("SortOrder", SortOrder.ASCENDING)

		// Audio settings
		val enableAudioSettings = booleanPreference("EnableAudioSettings", false)
		val audioLanguage = enumPreference("AudioLanguage", LanguagesAudio.AUTO)

		fun getGridSizeChecked(size: Int, direction: GridDirection, imageType: ImageType): Int = when (direction) {
			GridDirection.HORIZONTAL -> when (imageType) {
				ImageType.POSTER -> size.clamp(2, 11)
				ImageType.THUMB -> size.clamp(3, 15)
				ImageType.BANNER -> size.clamp(4, 15)
			}
			GridDirection.VERTICAL -> {
				when (imageType) {
					ImageType.POSTER -> size.clamp(4, 21)
					ImageType.THUMB -> size.clamp(3, 17)
					ImageType.BANNER -> size.clamp(2, 9)
				}
			}
		}
	}

	fun getUiSettingsHash(): Int {
		return this[imageType].hashCode() +
				this[gridDirection].hashCode() +
				this[ratingType].hashCode() +
				this[cardInfoType].hashCode() +
				this[gridSize].hashCode() +
				this[cardSpacing].hashCode()
	}
}

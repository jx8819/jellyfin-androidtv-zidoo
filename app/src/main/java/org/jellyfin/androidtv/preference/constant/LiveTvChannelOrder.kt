package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.preference.dsl.EnumDisplayOptions
import org.jellyfin.sdk.model.api.ItemSortBy

enum class LiveTvChannelOrder(
	val stringValue: String
) {
	@EnumDisplayOptions(R.string.lbl_guide_option_played)
	LAST_PLAYED(ItemSortBy.DATE_PLAYED.serialName),

	@EnumDisplayOptions(R.string.lbl_guide_option_number)
	CHANNEL_NUMBER(ItemSortBy.SORT_NAME.serialName);

	companion object {
		fun fromString(value: String) = values()
			.firstOrNull { it.stringValue.equals(value, ignoreCase = true) }
			?: LAST_PLAYED
	}
}

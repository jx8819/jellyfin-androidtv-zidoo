package org.jellyfin.androidtv.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.leanback.widget.*
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.constant.Extras
import org.jellyfin.androidtv.constant.LiveTvOption
import org.jellyfin.androidtv.ui.GridButton
import org.jellyfin.androidtv.ui.browsing.BrowseRecordingsActivity
import org.jellyfin.androidtv.ui.browsing.BrowseScheduleActivity
import org.jellyfin.androidtv.ui.browsing.UserViewActivity
import org.jellyfin.androidtv.ui.livetv.LiveTvGuideActivity
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.GridButtonPresenter
import org.jellyfin.androidtv.util.Utils
import org.jellyfin.apiclient.model.dto.BaseItemDto
import org.jellyfin.apiclient.model.dto.BaseItemType
import org.jellyfin.apiclient.serialization.GsonJsonSerializer
import org.koin.java.KoinJavaComponent.inject
import java.util.*

class HomeFragmentLiveTVRow(
	private val activity: Activity,
	private val userRepository: UserRepository,
) : HomeFragmentRow, OnItemViewClickedListener {
	// Legacy browse views accept app-only collection types such as SeriesTimers.
	private val serializer by inject<GsonJsonSerializer>(GsonJsonSerializer::class.java)

	override fun addToRowsAdapter(context: Context, presenter: CardPresenter, rowsAdapter: ArrayObjectAdapter) {
		val header = HeaderItem(rowsAdapter.size().toLong(), activity.getString(R.string.pref_live_tv_cat))
		val adapter = ArrayObjectAdapter(GridButtonPresenter())

		// Live TV Guide button
		adapter.add(GridButton(LiveTvOption.LIVE_TV_GUIDE_OPTION_ID, activity.getString(R.string.lbl_live_tv_guide), R.drawable.tile_port_guide))
		// Live TV Recordings button
		adapter.add(GridButton(LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID, activity.getString(R.string.lbl_recorded_tv), R.drawable.tile_port_record))
		if (Utils.canManageRecordings(userRepository.currentUser.value)) {
			// Recording Schedule button
			adapter.add(GridButton(LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID, activity.getString(R.string.lbl_schedule), R.drawable.tile_port_time))
			// Recording Series button
			adapter.add(GridButton(LiveTvOption.LIVE_TV_SERIES_OPTION_ID, activity.getString(R.string.lbl_series), R.drawable.tile_port_series_timer))
		}

		rowsAdapter.add(ListRow(header, adapter))
	}

	override fun onItemClicked(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
		if (item !is GridButton) return

		when (item.id) {
			LiveTvOption.LIVE_TV_GUIDE_OPTION_ID -> {
				activity.startActivity(
					Intent(activity, LiveTvGuideActivity::class.java)
				)
			}
			LiveTvOption.LIVE_TV_RECORDINGS_OPTION_ID -> {
				activity.startActivity(
					Intent(activity, BrowseRecordingsActivity::class.java).apply {
						putExtra(
							Extras.Folder,
							serializer.SerializeToString(
								BaseItemDto().apply {
									id = UUID(0L, 0L).toString()
									name = activity.getString(R.string.lbl_recorded_tv)
									baseItemType = BaseItemType.Folder
								}
							))
					}
				)
			}
			LiveTvOption.LIVE_TV_SCHEDULE_OPTION_ID -> {
				activity.startActivity(
					Intent(activity, BrowseScheduleActivity::class.java)
				)
			}
			LiveTvOption.LIVE_TV_SERIES_OPTION_ID -> {
				activity.startActivity(
					Intent(activity, UserViewActivity::class.java).apply {
						putExtra(
							Extras.Folder,
							serializer.SerializeToString(
								BaseItemDto().apply {
									id = UUID(0L, 0L).toString()
									name = activity.getString(R.string.lbl_series_recordings)
									collectionType = "SeriesTimers"
									baseItemType = BaseItemType.Folder
								}
							)
						)
					}
				)
			}
		}
	}
}

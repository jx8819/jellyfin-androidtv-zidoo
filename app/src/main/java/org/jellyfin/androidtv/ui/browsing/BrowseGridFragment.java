package org.jellyfin.androidtv.ui.browsing;

import static org.koin.java.KoinJavaComponent.inject;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.leanback.widget.VerticalGridView;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.auth.repository.UserRepository;
import org.jellyfin.androidtv.constant.CardInfoType;
import org.jellyfin.androidtv.constant.ChangeTriggerType;
import org.jellyfin.androidtv.constant.CustomMessage;
import org.jellyfin.androidtv.constant.Extras;
import org.jellyfin.androidtv.constant.FocusZoomSize;
import org.jellyfin.androidtv.constant.GridDirection;
import org.jellyfin.androidtv.constant.ImageType;
import org.jellyfin.androidtv.constant.QueryType;
import org.jellyfin.androidtv.data.model.FilterOptions;
import org.jellyfin.androidtv.data.querying.StdItemQuery;
import org.jellyfin.androidtv.data.repository.UserViewsRepository;
import org.jellyfin.androidtv.data.service.BackgroundService;
import org.jellyfin.androidtv.databinding.HorizontalGridBrowseBinding;
import org.jellyfin.androidtv.databinding.PopupEmptyBinding;
import org.jellyfin.androidtv.preference.LibraryPreferences;
import org.jellyfin.androidtv.preference.PreferencesRepository;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.ui.AlphaPickerView;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter;
import org.jellyfin.androidtv.ui.playback.MediaManager;
import org.jellyfin.androidtv.ui.preference.PreferencesActivity;
import org.jellyfin.androidtv.ui.presentation.CardPresenter;
import org.jellyfin.androidtv.ui.presentation.HorizontalGridPresenter;
import org.jellyfin.androidtv.ui.shared.BaseActivity;
import org.jellyfin.androidtv.ui.shared.KeyListener;
import org.jellyfin.androidtv.ui.shared.MessageListener;
import org.jellyfin.androidtv.util.CoroutineUtils;
import org.jellyfin.androidtv.util.KeyProcessor;
import org.jellyfin.androidtv.util.LayoutHelper;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.apiclient.interaction.EmptyResponse;
import org.jellyfin.apiclient.model.dto.BaseItemType;
import org.jellyfin.apiclient.model.querying.ArtistsQuery;
import org.jellyfin.apiclient.model.querying.ItemFields;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.jellyfin.sdk.model.api.SortOrder;
import org.jellyfin.sdk.model.api.CollectionType;
import org.jellyfin.sdk.model.api.ItemSortBy;
import org.koin.java.KoinJavaComponent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import kotlin.Lazy;
import kotlinx.serialization.json.Json;
import timber.log.Timber;

public class BrowseGridFragment extends Fragment {
    private final static int CHUNK_SIZE_MINIMUM = 50;
    private final static int VIEW_SELECT_UPDATE_DELAY = 100; // delay in ms until we update the top-row info for a selected item

    private String mainTitle;
    private BaseActivity mActivity;
    private BaseRowItem mCurrentItem;
    private CompositeClickedListener mClickedListener = new CompositeClickedListener();
    private CompositeSelectedListener mSelectedListener = new CompositeSelectedListener();
    private final Handler mHandler = new Handler();
    private BrowseRowDef mRowDef;
    private CardPresenter mCardPresenter;

    private boolean justLoaded = true;
    private int mGridSize = 7;
    private ImageType mImageType = ImageType.POSTER;
    private GridDirection mGridDirection = GridDirection.VERTICAL;
    private CardInfoType mCardInfoType = CardInfoType.NO_INFO;
    private boolean determiningPosterSize = false;

    private UUID mParentId;
    private BaseItemDto mFolder;
    private LibraryPreferences libraryPreferences;

    private HorizontalGridBrowseBinding binding;
    private ItemRowAdapter mAdapter;
    private Presenter mGridPresenter;
    private Presenter.ViewHolder mGridViewHolder;
    private BaseGridView mGridView;
    private int mSelectedPosition = -1;

    private final Lazy<BackgroundService> backgroundService = inject(BackgroundService.class);
    private final Lazy<MediaManager> mediaManager = inject(MediaManager.class);
    private final Lazy<PreferencesRepository> preferencesRepository = inject(PreferencesRepository.class);
    private final Lazy<UserViewsRepository> userViewsRepository = inject(UserViewsRepository.class);
    private final Lazy<UserRepository> userRepository = inject(UserRepository.class);

    private boolean mDirty = true; // RowDef or GridSize changed
    private int mLibrarySettingsUiHash = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFolder = Json.Default.decodeFromString(BaseItemDto.Companion.serializer(), requireActivity().getIntent().getStringExtra(Extras.Folder));
        mParentId = mFolder.getId();
        mainTitle = mFolder.getName();

        sortOptions = new HashMap<>();
        {
            // don't use Descending Name fallbacks for dates/ratings!
            sortOptions.put(0, new SortOption(getString(R.string.lbl_name), ItemSortBy.SORT_NAME.getSerialName(), SortOrder.ASCENDING));
            if (mFolder != null && CollectionType.TVSHOWS.getSerialName().equals(mFolder.getCollectionType())) {
                sortOptions.put(1, new SortOption(getString(R.string.lbl_date_latest), ItemSortBy.DATE_LAST_CONTENT_ADDED.getSerialName(), SortOrder.DESCENDING));
                sortOptions.put(2, new SortOption(getString(R.string.lbl_date_played), ItemSortBy.SERIES_DATE_PLAYED.getSerialName() + "," + ItemSortBy.PREMIERE_DATE.getSerialName(), SortOrder.DESCENDING));
                sortOptions.put(3, new SortOption(getString(R.string.lbl_premier_date), ItemSortBy.PREMIERE_DATE.getSerialName(), SortOrder.DESCENDING));
                sortOptions.put(4, new SortOption(getString(R.string.lbl_date_added), ItemSortBy.DATE_CREATED.getSerialName(), SortOrder.DESCENDING));
                sortOptions.put(5, new SortOption(getString(R.string.lbl_community_rating), ItemSortBy.COMMUNITY_RATING.getSerialName() + "," + ItemSortBy.PREMIERE_DATE.getSerialName(), SortOrder.DESCENDING));
                sortOptions.put(6, new SortOption(getString(R.string.lbl_critic_rating), ItemSortBy.CRITIC_RATING.getSerialName() + "," + ItemSortBy.PREMIERE_DATE.getSerialName(), SortOrder.DESCENDING));
            } else {
                sortOptions.put(1, new SortOption(getString(R.string.lbl_date_played), ItemSortBy.DATE_PLAYED.getSerialName() + "," + ItemSortBy.PREMIERE_DATE.getSerialName(), SortOrder.DESCENDING));
                sortOptions.put(2, new SortOption(getString(R.string.lbl_premier_date), ItemSortBy.PREMIERE_DATE.getSerialName(), SortOrder.DESCENDING));
                sortOptions.put(3, new SortOption(getString(R.string.lbl_date_added), ItemSortBy.DATE_CREATED.getSerialName(), SortOrder.DESCENDING));
                sortOptions.put(4, new SortOption(getString(R.string.lbl_community_rating), ItemSortBy.COMMUNITY_RATING.getSerialName() + "," + ItemSortBy.PREMIERE_DATE.getSerialName(), SortOrder.DESCENDING));
                sortOptions.put(5, new SortOption(getString(R.string.lbl_critic_rating), ItemSortBy.CRITIC_RATING.getSerialName() + "," + ItemSortBy.PREMIERE_DATE.getSerialName(), SortOrder.DESCENDING));
            }
        }

        if (getActivity() instanceof BaseActivity)
            mActivity = (BaseActivity) getActivity();

        backgroundService.getValue().attach(requireActivity());
        mediaManager.getValue().setFolderViewDisplayPreferencesId(mFolder);
        libraryPreferences = preferencesRepository.getValue().getLibraryPreferences(Objects.requireNonNull(mFolder.getDisplayPreferencesId()));
        updateUiSettings();
        setupQueries();
        setupEventListeners();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaManager.getValue().setFolderViewDisplayPreferencesId(null);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = HorizontalGridBrowseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    private void buildGrid() {
        determiningPosterSize = true;
        createGrid();
        buildAdapter();
        loadGrid();
        determiningPosterSize = false;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        buildGrid();
        addTools();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mGridView = null;
    }

    private void createGrid() {
        if (mDirty)
            createGridPresenter();

        int spacing = Utils.convertDpToPixel(requireContext(), libraryPreferences.get(LibraryPreferences.Companion.getCardSpacing()).getSizeDP());

        int paddingTop = 0;
        int paddingBottom = requireContext().getResources().getDimensionPixelSize(R.dimen.safe_area_vertical);
        int paddingH = requireContext().getResources().getDimensionPixelSize(R.dimen.safe_area_horizontal_small);

        FocusZoomSize zoom = KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getFocusZoomSize());
        if (zoom != FocusZoomSize.NONE) {
            var paddingZoom = LayoutHelper.INSTANCE.calcGridPadding(
                    zoom,
                    mCardInfoType,
                    mGridSize,
                    mGridDirection,
                    isSquareCard() ? 1.0 : mImageType.getAspect(),
                    spacing,
                    LayoutHelper.INSTANCE.getStatusBarLayoutHeight()
            );
            paddingTop = Math.max(paddingZoom.getFirst(), requireContext().getResources().getDimensionPixelSize(R.dimen.safe_area_vertical_small));
            paddingBottom = Math.max(paddingZoom.getFirst(), requireContext().getResources().getDimensionPixelSize(R.dimen.safe_area_vertical));
            paddingH = Math.max(paddingZoom.getSecond(), requireContext().getResources().getDimensionPixelSize(R.dimen.safe_area_horizontal_small));
        }

        mGridViewHolder = mGridPresenter.onCreateViewHolder(binding.rowsFragment);
        if (mGridViewHolder instanceof HorizontalGridPresenter.ViewHolder) {
            mGridView = ((HorizontalGridPresenter.ViewHolder) mGridViewHolder).getGridView();
            mGridView.setGravity(Gravity.CENTER_VERTICAL);
            HorizontalGridView gridView = ((HorizontalGridPresenter.ViewHolder) mGridViewHolder).getGridView();
            gridView.setClipToPadding(false);
            gridView.setItemSpacing(spacing);
            gridView.setPadding(paddingH, paddingTop, paddingH, paddingBottom);
            gridView.setRowHeight(0);
        } else if (mGridViewHolder instanceof VerticalGridPresenter.ViewHolder) {
            mGridView = ((VerticalGridPresenter.ViewHolder) mGridViewHolder).getGridView();
            mGridView.setGravity(Gravity.CENTER_HORIZONTAL);
            VerticalGridView gridView = ((VerticalGridPresenter.ViewHolder) mGridViewHolder).getGridView();
            gridView.setClipToPadding(false);
            gridView.setItemSpacing(spacing);
            gridView.setPadding(paddingH, paddingTop, paddingH, paddingBottom);
            gridView.setColumnWidth(0);
        }

        Timber.d("GridSpacing: H<%s> V<%s>", mGridView.getHorizontalSpacing(), mGridView.getVerticalSpacing());
        Timber.d("GridPadding: Top<%s> Bottom<%s> Left<%s>", mGridView.getPaddingTop(), mGridView.getPaddingBottom(), mGridView.getPaddingStart());

        mGridView.setFocusable(true);
        mGridView.setFocusableInTouchMode(true);
        mGridView.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        binding.rowsFragment.removeAllViews();
        binding.rowsFragment.addView(mGridViewHolder.view);
    }

    private void createGridPresenter() {
        FocusZoomSize zoomFactor = KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getFocusZoomSize());
        boolean dimmingEnabled = KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getEnableFocusDimming());
        if (mGridDirection.equals(GridDirection.VERTICAL)) {
            VerticalGridPresenter presenter = new VerticalGridPresenter(zoomFactor.getValue(), dimmingEnabled);
            presenter.setOnItemViewSelectedListener(mRowSelectedListener);
            presenter.setOnItemViewClickedListener(mClickedListener);
            presenter.setShadowEnabled(false);
            presenter.enableChildRoundedCorners(false);
            presenter.setKeepChildForeground(false);
            presenter.setNumberOfColumns(mGridSize);
            mGridPresenter = presenter;
        } else if (mGridDirection.equals(GridDirection.HORIZONTAL)) {
            HorizontalGridPresenter presenter = new HorizontalGridPresenter(zoomFactor.getValue(), dimmingEnabled);
            presenter.setOnItemViewSelectedListener(mRowSelectedListener);
            presenter.setOnItemViewClickedListener(mClickedListener);
            presenter.setShadowEnabled(false);
            presenter.enableChildRoundedCorners(false);
            presenter.setKeepChildForeground(false);
            presenter.setNumberOfRows(mGridSize);
            mGridPresenter = presenter;
        }
    }

    public void setItem(BaseRowItem item) {
        if (item != null) {
            binding.title.setText(item.getFullName(requireContext()));
            binding.infoRow.setRowItem(item);
            binding.infoRow.setIncludeRuntime(true);
            binding.infoRow.setIncludeEndtime(true);
        } else {
            binding.title.setText("");
            binding.infoRow.setRowItem(null);
        }
    }

    public class SortOption {
        public String name;
        public String value;
        public SortOrder order;

        public SortOption(String name, String value, SortOrder order) {
            this.name = name;
            this.value = value;
            this.order = order;
        }
    }

    private Map<Integer, SortOption> sortOptions;

    private SortOption getSortOption(String value) {
        for (Integer key : sortOptions.keySet()) {
            SortOption option = sortOptions.get(key);
            if (Objects.requireNonNull(option).value.equals(value)) return option;
        }

        return new SortOption("Unknown", "", SortOrder.ASCENDING);
    }

    public void setStatusText(String folderName) {
        if (this.getContext() == null)
            return;

        String text = getString(R.string.lbl_showing) + " ";
        FilterOptions filters = mAdapter.getFilters();
        if (filters == null || (!filters.isFavoriteOnly() && !filters.isUnwatchedOnly())) {
            text += getString(R.string.lbl_all_items);
        } else {
            text += (filters.isUnwatchedOnly() ? getString(R.string.lbl_unwatched) : "") + " " +
                    (filters.isFavoriteOnly() ? getString(R.string.lbl_favorites) : "");
        }

        if (mAdapter.getStartLetter() != null) {
            text += " " + getString(R.string.lbl_starting_with) + " " + mAdapter.getStartLetter();
        }

        text += " " + getString(R.string.lbl_from) + " '" + folderName + "' " + getString(R.string.lbl_sorted_by) + " " + getSortOption(mAdapter.getSortBy()).name;

        binding.statusText.setText(text);
    }

    final private OnItemViewSelectedListener mRowSelectedListener =
            new OnItemViewSelectedListener() {
                @Override
                public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                    if (mGridView != null) {
                        int position = mGridView.getSelectedPosition();
                        if (position != mSelectedPosition) {
                            mSelectedPosition = position;
                        }
                        if (position >= 0) {
                            mSelectedListener.onItemSelected(itemViewHolder, item, rowViewHolder, row);
                        }
                    }
                }
            };

    protected void updateCounter() {
        if (mAdapter != null) {
            binding.counter.setText(String.format(Locale.US, "%d", mAdapter.getTotalItems()));
        }
    }

    private void setRowDef(final BrowseRowDef rowDef) {
        if (mRowDef == null || mRowDef.hashCode() != rowDef.hashCode()) {
            mDirty = true;
        }
        mRowDef = rowDef;
    }

    private void setupQueries() {
        StdItemQuery query = new StdItemQuery(new ItemFields[]{
                ItemFields.PrimaryImageAspectRatio,
                ItemFields.ChildCount,
                ItemFields.MediaSources,
                ItemFields.MediaStreams,
                ItemFields.DisplayPreferencesId,
                ItemFields.DateCreated,
                ItemFields.DateLastMediaAdded,
                ItemFields.SeriesPrimaryImage,
        });
        query.setParentId(mParentId.toString());
        if (mFolder.getType() == BaseItemKind.USER_VIEW || mFolder.getType() == BaseItemKind.COLLECTION_FOLDER) {
            String type = mFolder.getCollectionType() != null ? mFolder.getCollectionType().getSerialName() : "";
            switch (type) {
                case "movies" -> {
                    query.setIncludeItemTypes(new String[]{"Movie"});
                    query.setRecursive(true);
                }
                case "tvshows" -> {
                    query.setIncludeItemTypes(new String[]{"Series"});
                    query.setRecursive(true);
                }
                case "boxsets" -> {
                    query.setIncludeItemTypes(new String[]{"BoxSet"});
                    query.setParentId(null);
                    query.setRecursive(true);
                }
                case "music" -> {
                    //Special queries needed for album artists
                    String includeType = requireActivity().getIntent().getStringExtra(Extras.IncludeType);
                    if ("AlbumArtist".equals(includeType)) {
                        ArtistsQuery albumArtists = new ArtistsQuery();
                        albumArtists.setUserId(userRepository.getValue().getCurrentUser().getValue().getId().toString());
                        albumArtists.setFields(new ItemFields[]{
                                ItemFields.PrimaryImageAspectRatio,
                                ItemFields.ItemCounts,
                                ItemFields.ChildCount,
                                ItemFields.DateCreated,
                                ItemFields.DateLastMediaAdded,
                        });
                        albumArtists.setParentId(mParentId.toString());
                        setRowDef(new BrowseRowDef("", albumArtists, new ChangeTriggerType[]{}).setChunkSize(CHUNK_SIZE_MINIMUM));
                        return;
                    }
                    query.setIncludeItemTypes(new String[]{includeType != null ? includeType : "MusicAlbum"});
                    query.setRecursive(true);
                }
            }
        }

        setRowDef(new BrowseRowDef("", query).setChunkSize(CHUNK_SIZE_MINIMUM));
    }

    protected boolean isSquareCard() {
        BaseItemKind fType = mFolder.getType();
        if (fType == BaseItemKind.AUDIO || fType == BaseItemKind.GENRE || fType == BaseItemKind.MUSIC_ALBUM || fType == BaseItemKind.MUSIC_ARTIST || fType == BaseItemKind.MUSIC_GENRE) {
            return true;
        } else if (CollectionType.MUSIC.getSerialName().equals(mFolder.getCollectionType())) {
            return true;
        } else {
            return false;
        }
    }

    protected void updateUiSettings() {
        mImageType = libraryPreferences.get(LibraryPreferences.Companion.getImageType());
        mCardInfoType = libraryPreferences.get(LibraryPreferences.Companion.getCardInfoType());
        mGridDirection = libraryPreferences.get(LibraryPreferences.Companion.getGridDirection());
        mGridSize = LibraryPreferences.Companion.getGridSizeChecked(libraryPreferences.get(LibraryPreferences.Companion.getGridSize()), mGridDirection, mImageType);

        int newHash = libraryPreferences.getUiSettingsHash();
        if (mLibrarySettingsUiHash != newHash) {
            mLibrarySettingsUiHash = newHash;
            mDirty = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        updateUiSettings();
        if (mDirty) {
            buildGrid();
        }

        if (!justLoaded) {
            //Re-retrieve anything that needs it but delay slightly so we don't take away gui landing
            if (mAdapter != null) {
                mHandler.postDelayed(() -> {
                    if (mActivity != null && mActivity.isFinishing()) return;
                    if (getActivity() != null && getActivity().isFinishing()) return;
                    if (mAdapter != null && mAdapter.size() > 0) {
                        if (!mAdapter.ReRetrieveIfNeeded()) {
                            refreshCurrentItem();
                        }
                    }
                }, 500);
            }
        } else {
            justLoaded = false;
        }
    }

    private void createCardPresenter() {
        mCardPresenter = new CardPresenter(mCardInfoType, null);
        mCardPresenter.setImageType(mImageType);
        mCardPresenter.setRatingDisplay(libraryPreferences.get(LibraryPreferences.Companion.getRatingType()));
        mCardPresenter.setAllowBackdropFallback(true);
        mCardPresenter.setSquareAspect(isSquareCard());
    }

    private void buildAdapter() {
        if (mDirty)
            createCardPresenter();

        mRowDef.chunkSize = Math.max(
                LayoutHelper.INSTANCE.estimateCardsOnScreen(
                        mGridSize,
                        mGridDirection,
                        isSquareCard() ? 1.0 : mImageType.getAspect(),
                        Math.max(mGridView.getVerticalSpacing(), mGridView.getHorizontalSpacing()),
                        LayoutHelper.INSTANCE.getStatusBarLayoutHeight()
        ), CHUNK_SIZE_MINIMUM);
        Timber.d("Auto-Adapting chunkSize: <%s>", mRowDef.chunkSize);
        mAdapter = ItemRowAdapter.buildItemRowAdapter(requireContext(), mRowDef, mCardPresenter, null);
        mDirty = false;

        FilterOptions filters = new FilterOptions();
        filters.setFavoriteOnly(libraryPreferences.get(LibraryPreferences.Companion.getFilterFavoritesOnly()));
        filters.setUnwatchedOnly(libraryPreferences.get(LibraryPreferences.Companion.getFilterUnwatchedOnly()));
        mAdapter.setFilters(filters);

        mAdapter.setRetrieveFinishedListener(new EmptyResponse() {
            @Override
            public void onResponse() {
                setStatusText(mFolder.getName());
                setItem(mCurrentItem);
                updateCounter();
                mLetterButton.setVisibility(ItemSortBy.SORT_NAME.getSerialName().equals(mAdapter.getSortBy()) ? View.VISIBLE : View.GONE);
                if (mAdapter.getItemsLoaded() == 0) {
                    binding.toolbarLayout.requestFocus();
                    mHandler.postDelayed(() -> {
                        if (mFolder != null) binding.title.setText(mFolder.getName());
                    }, 500);
                } else {
                    mHandler.postDelayed(() -> {
                        if (mGridView != null && mAdapter.getItemsLoaded() > 0)
                            mGridView.requestFocus();
                        else
                            binding.toolbarLayout.requestFocus();
                    }, 500);
                }
            }
        });

        if (mGridPresenter != null && mGridViewHolder != null) {
            mGridPresenter.onBindViewHolder(mGridViewHolder, mAdapter);
            if (mGridView != null && mSelectedPosition != -1) {
                mGridView.setSelectedPosition(mSelectedPosition);
            }
        }
    }

    public void loadGrid() {
        if (mAdapter != null) {
            mAdapter.setSortBy(getSortOption(libraryPreferences.get(LibraryPreferences.Companion.getSortBy())));
            mAdapter.Retrieve();
        }
    }

    private ImageButton mSortButton;
    private ImageButton mSettingsButton;
    private ImageButton mUnwatchedButton;
    private ImageButton mFavoriteButton;
    private ImageButton mLetterButton;

    private void updateDisplayPrefs() {
        libraryPreferences.set(LibraryPreferences.Companion.getFilterFavoritesOnly(), mAdapter.getFilters().isFavoriteOnly());
        libraryPreferences.set(LibraryPreferences.Companion.getFilterUnwatchedOnly(), mAdapter.getFilters().isUnwatchedOnly());
        libraryPreferences.set(LibraryPreferences.Companion.getSortBy(), mAdapter.getSortBy());
        libraryPreferences.set(LibraryPreferences.Companion.getSortOrder(), getSortOption(mAdapter.getSortBy()).order);
        CoroutineUtils.runBlocking((coroutineScope, continuation) -> libraryPreferences.commit(continuation));
    }

    private void addTools() {
        //Add tools
        mSortButton = binding.toolbarLayout.addButton(R.drawable.ic_sort, R.string.lbl_sort_by);
        mSortButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Create sort menu
                PopupMenu sortMenu = new PopupMenu(getActivity(), binding.toolbarLayout, Gravity.END);
                for (Integer key : sortOptions.keySet()) {
                    SortOption option = sortOptions.get(key);
                    if (option == null) option = sortOptions.get(0);
                    MenuItem item = sortMenu.getMenu().add(0, key, key, Objects.requireNonNull(option).name);
                    if (option.value.equals(libraryPreferences.get(LibraryPreferences.Companion.getSortBy())))
                        item.setChecked(true);
                }
                sortMenu.getMenu().setGroupCheckable(0, true, true);
                sortMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        mAdapter.setSortBy(Objects.requireNonNull(sortOptions.get(item.getItemId())));
                        mAdapter.Retrieve();
                        item.setChecked(true);
                        updateDisplayPrefs();
                        return true;
                    }
                });
                sortMenu.show();
            }
        });

        if (mRowDef.getQueryType() == QueryType.Items) {
            mUnwatchedButton = binding.toolbarLayout.addButton(R.drawable.ic_unwatch, R.string.lbl_unwatched);
            mUnwatchedButton.setActivated(mAdapter.getFilters().isUnwatchedOnly());
            mUnwatchedButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FilterOptions filters = mAdapter.getFilters();
                    if (filters == null) filters = new FilterOptions();

                    filters.setUnwatchedOnly(!filters.isUnwatchedOnly());
                    mUnwatchedButton.setActivated(filters.isUnwatchedOnly());
                    mAdapter.setFilters(filters);
                    mAdapter.Retrieve();
                    updateDisplayPrefs();
                }
            });
        }

        mFavoriteButton = binding.toolbarLayout.addButton(R.drawable.ic_heart, R.string.lbl_favorite);
        mFavoriteButton.setActivated(mAdapter.getFilters().isFavoriteOnly());
        mFavoriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FilterOptions filters = mAdapter.getFilters();
                if (filters == null) filters = new FilterOptions();

                filters.setFavoriteOnly(!filters.isFavoriteOnly());
                mFavoriteButton.setActivated(filters.isFavoriteOnly());
                mAdapter.setFilters(filters);
                mAdapter.Retrieve();
                updateDisplayPrefs();
            }
        });

        JumplistPopup jumplistPopup = new JumplistPopup();
        mLetterButton = binding.toolbarLayout.addButton(R.drawable.ic_jump_letter, R.string.lbl_by_letter);
        mLetterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Open letter jump popup
                jumplistPopup.show();
            }
        });

        mSettingsButton = binding.toolbarLayout.addButton(R.drawable.ic_settings, R.string.lbl_settings);
        mSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsIntent = new Intent(getActivity(), PreferencesActivity.class);
                settingsIntent.putExtra(PreferencesActivity.EXTRA_SCREEN, DisplayPreferencesScreen.class.getCanonicalName());
                Bundle screenArgs = new Bundle();
                screenArgs.putString(DisplayPreferencesScreen.ARG_PREFERENCES_ID, mFolder.getDisplayPreferencesId());
                screenArgs.putBoolean(DisplayPreferencesScreen.ARG_ALLOW_VIEW_SELECTION, userViewsRepository.getValue().allowViewSelection(mFolder.getCollectionType() != null ? mFolder.getCollectionType().getSerialName() : null));
                settingsIntent.putExtra(PreferencesActivity.EXTRA_SCREEN_ARGS, screenArgs);
                requireActivity().startActivity(settingsIntent);
            }
        });
    }

    class JumplistPopup {
        private final int WIDTH = Utils.convertDpToPixel(requireContext(), 900);
        private final int HEIGHT = Utils.convertDpToPixel(requireContext(), 55);

        private final PopupWindow popupWindow;
        private final AlphaPickerView alphaPicker;

        JumplistPopup() {
            PopupEmptyBinding layout = PopupEmptyBinding.inflate(getLayoutInflater(), binding.rowsFragment, false);
            popupWindow = new PopupWindow(layout.emptyPopup, WIDTH, HEIGHT, true);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setAnimationStyle(R.style.WindowAnimation_SlideTop);

            alphaPicker = new AlphaPickerView(requireContext(), null);
            alphaPicker.setOnAlphaSelected(letter -> {
                mAdapter.setStartLetter(letter.toString());
                loadGrid();
                dismiss();
                return null;
            });

            layout.emptyPopup.addView(alphaPicker);
        }

        public void show() {
            popupWindow.showAtLocation(binding.rowsFragment, Gravity.TOP, binding.rowsFragment.getLeft(), binding.rowsFragment.getTop());
            if (mAdapter.getStartLetter() != null && !mAdapter.getStartLetter().isEmpty()) {
                alphaPicker.focus(mAdapter.getStartLetter().charAt(0));
            }
        }

        public void dismiss() {
            if (popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }
        }
    }

    private void setupEventListeners() {
        mClickedListener.registerListener(new ItemViewClickedListener());
        mSelectedListener.registerListener(new ItemViewSelectedListener());

        if (mActivity != null) {
            mActivity.registerKeyListener(new KeyListener() {
                @Override
                public boolean onKeyUp(int key, KeyEvent event) {
                    if (!binding.rowsFragment.hasFocus()) {
                        return false;
                    }
                    if (key == KeyEvent.KEYCODE_MEDIA_PLAY || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                        mediaManager.getValue().setCurrentMediaAdapter(mAdapter);
                        mediaManager.getValue().setCurrentMediaPosition(mCurrentItem.getIndex());
                        mediaManager.getValue().setCurrentMediaTitle(mFolder.getName());
                    }
                    return KeyProcessor.HandleKey(key, mCurrentItem, mActivity);
                }
            });

            mActivity.registerMessageListener(new MessageListener() {
                @Override
                public void onMessageReceived(CustomMessage message) {
                    if (message == CustomMessage.RefreshCurrentItem) {
                        refreshCurrentItem();
                    }
                }
            });
        }
    }

    private void refreshCurrentItem() {
        if (mediaManager.getValue().getCurrentMediaPosition() >= 0) {
            mCurrentItem = mediaManager.getValue().getCurrentMediaItem();

            if (mGridPresenter instanceof HorizontalGridPresenter)
                ((HorizontalGridPresenter) mGridPresenter).setPosition(mediaManager.getValue().getCurrentMediaPosition());
            // Don't do anything for vertical grids as the presenter does not allow setting the position

            mediaManager.getValue().setCurrentMediaPosition(-1); // re-set so it doesn't mess with parent views
        }
        if (mCurrentItem != null &&
            mCurrentItem.getBaseItemType() != BaseItemType.Photo &&
            mCurrentItem.getBaseItemType() != BaseItemType.PhotoAlbum &&
            mCurrentItem.getBaseItemType() != BaseItemType.MusicArtist &&
            mCurrentItem.getBaseItemType() != BaseItemType.MusicAlbum
        ) {
            Timber.d("Refresh item \"%s\"", mCurrentItem.getFullName(requireContext()));
            mCurrentItem.refresh(new EmptyResponse() {
                @Override
                public void onResponse() {
                    mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(mCurrentItem), 1);
                    //Now - if filtered make sure we still pass
                    if (mAdapter.getFilters() != null) {
                        if ((mAdapter.getFilters().isFavoriteOnly() && !mCurrentItem.isFavorite()) || (mAdapter.getFilters().isUnwatchedOnly() && mCurrentItem.isPlayed())) {
                            //if we are about to remove last item, throw focus to toolbar so framework doesn't crash
                            if (mAdapter.size() == 1) binding.toolbarLayout.requestFocus();
                            mAdapter.remove(mCurrentItem);
                            mAdapter.setTotalItems(mAdapter.getTotalItems() - 1);
                        }
                    }
                }
            });
        }
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(final Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (!(item instanceof BaseRowItem)) return;
            ItemLauncher.launch((BaseRowItem) item, mAdapter, ((BaseRowItem) item).getIndex(), getActivity());
        }
    }

    private final Runnable mDelayedSetItem = new Runnable() {
        @Override
        public void run() {
            backgroundService.getValue().setBackground(mCurrentItem.getBaseItem());
            setItem(mCurrentItem);
        }
    };

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            mHandler.removeCallbacks(mDelayedSetItem);
            if (!(item instanceof BaseRowItem)) {
                mCurrentItem = null;
                binding.title.setText(mainTitle);
                //fill in default background
                backgroundService.getValue().clearBackgrounds();
            } else {
                mCurrentItem = (BaseRowItem) item;
                binding.title.setText(mCurrentItem.getName(requireContext()));
                binding.infoRow.removeAllViews();
                mHandler.postDelayed(mDelayedSetItem, VIEW_SELECT_UPDATE_DELAY);

                if (!determiningPosterSize)
                    mAdapter.loadMoreItemsIfNeeded(mCurrentItem.getIndex());
            }
        }
    }
}

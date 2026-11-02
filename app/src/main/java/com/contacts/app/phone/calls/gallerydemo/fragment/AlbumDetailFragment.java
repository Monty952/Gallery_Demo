package com.contacts.app.phone.calls.gallerydemo.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.adapter.MediaGridAdapter;
import com.contacts.app.phone.calls.gallerydemo.data.FavoritesRepository;
import com.contacts.app.phone.calls.gallerydemo.data.MediaStoreRepository;
import com.contacts.app.phone.calls.gallerydemo.data.SettingsRepository;
import com.contacts.app.phone.calls.gallerydemo.data.VirtualAlbumRepository;
import com.contacts.app.phone.calls.gallerydemo.databinding.FragmentAlbumDetailBinding;
import com.contacts.app.phone.calls.gallerydemo.model.MediaFilter;
import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;
import com.contacts.app.phone.calls.gallerydemo.util.MediaUtils;
import com.contacts.app.phone.calls.gallerydemo.util.ThumbnailLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Shows a list of media. The "kind" decides WHICH media:
//   BUCKET   = one real folder        VIRTUAL = one custom album
//   FEATURED = favourites + newest    FILTER  = a media type (Photos, Videos, ...)
public class AlbumDetailFragment extends Fragment implements ScreenActions {

    private static final String ARG_KIND = "kind";
    private static final String ARG_BUCKET_ID = "bucket_id";
    private static final String ARG_NAME = "name";
    private static final String ARG_FILTER = "filter";

    private static final int MIN_COLUMNS = 3;
    private static final int TARGET_COLUMN_WIDTH_DP = 110;
    private static final int FEATURED_RECENT_PHOTOS = 20;

    private enum Kind { BUCKET, VIRTUAL, FEATURED, FILTER }

    private enum Section { LOADING, MESSAGE, CONTENT }

    @Nullable
    private FragmentAlbumDetailBinding binding;
    @Nullable
    private MediaGridAdapter adapter;

    private MediaStoreRepository repository;
    private ThumbnailLoader thumbnailLoader;
    private FavoritesRepository favoritesRepository;
    private VirtualAlbumRepository virtualAlbumRepository;
    private SettingsRepository settingsRepository;

    private Kind kind = Kind.BUCKET;
    private long bucketId;
    private String albumName = "";
    private MediaFilter filter = MediaFilter.ALL;

    private final List<MediaItem> allItems = new ArrayList<>();
    private String searchQuery = "";

    // NEW (Stage 11): reacts to sort changes and refresh requests
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (prefs, key) -> {
        if (binding == null) {
            return;
        }
        if (SettingsRepository.KEY_SORT_ORDER.equals(key)) {
            applyView();
        } else if (SettingsRepository.KEY_REFRESH_TOKEN.equals(key)) {
            loadAlbumMedia();
        }
    };

    // ===== Four ways to create this screen =====

    @NonNull
    public static AlbumDetailFragment forBucket(long bucketId) {
        Bundle args = new Bundle();
        args.putString(ARG_KIND, Kind.BUCKET.name());
        args.putLong(ARG_BUCKET_ID, bucketId);
        return withArguments(args);
    }

    @NonNull
    public static AlbumDetailFragment forVirtual(@NonNull String albumName) {
        Bundle args = new Bundle();
        args.putString(ARG_KIND, Kind.VIRTUAL.name());
        args.putString(ARG_NAME, albumName);
        return withArguments(args);
    }

    @NonNull
    public static AlbumDetailFragment forFeatured() {
        Bundle args = new Bundle();
        args.putString(ARG_KIND, Kind.FEATURED.name());
        return withArguments(args);
    }

    @NonNull
    public static AlbumDetailFragment forFilter(@NonNull MediaFilter filter) {
        Bundle args = new Bundle();
        args.putString(ARG_KIND, Kind.FILTER.name());
        args.putString(ARG_FILTER, filter.name());
        return withArguments(args);
    }

    @NonNull
    private static AlbumDetailFragment withArguments(@NonNull Bundle args) {
        AlbumDetailFragment fragment = new AlbumDetailFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public AlbumDetailFragment() {
        super(R.layout.fragment_album_detail);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = requireArguments();
        kind = Kind.valueOf(args.getString(ARG_KIND, Kind.BUCKET.name()));
        bucketId = args.getLong(ARG_BUCKET_ID);
        albumName = args.getString(ARG_NAME, "");
        filter = MediaFilter.fromName(args.getString(ARG_FILTER), MediaFilter.ALL);

        repository = new MediaStoreRepository(requireContext());
        thumbnailLoader = new ThumbnailLoader(requireContext());
        favoritesRepository = new FavoritesRepository(requireContext());
        virtualAlbumRepository = new VirtualAlbumRepository(requireContext());
        settingsRepository = new SettingsRepository(requireContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentAlbumDetailBinding.bind(view);
        setUpMediaGrid();
        loadAlbumMedia();
    }

    @Override
    public void onStart() {
        super.onStart();
        settingsRepository.registerListener(settingsListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Back from the viewer? The user may have changed favourites there.
        if (!allItems.isEmpty()
                && MediaUtils.applyFavourites(allItems, favoritesRepository.getAllUriStrings())) {
            applyView();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        settingsRepository.unregisterListener(settingsListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.mediaRecyclerView.setAdapter(null);
        }
        adapter = null;
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        repository.close();
        thumbnailLoader.close();
    }

    private void setUpMediaGrid() {
        if (binding == null) {
            return;
        }
        adapter = new MediaGridAdapter(thumbnailLoader);
        adapter.setOnMediaClickListener(this::onMediaClicked);

        int columns = Math.max(MIN_COLUMNS,
                getResources().getConfiguration().screenWidthDp / TARGET_COLUMN_WIDTH_DP);
        binding.mediaRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        binding.mediaRecyclerView.setHasFixedSize(true);
        binding.mediaRecyclerView.setAdapter(adapter);
    }

    // CHANGED (Stage 9): opens the ViewPager2 viewer with the list currently shown
    private void onMediaClicked(@NonNull MediaItem item, int position) {
        if (adapter != null) {
            com.contacts.app.phone.calls.gallerydemo.activity.MediaViewerActivity.start(requireContext(), adapter.getCurrentList(), position);
        }
    }

    private void loadAlbumMedia() {
        if (binding == null) {
            return;
        }
        showSection(Section.LOADING);
        repository.loadAllMedia(new MediaStoreRepository.LoadCallback() {
            @Override
            public void onLoaded(@NonNull List<MediaItem> items) {
                if (binding == null) {
                    return;
                }
                List<MediaItem> all = new ArrayList<>(items);
                MediaUtils.applyFavourites(all, favoritesRepository.getAllUriStrings());
                allItems.clear();
                allItems.addAll(selectForKind(all));
                applyView();
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (binding != null) {
                    showMessage(getString(R.string.error_body), true);
                }
            }
        });
    }

    // Picks the items that belong in this screen
    @NonNull
    private List<MediaItem> selectForKind(@NonNull List<MediaItem> all) {
        Set<String> virtualUris = kind == Kind.VIRTUAL
                ? virtualAlbumRepository.getItemUris(albumName)
                : Collections.<String>emptySet();

        List<MediaItem> result = new ArrayList<>();
        int recentPhotos = 0;
        for (MediaItem item : all) { // "all" is newest first
            switch (kind) {
                case BUCKET:
                    if (item.getBucketId() == bucketId) {
                        result.add(item);
                    }
                    break;
                case VIRTUAL:
                    if (virtualUris.contains(item.getUri().toString())) {
                        result.add(item);
                    }
                    break;
                case FEATURED:
                    boolean recent = !item.isVideo() && recentPhotos < FEATURED_RECENT_PHOTOS;
                    if (recent) {
                        recentPhotos++;
                    }
                    if (item.isFavourite() || recent) {
                        result.add(item);
                    }
                    break;
                case FILTER:
                default:
                    if (filter.matches(item)) {
                        result.add(item);
                    }
                    break;
            }
        }
        return result;
    }

    // Applies search + sort to allItems and shows the result
    private void applyView() {
        if (binding == null || adapter == null) {
            return;
        }
        String query = searchQuery.toLowerCase(Locale.getDefault());
        List<MediaItem> visible = new ArrayList<>();
        for (MediaItem item : allItems) {
            if (query.isEmpty() || item.getDisplayName().toLowerCase(Locale.getDefault()).contains(query)) {
                visible.add(item);
            }
        }
        visible.sort(settingsRepository.getSortOrder().mediaComparator());
        adapter.submitList(visible);

        if (visible.isEmpty()) {
            showMessage(getEmptyText(), false);
        } else {
            showSection(Section.CONTENT);
        }
    }

    @NonNull
    private String getEmptyText() {
        if (!searchQuery.isEmpty()) {
            return getString(R.string.search_no_results, searchQuery);
        }
        switch (kind) {
            case BUCKET:
                return getString(R.string.album_detail_empty);
            case VIRTUAL:
                return getString(R.string.virtual_album_empty);
            case FEATURED:
            case FILTER:
            default:
                return getString(R.string.collection_empty);
        }
    }

    // ===== ScreenActions (toolbar) =====

    @Override
    public void onSearchQueryChanged(@NonNull String query) {
        searchQuery = query.trim();
        applyView();
    }

    @Override
    public boolean supportsSelection() {
        return false;
    }

    @Override
    public void startSelectionMode() {
        // not used on this screen
    }

    private void showMessage(@NonNull String text, boolean showRetry) {
        if (binding == null) {
            return;
        }
        binding.messageText.setText(text);
        binding.messageAction.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        binding.messageAction.setOnClickListener(v -> loadAlbumMedia());
        showSection(Section.MESSAGE);
    }

    private void showSection(Section section) {
        if (binding == null) {
            return;
        }
        binding.loadingIndicator.setVisibility(section == Section.LOADING ? View.VISIBLE : View.GONE);
        binding.messageContainer.setVisibility(section == Section.MESSAGE ? View.VISIBLE : View.GONE);
        binding.mediaRecyclerView.setVisibility(section == Section.CONTENT ? View.VISIBLE : View.GONE);
    }
}
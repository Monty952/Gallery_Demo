package com.contacts.app.phone.calls.gallerydemo.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.adapter.MediaGridAdapter;
import com.contacts.app.phone.calls.gallerydemo.data.FavoritesRepository;
import com.contacts.app.phone.calls.gallerydemo.data.MediaStoreRepository;
import com.contacts.app.phone.calls.gallerydemo.data.SettingsRepository;
import com.contacts.app.phone.calls.gallerydemo.data.VirtualAlbumRepository;
import com.contacts.app.phone.calls.gallerydemo.databinding.BottomSheetMediaFilterBinding;
import com.contacts.app.phone.calls.gallerydemo.databinding.FragmentPhotosBinding;
import com.contacts.app.phone.calls.gallerydemo.dialog.AddToAlbumDialog;
import com.contacts.app.phone.calls.gallerydemo.model.MediaFilter;
import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;
import com.contacts.app.phone.calls.gallerydemo.util.MediaUtils;
import com.contacts.app.phone.calls.gallerydemo.util.PermissionHelper;
import com.contacts.app.phone.calls.gallerydemo.util.ThumbnailLoader;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class PhotosFragment extends Fragment implements ScreenActions {

    private static final int MIN_COLUMNS = 3;
    private static final int TARGET_COLUMN_WIDTH_DP = 110;
    private static final String STATE_FILTER = "state_filter";

    private enum DenialState { NOT_ASKED, DENIED, BLOCKED }

    private enum Section { LOADING, MESSAGE, CONTENT }

    @Nullable
    private FragmentPhotosBinding binding;
    @Nullable
    private MediaGridAdapter adapter;
    @Nullable
    private ActionMode actionMode; // NEW (Stage 11): the selection-mode toolbar

    private MediaStoreRepository repository;
    private ThumbnailLoader thumbnailLoader;
    private FavoritesRepository favoritesRepository;
    private VirtualAlbumRepository virtualAlbumRepository; // NEW (Stage 7)
    private SettingsRepository settingsRepository;         // NEW (Stage 11)

    private final List<MediaItem> allItems = new ArrayList<>();
    private MediaFilter currentFilter = MediaFilter.ALL;
    private String searchQuery = ""; // NEW (Stage 11)

    @Nullable
    private PermissionHelper.Access shownAccess;
    private DenialState denialState = DenialState.NOT_ASKED;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    results -> onPermissionResult());

    // NEW (Stage 11): sort changed -> re-sort. Refresh requested -> reload.
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener = (prefs, key) -> {
        if (binding == null) {
            return;
        }
        if (SettingsRepository.KEY_SORT_ORDER.equals(key)) {
            applyFilter(true);
        } else if (SettingsRepository.KEY_REFRESH_TOKEN.equals(key)) {
            refresh();
        }
    };

    // NEW (Stage 11): what happens in the selection-mode toolbar
    private final ActionMode.Callback selectionCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_selection, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int id = item.getItemId();
            if (id == R.id.action_select_all) {
                if (adapter != null) {
                    adapter.selectAll();
                }
                return true;
            }
            if (id == R.id.action_select_favourite) {
                favouriteSelected();
                mode.finish();
                return true;
            }
            if (id == R.id.action_select_add_to_album) {
                addSelectedToAlbum();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            if (adapter != null) {
                adapter.setSelectionMode(false);
            }
        }
    };

    public PhotosFragment() {
        super(R.layout.fragment_photos);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new MediaStoreRepository(requireContext());
        thumbnailLoader = new ThumbnailLoader(requireContext());
        favoritesRepository = new FavoritesRepository(requireContext());
        virtualAlbumRepository = new VirtualAlbumRepository(requireContext());
        settingsRepository = new SettingsRepository(requireContext());
        if (savedInstanceState != null) {
            currentFilter = MediaFilter.fromName(
                    savedInstanceState.getString(STATE_FILTER), MediaFilter.ALL);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentPhotosBinding.bind(view);
        binding.partialBannerAction.setOnClickListener(v -> requestAccess());
        setUpMediaGrid();
        binding.filterFab.setOnClickListener(v -> showFilterBottomSheet());
    }

    @Override
    public void onStart() {
        super.onStart();
        settingsRepository.registerListener(settingsListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        syncWithAccess();
        syncFavouritesFromStorage(); // NEW (Stage 9): the viewer may have changed favourites
    }

    @Override
    public void onStop() {
        super.onStop();
        settingsRepository.unregisterListener(settingsListener);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden && actionMode != null) {
            actionMode.finish(); // leaving this tab ends selection mode
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_FILTER, currentFilter.name());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (actionMode != null) {
            actionMode.finish();
        }
        if (binding != null) {
            binding.mediaRecyclerView.setAdapter(null);
        }
        adapter = null;
        binding = null;
        shownAccess = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        repository.close();
        thumbnailLoader.close();
    }

    public void refresh() {
        shownAccess = null;
        if (binding != null) {
            syncWithAccess();
        }
    }

    private void setUpMediaGrid() {
        if (binding == null) {
            return;
        }
        adapter = new MediaGridAdapter(thumbnailLoader);
        adapter.setOnMediaClickListener(this::onMediaClicked);
        adapter.setOnMediaLongClickListener(this::showItemMenu);
        adapter.setOnSelectionChangedListener(this::updateSelectionTitle); // NEW (Stage 11)

        binding.mediaRecyclerView.setLayoutManager(
                new GridLayoutManager(requireContext(), calculateColumnCount()));
        binding.mediaRecyclerView.setHasFixedSize(true);
        binding.mediaRecyclerView.setAdapter(adapter);
    }

    private int calculateColumnCount() {
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        return Math.max(MIN_COLUMNS, screenWidthDp / TARGET_COLUMN_WIDTH_DP);
    }

    // CHANGED (Stage 9): the Toast is replaced by the real viewer
    private void onMediaClicked(@NonNull MediaItem item, int position) {
        if (adapter != null) {
            // getCurrentList() is exactly what the user sees (filter + search + sort applied)
            com.contacts.app.phone.calls.gallerydemo.activity.MediaViewerActivity.start(requireContext(), adapter.getCurrentList(), position);
        }
    }

    // ===== Filter bottom sheet =====

    private void showFilterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        BottomSheetMediaFilterBinding sheetBinding =
                BottomSheetMediaFilterBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());

        sheetBinding.filterNavigationView.setCheckedItem(currentFilter.getMenuItemId());
        sheetBinding.filterNavigationView.setNavigationItemSelectedListener(menuItem -> {
            setFilter(MediaFilter.fromMenuItemId(menuItem.getItemId(), currentFilter));
            dialog.dismiss();
            return true;
        });

        dialog.show();
    }

    private void showFilterMenu(@Nullable View anchor) {
        showFilterBottomSheet();
    }

    private void setFilter(@NonNull MediaFilter filter) {
        if (filter == currentFilter) {
            return;
        }
        currentFilter = filter;
        applyFilter(true);
    }

    // CHANGED (Stage 11): filter + search + sort, all in memory (no MediaStore query)
    private void applyFilter(boolean scrollToTop) {
        if (binding == null || adapter == null) {
            return;
        }
        String query = searchQuery.toLowerCase(Locale.getDefault());
        List<MediaItem> visible = new ArrayList<>();
        for (MediaItem item : allItems) {
            if (!currentFilter.matches(item)) {
                continue;
            }
            if (!query.isEmpty()
                    && !item.getDisplayName().toLowerCase(Locale.getDefault()).contains(query)) {
                continue;
            }
            visible.add(item);
        }
        visible.sort(settingsRepository.getSortOrder().mediaComparator());

        adapter.submitList(visible, () -> {
            if (scrollToTop && binding != null) {
                binding.mediaRecyclerView.scrollToPosition(0);
            }
        });

        int count = visible.size();
        String countText = getResources().getQuantityString(R.plurals.item_count, count, count);
        binding.filterLabel.setText(getString(
                R.string.filter_status, getString(currentFilter.getLabelRes()), countText));

        binding.filterEmptyText.setText(getEmptyText());
        binding.filterEmptyText.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @NonNull
    private String getEmptyText() {
        if (!searchQuery.isEmpty()) {
            return getString(R.string.search_no_results, searchQuery);
        }
        switch (currentFilter) {
            case PHOTOS:
                return getString(R.string.filter_empty_photos);
            case VIDEOS:
                return getString(R.string.filter_empty_videos);
            case SCREENSHOTS:
                return getString(R.string.filter_empty_screens);
            case FAVOURITES:
                return getString(R.string.filter_empty_favourites);
            case ALL:
            default:
                return getString(R.string.empty_body);
        }
    }

    // ===== NEW (Stage 11): ScreenActions (called by the toolbar in MainActivity) =====

    @Override
    public void onSearchQueryChanged(@NonNull String query) {
        searchQuery = query.trim();
        applyFilter(true);
    }

    @Override
    public boolean supportsSelection() {
        return true;
    }

    @Override
    public void startSelectionMode() {
        if (adapter == null || actionMode != null) {
            return;
        }
        if (adapter.getItemCount() == 0) {
            Toast.makeText(requireContext(), R.string.nothing_to_select, Toast.LENGTH_SHORT).show();
            return;
        }
        adapter.setSelectionMode(true);
        actionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(selectionCallback);
        updateSelectionTitle(0);
    }

    private void updateSelectionTitle(int selectedCount) {
        if (actionMode != null) {
            actionMode.setTitle(getString(R.string.selected_count, selectedCount));
        }
    }

    private void favouriteSelected() {
        if (adapter != null) {
            setFavourite(adapter.getSelectedItems(), true);
        }
    }

    private void addSelectedToAlbum() {
        if (adapter == null) {
            return;
        }
        List<MediaItem> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            return;
        }
        AddToAlbumDialog.show(requireContext(), virtualAlbumRepository, selected, () -> {
            if (actionMode != null) {
                actionMode.finish();
            }
        });
    }

    // ===== Item menu and favourites =====

    // Menu shown when the user presses and holds an item
    private void showItemMenu(@NonNull MediaItem item, @NonNull View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.menu_media_item, popup.getMenu());
        popup.setForceShowIcon(true);

        popup.getMenu().findItem(R.id.action_toggle_favourite)
                .setTitle(item.isFavourite() ? R.string.favourite_remove : R.string.favourite_add)
                .setIcon(item.isFavourite() ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);

        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.action_toggle_favourite) {
                setFavourite(Collections.singletonList(item), !item.isFavourite());
                return true;
            }
            if (id == R.id.action_add_to_album) { // NEW (Stage 7)
                AddToAlbumDialog.show(requireContext(), virtualAlbumRepository,
                        Collections.singletonList(item), null);
                return true;
            }
            return false;
        });
        popup.show();
    }

    // CHANGED (Stage 11): handles one OR many items with a single save
    private void setFavourite(@NonNull Collection<MediaItem> targets, boolean value) {
        if (targets.isEmpty()) {
            return;
        }
        Set<Uri> uris = new HashSet<>();
        for (MediaItem target : targets) {
            uris.add(target.getUri());
        }
        favoritesRepository.setFavourites(uris, value);

        // Replace changed items by COPIES so DiffUtil sees the difference
        for (int i = 0; i < allItems.size(); i++) {
            MediaItem item = allItems.get(i);
            if (uris.contains(item.getUri()) && item.isFavourite() != value) {
                allItems.set(i, item.copyWithFavourite(value));
            }
        }
        applyFilter(false);

        Toast.makeText(requireContext(),
                value ? R.string.favourite_added : R.string.favourite_removed,
                Toast.LENGTH_SHORT).show();
    }

    // NEW (Stage 9): after the viewer closes, re-read the saved favourites
    private void syncFavouritesFromStorage() {
        if (binding == null || allItems.isEmpty()) {
            return;
        }
        if (MediaUtils.applyFavourites(allItems, favoritesRepository.getAllUriStrings())) {
            applyFilter(false);
        }
    }

    // ===== Permissions and loading =====

    private void syncWithAccess() {
        PermissionHelper.Access access = PermissionHelper.getAccess(requireContext());
        if (access == shownAccess) {
            return;
        }
        shownAccess = access;

        if (access == PermissionHelper.Access.NONE) {
            showPermissionRequired();
        } else {
            denialState = DenialState.NOT_ASKED;
            loadMedia(access);
        }
    }

    private void requestAccess() {
        permissionLauncher.launch(PermissionHelper.getRequestedPermissions());
    }

    private void onPermissionResult() {
        if (binding == null) {
            return;
        }
        if (PermissionHelper.getAccess(requireContext()) == PermissionHelper.Access.NONE) {
            boolean canAskAgain =
                    shouldShowRequestPermissionRationale(PermissionHelper.getPrimaryPermission());
            denialState = canAskAgain ? DenialState.DENIED : DenialState.BLOCKED;
        }
        shownAccess = null;
        syncWithAccess();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(intent);
    }

    private void loadMedia(PermissionHelper.Access access) {
        showSection(Section.LOADING);
        repository.loadAllMedia(new MediaStoreRepository.LoadCallback() {
            @Override
            public void onLoaded(@NonNull List<MediaItem> items) {
                if (binding != null) {
                    showLoaded(items, access);
                }
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (binding != null) {
                    showLoadError();
                }
            }
        });
    }

    private void showLoaded(List<MediaItem> items, PermissionHelper.Access access) {
        if (binding == null || adapter == null) {
            return;
        }
        boolean partial = access == PermissionHelper.Access.PARTIAL;

        allItems.clear();
        allItems.addAll(items);
        MediaUtils.applyFavourites(allItems, favoritesRepository.getAllUriStrings());

        if (allItems.isEmpty()) {
            adapter.submitList(Collections.<MediaItem>emptyList());
            if (partial) {
                showMessage(R.drawable.ic_photos, R.string.empty_partial_title,
                        R.string.empty_partial_body, R.string.action_select_items,
                        v -> requestAccess());
            } else {
                showMessage(R.drawable.ic_photos, R.string.empty_title,
                        R.string.empty_body, R.string.action_refresh,
                        v -> refresh());
            }
            return;
        }

        applyFilter(false);
        binding.partialBanner.setVisibility(partial ? View.VISIBLE : View.GONE);
        showSection(Section.CONTENT);
    }

    private void showLoadError() {
        if (PermissionHelper.getAccess(requireContext()) == PermissionHelper.Access.NONE) {
            shownAccess = PermissionHelper.Access.NONE;
            showPermissionRequired();
            return;
        }
        showMessage(R.drawable.ic_error, R.string.error_title, R.string.error_body,
                R.string.action_retry, v -> refresh());
    }

    private void showPermissionRequired() {
        switch (denialState) {
            case BLOCKED:
                showMessage(R.drawable.ic_photos, R.string.permission_denied_title,
                        R.string.permission_blocked_body, R.string.permission_action_settings,
                        v -> openAppSettings());
                break;
            case DENIED:
                showMessage(R.drawable.ic_photos, R.string.permission_denied_title,
                        R.string.permission_denied_body, R.string.permission_action_allow,
                        v -> requestAccess());
                break;
            default:
                showMessage(R.drawable.ic_photos, R.string.permission_title,
                        R.string.permission_body, R.string.permission_action_allow,
                        v -> requestAccess());
                break;
        }
    }

    private void showMessage(@DrawableRes int icon,
                             @StringRes int title,
                             @StringRes int body,
                             @StringRes int actionLabel,
                             View.OnClickListener action) {
        if (binding == null) {
            return;
        }
        binding.messageIcon.setImageResource(icon);
        binding.messageTitle.setText(title);
        binding.messageBody.setText(body);
        binding.messageAction.setText(actionLabel);
        binding.messageAction.setOnClickListener(action);
        showSection(Section.MESSAGE);
    }

    private void showSection(Section section) {
        if (binding == null) {
            return;
        }
        binding.loadingIndicator.setVisibility(section == Section.LOADING ? View.VISIBLE : View.GONE);
        binding.messageContainer.setVisibility(section == Section.MESSAGE ? View.VISIBLE : View.GONE);
        binding.contentContainer.setVisibility(section == Section.CONTENT ? View.VISIBLE : View.GONE);

        if (section == Section.CONTENT) {
            binding.filterFab.show();
        } else {
            binding.filterFab.hide();
        }
    }
}
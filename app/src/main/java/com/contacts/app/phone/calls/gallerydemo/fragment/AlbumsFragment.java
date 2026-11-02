package com.contacts.app.phone.calls.gallerydemo.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.activity.SettingsActivity;
import com.contacts.app.phone.calls.gallerydemo.adapter.AlbumGridAdapter;
import com.contacts.app.phone.calls.gallerydemo.data.AlbumRepository;
import com.contacts.app.phone.calls.gallerydemo.data.MediaStoreRepository;
import com.contacts.app.phone.calls.gallerydemo.data.SettingsRepository;
import com.contacts.app.phone.calls.gallerydemo.data.VirtualAlbumRepository;
import com.contacts.app.phone.calls.gallerydemo.databinding.FragmentAlbumsBinding;
import com.contacts.app.phone.calls.gallerydemo.dialog.CreateAlbumDialogFragment;
import com.contacts.app.phone.calls.gallerydemo.dialog.CustomBottomSheetDialogFragment;
import com.contacts.app.phone.calls.gallerydemo.model.Album;
import com.contacts.app.phone.calls.gallerydemo.model.MediaFilter;
import com.contacts.app.phone.calls.gallerydemo.util.PermissionHelper;
import com.contacts.app.phone.calls.gallerydemo.util.ThumbnailLoader;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AlbumsFragment extends Fragment implements ScreenActions {

    public static final String DETAIL_TAG = "album_detail";

    private static final String CREATE_DIALOG_TAG = "create_album";
    private static final String SHEET_TAG = "albums_sheet";
    private static final String STATE_SHOW_ALL = "state_show_all";
    private static final int MIN_COLUMNS = 2;
    private static final int TARGET_COLUMN_WIDTH_DP = 170;
    private static final int MAIN_ALBUM_LIMIT = 8; // the "main collection"

    private enum DenialState { NOT_ASKED, DENIED, BLOCKED }

    private enum Section { LOADING, MESSAGE, CONTENT }

    @Nullable
    private FragmentAlbumsBinding binding;
    @Nullable
    private AlbumGridAdapter adapter;

    private MediaStoreRepository mediaStoreRepository;
    private VirtualAlbumRepository virtualAlbumRepository;
    private AlbumRepository albumRepository;
    private SettingsRepository settingsRepository;
    private ThumbnailLoader thumbnailLoader;

    private final List<Album> allAlbums = new ArrayList<>();
    private String searchQuery = "";
    private boolean showAllAlbums;

    private DenialState denialState = DenialState.NOT_ASKED;
    private boolean hasLoadedOnce;
    private boolean openingDetail;

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
            applyAlbums();
        } else if (SettingsRepository.KEY_REFRESH_TOKEN.equals(key)) {
            syncWithAccess();
        }
    };

    public AlbumsFragment() {
        super(R.layout.fragment_albums);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mediaStoreRepository = new MediaStoreRepository(requireContext());
        virtualAlbumRepository = new VirtualAlbumRepository(requireContext());
        albumRepository = new AlbumRepository(mediaStoreRepository, virtualAlbumRepository);
        settingsRepository = new SettingsRepository(requireContext());
        thumbnailLoader = new ThumbnailLoader(requireContext());
        if (savedInstanceState != null) {
            showAllAlbums = savedInstanceState.getBoolean(STATE_SHOW_ALL);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentAlbumsBinding.bind(view);
        binding.partialBannerAction.setOnClickListener(v -> requestAccess());
        setUpAlbumGrid();

        // NEW (Stage 7 and 8): the two round buttons
        binding.createAlbumFab.setOnClickListener(v -> showCreateAlbumDialog());
        binding.albumsMenuFab.setOnClickListener(v -> showBottomSheet());

        // NEW (Stage 7 and 8): answers coming back from the dialog and the bottom sheet.
        // Both were shown with getChildFragmentManager(), so we listen on it too.
        getChildFragmentManager().setFragmentResultListener(
                CreateAlbumDialogFragment.REQUEST_KEY, getViewLifecycleOwner(),
                (requestKey, result) -> {
                    String name = result.getString(CreateAlbumDialogFragment.RESULT_NAME);
                    if (name != null) {
                        onAlbumNameChosen(name);
                    }
                });
        getChildFragmentManager().setFragmentResultListener(
                CustomBottomSheetDialogFragment.REQUEST_KEY, getViewLifecycleOwner(),
                (requestKey, result) -> onSheetOption(
                        result.getString(CustomBottomSheetDialogFragment.RESULT_OPTION, "")));
    }

    @Override
    public void onStart() {
        super.onStart();
        settingsRepository.registerListener(settingsListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            syncWithAccess();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            openingDetail = false;
            syncWithAccess();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SHOW_ALL, showAllAlbums);
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
            binding.albumRecyclerView.setAdapter(null);
        }
        adapter = null;
        binding = null;
        hasLoadedOnce = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaStoreRepository.close();
        thumbnailLoader.close();
    }

    private void setUpAlbumGrid() {
        if (binding == null) {
            return;
        }
        adapter = new AlbumGridAdapter(thumbnailLoader);
        adapter.setOnAlbumClickListener(this::openAlbum);
        binding.albumRecyclerView.setLayoutManager(
                new GridLayoutManager(requireContext(), calculateColumnCount()));
        binding.albumRecyclerView.setAdapter(adapter);
    }

    private int calculateColumnCount() {
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        return Math.max(MIN_COLUMNS, screenWidthDp / TARGET_COLUMN_WIDTH_DP);
    }

    // ===== NEW (Stage 11): search, and sort + "main collection" limit =====

    @Override
    public void onSearchQueryChanged(@NonNull String query) {
        searchQuery = query.trim();
        applyAlbums();
    }

    @Override
    public boolean supportsSelection() {
        return false;
    }

    @Override
    public void startSelectionMode() {
        // not used on this screen
    }

    // Search -> sort -> limit to the main collection (unless searching or "show all" is on)
    private void applyAlbums() {
        if (binding == null || adapter == null || allAlbums.isEmpty()) {
            return;
        }
        String query = searchQuery.toLowerCase(Locale.getDefault());
        List<Album> result = new ArrayList<>();
        for (Album album : allAlbums) {
            String name = album.getDisplayName(requireContext()).toLowerCase(Locale.getDefault());
            if (query.isEmpty() || name.contains(query)) {
                result.add(album);
            }
        }
        result.sort(settingsRepository.getSortOrder().albumComparator());

        int totalMatching = result.size();
        boolean limited = query.isEmpty() && !showAllAlbums && totalMatching > MAIN_ALBUM_LIMIT;
        if (limited) {
            result = new ArrayList<>(result.subList(0, MAIN_ALBUM_LIMIT));
        }
        adapter.submitList(result);

        binding.albumsStatus.setVisibility(limited ? View.VISIBLE : View.GONE);
        if (limited) {
            binding.albumsStatus.setText(
                    getString(R.string.albums_status_limited, MAIN_ALBUM_LIMIT, totalMatching));
        }
        boolean nothingFound = result.isEmpty();
        binding.albumsEmptyText.setVisibility(nothingFound ? View.VISIBLE : View.GONE);
        if (nothingFound) {
            binding.albumsEmptyText.setText(getString(R.string.search_no_results, searchQuery));
        }
    }

    // ===== NEW (Stage 7): create album =====

    private void showCreateAlbumDialog() {
        if (getChildFragmentManager().findFragmentByTag(CREATE_DIALOG_TAG) != null) {
            return;
        }
        ArrayList<String> names = new ArrayList<>();
        for (Album album : allAlbums) {
            names.add(album.getDisplayName(requireContext()));
        }
        CreateAlbumDialogFragment.newInstance(names)
                .show(getChildFragmentManager(), CREATE_DIALOG_TAG);
    }

    private void onAlbumNameChosen(@NonNull String name) {
        if (virtualAlbumRepository.createAlbum(name)) {
            Toast.makeText(requireContext(), getString(R.string.album_created, name),
                    Toast.LENGTH_SHORT).show();
            syncWithAccess(); // reload so the new album appears
        } else {
            Toast.makeText(requireContext(), R.string.album_create_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ===== NEW (Stage 8): bottom sheet =====

    private void showBottomSheet() {
        if (getChildFragmentManager().findFragmentByTag(SHEET_TAG) != null) {
            return;
        }
        new CustomBottomSheetDialogFragment().show(getChildFragmentManager(), SHEET_TAG);
    }

    private void onSheetOption(@NonNull String option) {
        switch (option) {
            case CustomBottomSheetDialogFragment.OPTION_MORE_ALBUMS:
                toggleShowAllAlbums();
                break;
            case CustomBottomSheetDialogFragment.OPTION_FEATURED:
                openDetail(getString(R.string.featured_title), AlbumDetailFragment.forFeatured());
                break;
            case CustomBottomSheetDialogFragment.OPTION_MEDIA_TYPE:
                showMediaTypeDialog();
                break;
            case CustomBottomSheetDialogFragment.OPTION_MORE:
                startActivity(new Intent(requireContext(), SettingsActivity.class));
                break;
            default:
                break;
        }
    }

    private void toggleShowAllAlbums() {
        if (allAlbums.size() <= MAIN_ALBUM_LIMIT) {
            Toast.makeText(requireContext(),
                    getString(R.string.more_albums_all_shown, allAlbums.size()), Toast.LENGTH_SHORT).show();
            return;
        }
        showAllAlbums = !showAllAlbums;
        applyAlbums();
        Toast.makeText(requireContext(),
                showAllAlbums ? R.string.more_albums_showing_all : R.string.more_albums_showing_main,
                Toast.LENGTH_SHORT).show();
    }

    private void showMediaTypeDialog() {
        MediaFilter[] options = {
                MediaFilter.PHOTOS, MediaFilter.VIDEOS, MediaFilter.SCREENSHOTS, MediaFilter.FAVOURITES
        };
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = getString(options[i].getLabelRes());
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.media_type_title)
                .setItems(labels, (dialog, which) ->
                        openDetail(labels[which].toString(), AlbumDetailFragment.forFilter(options[which])))
                .show();
    }

    // ===== Opening a screen on top of this one =====

    private void openAlbum(@NonNull Album album) {
        AlbumDetailFragment detail = album.isVirtual()
                ? AlbumDetailFragment.forVirtual(album.getName())
                : AlbumDetailFragment.forBucket(album.getBucketId());
        openDetail(album.getDisplayName(requireContext()), detail);
    }

    // CHANGED (Stage 8): shared by albums, Featured and Media type
    private void openDetail(@NonNull String title, @NonNull Fragment detail) {
        if (openingDetail) {
            return;
        }
        openingDetail = true;
        getParentFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .hide(this)
                .add(R.id.fragment_container, detail, DETAIL_TAG)
                .addToBackStack(title)
                .commit();
    }

    // ===== Permissions and loading =====

    private void syncWithAccess() {
        if (binding == null) {
            return;
        }
        PermissionHelper.Access access = PermissionHelper.getAccess(requireContext());
        if (access == PermissionHelper.Access.NONE) {
            hasLoadedOnce = false;
            allAlbums.clear();
            if (adapter != null) {
                adapter.submitList(Collections.<Album>emptyList());
            }
            showPermissionRequired();
        } else {
            denialState = DenialState.NOT_ASKED;
            loadAlbums(access);
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
        syncWithAccess();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(intent);
    }

    private void loadAlbums(PermissionHelper.Access access) {
        if (!hasLoadedOnce) {
            showSection(Section.LOADING);
        }
        albumRepository.loadAlbums(new AlbumRepository.LoadCallback() {
            @Override
            public void onLoaded(@NonNull List<Album> albums) {
                if (binding != null) {
                    showLoaded(albums, access);
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

    private void showLoaded(List<Album> albums, PermissionHelper.Access access) {
        if (binding == null || adapter == null) {
            return;
        }
        hasLoadedOnce = true;
        allAlbums.clear();
        allAlbums.addAll(albums);

        if (allAlbums.isEmpty()) {
            adapter.submitList(Collections.<Album>emptyList());
            if (access == PermissionHelper.Access.PARTIAL) {
                showMessage(R.drawable.ic_albums, R.string.empty_partial_title,
                        R.string.empty_partial_body, R.string.action_select_items,
                        v -> requestAccess());
            } else {
                showMessage(R.drawable.ic_albums, R.string.albums_empty_title,
                        R.string.albums_empty_body, R.string.action_refresh,
                        v -> syncWithAccess());
            }
            setButtonsVisible(true); // the user can still create a custom album
            return;
        }

        binding.partialBanner.setVisibility(
                access == PermissionHelper.Access.PARTIAL ? View.VISIBLE : View.GONE);
        applyAlbums();
        showSection(Section.CONTENT);
    }

    private void showLoadError() {
        if (PermissionHelper.getAccess(requireContext()) == PermissionHelper.Access.NONE) {
            showPermissionRequired();
            return;
        }
        showMessage(R.drawable.ic_error, R.string.error_title, R.string.error_body,
                R.string.action_retry, v -> syncWithAccess());
    }

    private void showPermissionRequired() {
        switch (denialState) {
            case BLOCKED:
                showMessage(R.drawable.ic_albums, R.string.permission_denied_title,
                        R.string.permission_blocked_body, R.string.permission_action_settings,
                        v -> openAppSettings());
                break;
            case DENIED:
                showMessage(R.drawable.ic_albums, R.string.permission_denied_title,
                        R.string.permission_denied_body, R.string.permission_action_allow,
                        v -> requestAccess());
                break;
            default:
                showMessage(R.drawable.ic_albums, R.string.permission_title,
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
        setButtonsVisible(section == Section.CONTENT);
    }

    // The two round buttons are only shown when they make sense
    private void setButtonsVisible(boolean visible) {
        if (binding == null) {
            return;
        }
        if (visible) {
            binding.createAlbumFab.show();
            binding.albumsMenuFab.show();
        } else {
            binding.createAlbumFab.hide();
            binding.albumsMenuFab.hide();
        }
    }
}
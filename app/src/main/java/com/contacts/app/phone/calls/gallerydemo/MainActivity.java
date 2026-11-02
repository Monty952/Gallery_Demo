package com.contacts.app.phone.calls.gallerydemo;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.contacts.app.phone.calls.gallerydemo.activity.SettingsActivity;
import com.contacts.app.phone.calls.gallerydemo.data.SettingsRepository;
import com.contacts.app.phone.calls.gallerydemo.databinding.ActivityMainBinding;
import com.contacts.app.phone.calls.gallerydemo.dialog.SortDialog;
import com.contacts.app.phone.calls.gallerydemo.fragment.AlbumsFragment;
import com.contacts.app.phone.calls.gallerydemo.fragment.CallHistoryFragment;
import com.contacts.app.phone.calls.gallerydemo.fragment.PhotosFragment;
import com.contacts.app.phone.calls.gallerydemo.fragment.ScreenActions;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private static final String TAG_PHOTOS = "photos";
    private static final String TAG_ALBUMS = "albums";
    private static final String TAG_CALLS = "calls";
    private static final String STATE_TAB = "state_tab";

    // CHANGED: the bottom bar is gone, so we keep track of the tab ourselves
    private static final int TAB_PHOTOS = 0;
    private static final int TAB_ALBUMS = 1;
    private static final int TAB_CALLS = 2;

    private ActivityMainBinding binding;
    private SettingsRepository settingsRepository;
    @Nullable
    private MenuItem searchItem;
    private int currentTab = TAB_PHOTOS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        applySystemBarInsets();
        settingsRepository = new SettingsRepository(this);

        if (savedInstanceState == null) {
            addInitialFragments();
        } else {
            // After rotation the fragments are restored by Android, but this field is not
            currentTab = savedInstanceState.getInt(STATE_TAB, TAB_PHOTOS);
        }

        // NEW: the two floating buttons replace BottomNavigationView
        binding.tabPhotosFab.setOnClickListener(v -> onTabClicked(TAB_PHOTOS));
        binding.tabAlbumsFab.setOnClickListener(v -> onTabClicked(TAB_ALBUMS));
        binding.tabCallsFab.setOnClickListener(v -> onTabClicked(TAB_CALLS));
        updateTabButtons();

        getSupportFragmentManager().addOnBackStackChangedListener(this::updateToolbarForBackStack);
        updateToolbarForBackStack();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    // ===== NEW: tab buttons =====

    private void onTabClicked(int tab) {
        if (tab == currentTab) {
            closeAlbumDetail(); // tapping the active tab closes an open album
        } else {
            showSection(tab);
        }
    }

    // The selected tab gets a filled primary-colour circle, the other one stays flat
    private void updateTabButtons() {
        styleTab(binding.tabPhotosFab, currentTab == TAB_PHOTOS);
        styleTab(binding.tabAlbumsFab, currentTab == TAB_ALBUMS);
        styleTab(binding.tabCallsFab, currentTab == TAB_CALLS);
    }

    private void styleTab(@NonNull FloatingActionButton fab, boolean selected) {
        int background = MaterialColors.getColor(fab, selected
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorSurface);
        int icon = MaterialColors.getColor(fab, selected
                ? com.google.android.material.R.attr.colorOnPrimary
                : com.google.android.material.R.attr.colorOnSurfaceVariant);
        fab.setBackgroundTintList(ColorStateList.valueOf(background));
        fab.setImageTintList(ColorStateList.valueOf(icon));
    }

    // ===== The toolbar menu =====

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.search_hint));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchView.clearFocus();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    ScreenActions screen = getActiveScreen();
                    if (screen != null) {
                        screen.onSearchQueryChanged(newText == null ? "" : newText);
                    }
                    return true;
                }
            });
        }
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                clearSearchEverywhere();
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem select = menu.findItem(R.id.action_select);
        if (select != null) {
            select.setVisible(isPhotosTabVisible());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sort) {
            SortDialog.show(this, settingsRepository, null);
            return true;
        }
        if (id == R.id.action_select) {
            ScreenActions screen = getActiveScreen();
            if (screen != null && screen.supportsSelection()) {
                screen.startSelectionMode();
            }
            return true;
        }
        if (id == R.id.action_refresh) {
            settingsRepository.requestRefresh();
            Toast.makeText(this, R.string.refreshing, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    // ===== Finding the screen the user is looking at =====

    // CHANGED: uses currentTab instead of bottomNavigation.getSelectedItemId()
    private boolean isPhotosTabVisible() {
        return getSupportFragmentManager().getBackStackEntryCount() == 0
                && currentTab == TAB_PHOTOS;
    }

    @Nullable
    private ScreenActions getActiveScreen() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment;
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragment = fragmentManager.findFragmentByTag(AlbumsFragment.DETAIL_TAG);
        } else if (currentTab == TAB_ALBUMS) {
            fragment = fragmentManager.findFragmentByTag(TAG_ALBUMS);
        } else if (currentTab == TAB_CALLS) {
            fragment = fragmentManager.findFragmentByTag(TAG_CALLS);
        } else {
            fragment = fragmentManager.findFragmentByTag(TAG_PHOTOS);
        }
        return fragment instanceof ScreenActions ? (ScreenActions) fragment : null;
    }

    private void clearSearchEverywhere() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ScreenActions) {
                ((ScreenActions) fragment).onSearchQueryChanged("");
            }
        }
    }

    private void onScreenChanged() {
        if (searchItem != null && searchItem.isActionViewExpanded()) {
            searchItem.collapseActionView();
        }
        invalidateOptionsMenu();
    }

    // ===== Fragments and navigation =====

    private void addInitialFragments() {
        Fragment photos = new PhotosFragment();
        Fragment albums = new AlbumsFragment();
        Fragment calls = new CallHistoryFragment();
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container, photos, TAG_PHOTOS)
                .add(R.id.fragment_container, albums, TAG_ALBUMS)
                .add(R.id.fragment_container, calls, TAG_CALLS)
                .hide(albums)
                .hide(calls)
                .commit();
    }

    // CHANGED: takes TAB_PHOTOS / TAB_ALBUMS instead of a menu id
    private void showSection(int tab) {
        closeAlbumDetail();

        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment photos = fragmentManager.findFragmentByTag(TAG_PHOTOS);
        Fragment albums = fragmentManager.findFragmentByTag(TAG_ALBUMS);
        Fragment calls = fragmentManager.findFragmentByTag(TAG_CALLS);
        if (photos == null || albums == null || calls == null) {
            return;
        }
        // NEW CODE
        currentTab = tab;

        fragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                        .hide(photos)
                        .hide(albums)
                        .hide(calls)
                .show(tab == TAB_PHOTOS ? photos : tab == TAB_ALBUMS ? albums : calls)
                .commit();

         // OLD CODD
        /*currentTab = tab;
        boolean showPhotos = tab == TAB_PHOTOS;
        fragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .show(showPhotos ? photos : albums)
                .hide(showPhotos ? albums : photos)
                .commit();*/

        updateTabButtons();
        onScreenChanged();
    }

    private void closeAlbumDetail() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    private void updateToolbarForBackStack() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        int entryCount = fragmentManager.getBackStackEntryCount();
        if (entryCount > 0) {
            String title = fragmentManager.getBackStackEntryAt(entryCount - 1).getName();
            actionBar.setTitle(title != null ? title : getString(R.string.app_name));
            actionBar.setDisplayHomeAsUpEnabled(true);
        } else {
            actionBar.setTitle(R.string.app_name);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
        onScreenChanged();
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
package com.contacts.app.phone.calls.gallerydemo.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.Formatter;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.adapter.ToolsListAdapter;
import com.contacts.app.phone.calls.gallerydemo.data.AlbumRepository;
import com.contacts.app.phone.calls.gallerydemo.data.FavoritesRepository;
import com.contacts.app.phone.calls.gallerydemo.data.MediaStoreRepository;
import com.contacts.app.phone.calls.gallerydemo.data.SettingsRepository;
import com.contacts.app.phone.calls.gallerydemo.data.VirtualAlbumRepository;
import com.contacts.app.phone.calls.gallerydemo.databinding.ActivitySettingsBinding;
import com.contacts.app.phone.calls.gallerydemo.dialog.SortDialog;
import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;
import com.contacts.app.phone.calls.gallerydemo.util.PermissionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// "More" screen: a ListView of tools (sort, refresh, info, permissions)
public class SettingsActivity extends AppCompatActivity {

    private static final int TOOL_SORT = 1;
    private static final int TOOL_REFRESH = 2;
    private static final int TOOL_INFO = 3;
    private static final int TOOL_PERMISSIONS = 4;

    private ActivitySettingsBinding binding;
    private ToolsListAdapter toolsAdapter;
    private SettingsRepository settingsRepository;
    private MediaStoreRepository mediaStoreRepository;
    private FavoritesRepository favoritesRepository;
    private VirtualAlbumRepository virtualAlbumRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.settingsToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        settingsRepository = new SettingsRepository(this);
        mediaStoreRepository = new MediaStoreRepository(this);
        favoritesRepository = new FavoritesRepository(this);
        virtualAlbumRepository = new VirtualAlbumRepository(this);

        toolsAdapter = new ToolsListAdapter();
        binding.toolsList.setAdapter(toolsAdapter);
        // One listener for the whole list. "position" says which row was tapped.
        binding.toolsList.setOnItemClickListener((parent, view, position, id) ->
                onToolClicked(toolsAdapter.getItem(position)));
        refreshTools();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mediaStoreRepository.close();
    }

    private void refreshTools() {
        List<ToolsListAdapter.ToolItem> tools = new ArrayList<>();
        tools.add(new ToolsListAdapter.ToolItem(TOOL_SORT, R.drawable.ic_sort,
                getString(R.string.tool_sort_title),
                getString(settingsRepository.getSortOrder().getLabelRes())));
        tools.add(new ToolsListAdapter.ToolItem(TOOL_REFRESH, R.drawable.ic_refresh,
                getString(R.string.tool_refresh_title), getString(R.string.tool_refresh_subtitle)));
        tools.add(new ToolsListAdapter.ToolItem(TOOL_INFO, R.drawable.ic_info,
                getString(R.string.tool_info_title), getString(R.string.tool_info_subtitle)));
        tools.add(new ToolsListAdapter.ToolItem(TOOL_PERMISSIONS, R.drawable.ic_lock,
                getString(R.string.tool_permissions_title), getString(R.string.tool_permissions_subtitle)));
        toolsAdapter.setItems(tools);
    }

    private void onToolClicked(@NonNull ToolsListAdapter.ToolItem tool) {
        switch (tool.id) {
            case TOOL_SORT:
                SortDialog.show(this, settingsRepository, this::refreshTools);
                break;
            case TOOL_REFRESH:
                settingsRepository.requestRefresh();
                Toast.makeText(this, R.string.refreshing, Toast.LENGTH_SHORT).show();
                break;
            case TOOL_INFO:
                showGalleryInfo();
                break;
            case TOOL_PERMISSIONS:
                openAppSettings();
                break;
            default:
                break;
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void showGalleryInfo() {
        if (PermissionHelper.getAccess(this) == PermissionHelper.Access.NONE) {
            Toast.makeText(this, R.string.info_no_access, Toast.LENGTH_LONG).show();
            return;
        }
        mediaStoreRepository.loadAllMedia(new MediaStoreRepository.LoadCallback() {
            @Override
            public void onLoaded(@NonNull List<MediaItem> items) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                showInfoDialog(items);
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isFinishing() && !isDestroyed()) {
                    Toast.makeText(SettingsActivity.this, R.string.error_body, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showInfoDialog(@NonNull List<MediaItem> items) {
        Set<String> favouriteUris = favoritesRepository.getAllUriStrings();
        int photos = 0;
        int videos = 0;
        int favourites = 0;
        long totalBytes = 0;
        for (MediaItem item : items) {
            if (item.isVideo()) {
                videos++;
            } else {
                photos++;
            }
            if (favouriteUris.contains(item.getUri().toString())) {
                favourites++;
            }
            totalBytes += item.getSizeBytes();
        }
        int albums = AlbumRepository.groupIntoAlbums(items).size()
                + virtualAlbumRepository.getAlbumNames().size();

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.info_title)
                .setMessage(getString(R.string.info_body, photos, videos, albums, favourites,
                        Formatter.formatFileSize(this, totalBytes)))
                .setPositiveButton(R.string.action_close, null)
                .show();
    }
}
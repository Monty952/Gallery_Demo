package com.contacts.app.phone.calls.gallerydemo.model;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.util.MediaUtils;

public enum MediaFilter {

    ALL(R.id.filter_all, R.string.filter_all),
    PHOTOS(R.id.filter_photos, R.string.filter_photos),
    VIDEOS(R.id.filter_videos, R.string.filter_videos),
    SCREENSHOTS(R.id.filter_screens, R.string.filter_screens),
    FAVOURITES(R.id.filter_favourites, R.string.filter_favourites);

    @IdRes
    private final int menuItemId;
    @StringRes
    private final int labelRes;

    MediaFilter(@IdRes int menuItemId, @StringRes int labelRes) {
        this.menuItemId = menuItemId;
        this.labelRes = labelRes;
    }

    @IdRes
    public int getMenuItemId() {
        return menuItemId;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    public boolean matches(@NonNull MediaItem item) {
        switch (this) {
            case PHOTOS:
                return !item.isVideo();
            case VIDEOS:
                return item.isVideo();
            case SCREENSHOTS:
                return MediaUtils.isScreenCapture(item);
            case FAVOURITES:
                return item.isFavourite();
            case ALL:
            default:
                return true;
        }
    }

    // Finds which filter a tapped menu item stands for.
    // (if-statements on purpose: "switch" does not work with R.id values in new Android Gradle Plugin versions)
    @NonNull
    public static MediaFilter fromMenuItemId(int id, @NonNull MediaFilter fallback) {
        for (MediaFilter filter : values()) {
            if (filter.menuItemId == id) {
                return filter;
            }
        }
        return fallback;
    }

    // Used to restore the selected filter after the screen rotates.
    @NonNull
    public static MediaFilter fromName(String name, @NonNull MediaFilter fallback) {
        for (MediaFilter filter : values()) {
            if (filter.name().equals(name)) {
                return filter;
            }
        }
        return fallback;
    }
}

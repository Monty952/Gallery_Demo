package com.contacts.app.phone.calls.gallerydemo.model;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.contacts.app.phone.calls.gallerydemo.R;

import java.util.Comparator;

// The three ways the user can sort. Each knows how to compare media and albums.
public enum SortOrder {

    DATE_ADDED(R.string.sort_date),
    NAME(R.string.sort_name),
    SIZE(R.string.sort_size);

    @StringRes
    private final int labelRes;

    SortOrder(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    @NonNull
    public Comparator<MediaItem> mediaComparator() {
        switch (this) {
            case NAME:
                return (a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
            case SIZE:
                return Comparator.comparingLong(MediaItem::getSizeBytes).reversed();
            case DATE_ADDED:
            default:
                return Comparator.comparingLong(MediaItem::getDateAddedSeconds).reversed();
        }
    }

    // For albums, "Size" means the number of items in the album
    @NonNull
    public Comparator<Album> albumComparator() {
        switch (this) {
            case NAME:
                return (a, b) -> a.getName().compareToIgnoreCase(b.getName());
            case SIZE:
                return Comparator.comparingInt(Album::getItemCount).reversed();
            case DATE_ADDED:
            default:
                return Comparator.comparingLong(Album::getLatestDateSeconds).reversed();
        }
    }

    @NonNull
    public static SortOrder fromName(String name, @NonNull SortOrder fallback) {
        for (SortOrder order : values()) {
            if (order.name().equals(name)) {
                return order;
            }
        }
        return fallback;
    }
}
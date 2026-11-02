package com.contacts.app.phone.calls.gallerydemo.model;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.contacts.app.phone.calls.gallerydemo.R;

import java.util.Locale;

public class Album {

    private final long bucketId;
    private final String name;
    @Nullable
    private final MediaItem cover;
    private final int itemCount;
    private final boolean virtual;

    private Album(long bucketId, @NonNull String name, @Nullable MediaItem cover,
                  int itemCount, boolean virtual) {
        this.bucketId = bucketId;
        this.name = name;
        this.cover = cover;
        this.itemCount = itemCount;
        this.virtual = virtual;
    }

    // An album built from a real folder in shared storage
    @NonNull
    public static Album real(long bucketId, @NonNull String name, @NonNull MediaItem cover, int itemCount) {
        return new Album(bucketId, name, cover, itemCount, false);
    }

    // NEW (Stage 7): an album kept by this app (may be empty, so the cover can be null)
    @NonNull
    public static Album virtual(@NonNull String name, @Nullable MediaItem cover, int itemCount) {
        return new Album(0L, name, cover, itemCount, true);
    }

    public long getBucketId() { return bucketId; }

    @NonNull
    public String getName() { return name; }

    @Nullable
    public MediaItem getCover() { return cover; }

    public int getItemCount() { return itemCount; }

    public boolean isVirtual() { return virtual; }

    public long getLatestDateSeconds() {
        return cover != null ? cover.getDateAddedSeconds() : 0L;
    }

    // Unique key used by DiffUtil. Real and virtual albums can never clash.
    @NonNull
    public String getKey() {
        return virtual ? "v:" + name.toLowerCase(Locale.ROOT) : "b:" + bucketId;
    }

    @NonNull
    public String getDisplayName(@NonNull Context context) {
        return name.isEmpty() ? context.getString(R.string.album_unknown) : name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Album)) return false;
        return getKey().equals(((Album) other).getKey());
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }
}
package com.contacts.app.phone.calls.gallerydemo.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MediaItem {
    public enum Type {IMAGE, VIDEO}

    private final long id;
    private final Uri uri;
    private final String displayName;
    private final String mimeType;
    private final Type type;
    private final long dateAddedSeconds;
    private final long durationMs;
    private final long sizeBytes;
    private final long bucketId;
    private final String bucketName;
    private boolean favourite;


    public MediaItem(long id,
                     @NonNull Uri uri,
                     @Nullable String displayName,
                     @Nullable String mimeType,
                     @NonNull Type type,
                     long dateAddedSeconds,
                     long durationMs,
                     long sizeBytes,
                     long bucketId,
                     @Nullable String bucketName) {
        this.id = id;
        this.uri = uri;
        this.displayName = displayName != null ? displayName : String.valueOf(id);
        this.mimeType = mimeType != null ? mimeType : "";
        this.type = type;
        this.dateAddedSeconds = dateAddedSeconds;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.bucketId = bucketId;
        this.bucketName = bucketName != null ? bucketName : "";
    }

    public long getId() { return id; }
    @NonNull
    public Uri getUri() { return uri; }
    @NonNull
    public String getDisplayName() { return displayName; }
    @NonNull
    public String getMimeType() { return mimeType; }
    @NonNull
    public Type getType() { return type; }
    public boolean isVideo() { return type == Type.VIDEO; }
    public long getDateAddedSeconds() { return dateAddedSeconds; }
    public long getDurationMs() { return durationMs; }
    public long getSizeBytes() { return sizeBytes; }
    public long getBucketId() { return bucketId; }
    @NonNull
    public String getBucketName() { return bucketName; }
    public boolean isFavourite() { return favourite; }
    public void setFavourite(boolean favourite) { this.favourite = favourite; }

    //  (Stage 5): returns a COPY with a different favourite value.
    // We copy instead of changing the original so DiffUtil can see that the
    // old object and the new object are different, and redraw the heart.
    @NonNull
    public MediaItem copyWithFavourite(boolean favourite) {
        MediaItem copy = new MediaItem(id, uri, displayName, mimeType, type,
                dateAddedSeconds, durationMs, sizeBytes, bucketId, bucketName);
        copy.favourite = favourite;
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MediaItem)) return false;
        return uri.equals(((MediaItem) other).uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }



}

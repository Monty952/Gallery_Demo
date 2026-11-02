package com.contacts.app.phone.calls.gallerydemo.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;

import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaStoreRepository {

    public interface LoadCallback {
        void onLoaded(@NonNull List<MediaItem> items);

        void onError(@NonNull Exception error);
    }

    private static final String TAG = "MediaStoreRepository";

    private static final String[] IMAGE_PROJECTION = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    };

    private static final String[] VIDEO_PROJECTION = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
    };

    private final ContentResolver contentResolver;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MediaStoreRepository(@NonNull Context context) {
        this.contentResolver = context.getApplicationContext().getContentResolver();
    }

    public void loadAllMedia(@NonNull LoadCallback callback) {
        executor.execute(() -> {
            try {
                List<MediaItem> items = queryAllMedia();
                mainHandler.post(() -> callback.onLoaded(items));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load media", e);
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    public void close() {
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private List<MediaItem> queryAllMedia() {
        List<MediaItem> items = new ArrayList<>();
        items.addAll(queryCollection(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, IMAGE_PROJECTION, MediaItem.Type.IMAGE));
        items.addAll(queryCollection(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, VIDEO_PROJECTION, MediaItem.Type.VIDEO));
        items.sort(Comparator.comparingLong(MediaItem::getDateAddedSeconds).reversed());
        return items;
    }

    private List<MediaItem> queryCollection(Uri collection, String[] projection, MediaItem.Type type) {
        List<MediaItem> result = new ArrayList<>();
        String sortOrder = MediaStore.MediaColumns.DATE_ADDED + " DESC";

        try (Cursor cursor = contentResolver.query(collection, projection, null, null, sortOrder)) {
            if (cursor == null) {
                throw new IllegalStateException("MediaStore returned no cursor for " + collection);
            }

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            int bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);
            int bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
            int durationColumn = type == MediaItem.Type.VIDEO
                    ? cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    : -1;

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                result.add(new MediaItem(
                        id,
                        ContentUris.withAppendedId(collection, id),
                        cursor.getString(nameColumn),
                        cursor.getString(mimeColumn),
                        type,
                        cursor.getLong(dateColumn),
                        durationColumn >= 0 ? cursor.getLong(durationColumn) : 0L,
                        cursor.getLong(sizeColumn),
                        cursor.getLong(bucketIdColumn),
                        cursor.getString(bucketNameColumn)));
            }
        }
        return result;
    }
}

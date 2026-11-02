package com.contacts.app.phone.calls.gallerydemo.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//import java.util.logging.Handler;

public class ThumbnailLoader {

    private static final String TAG = "ThumbnailLoader";
    private static final int THUMBNAIL_SIZE_PX = 320;
    private static final int THREAD_COUNT = 3;

    private final ContentResolver contentResolver;
    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    public ThumbnailLoader(@NonNull Context context) {
        contentResolver = context.getApplicationContext().getContentResolver();

        int cacheSizeKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 16);
        cache = new LruCache<String, Bitmap>(cacheSizeKb) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public void load(@NonNull MediaItem item, @NonNull ImageView target) {
        String key = item.getUri().toString();
        target.setTag(key);

        Bitmap cached = cache.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }

        target.setImageDrawable(null);
        if (executor.isShutdown()) {
            return;
        }

        WeakReference<ImageView> targetRef = new WeakReference<>(target);
        executor.execute(() -> {
            if (!isStillWanted(targetRef, key)) {
                return;
            }
            Bitmap bitmap = createThumbnail(item);
            if (bitmap == null) {
                return;
            }
            cache.put(key, bitmap);
            mainHandler.post(() -> {
                ImageView view = targetRef.get();
                if (view != null && key.equals(view.getTag())) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    public void close() {
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        cache.evictAll();
    }

    private boolean isStillWanted(WeakReference<ImageView> targetRef, String key) {
        ImageView view = targetRef.get();
        return view != null && key.equals(view.getTag());
    }

    @Nullable
    private Bitmap createThumbnail(MediaItem item) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return contentResolver.loadThumbnail(
                        item.getUri(), new Size(THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX), null);
            }
            return createLegacyThumbnail(item);
        } catch (Exception e) {
            Log.w(TAG, "Could not create thumbnail for " + item.getUri(), e);
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private Bitmap createLegacyThumbnail(MediaItem item) {
        if (item.isVideo()) {
            return MediaStore.Video.Thumbnails.getThumbnail(
                    contentResolver, item.getId(),
                    MediaStore.Video.Thumbnails.MINI_KIND, null);
        }
        return MediaStore.Images.Thumbnails.getThumbnail(
                contentResolver, item.getId(),
                MediaStore.Images.Thumbnails.MINI_KIND, null);
    }
}
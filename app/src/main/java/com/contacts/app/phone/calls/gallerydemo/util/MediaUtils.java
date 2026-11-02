package com.contacts.app.phone.calls.gallerydemo.util;

import androidx.annotation.NonNull;

import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MediaUtils {

    //  words that suggest a screenshot / screen recording
    private static final String[] SCREEN_CAPTURE_FOLDER_HINTS = {
            "screenshot", "screen recording", "screenrecord", "screen_record"
    };
    private static final String[] SCREEN_CAPTURE_NAME_PREFIXES = {
            "screenshot", "screen_recording", "screen recording", "screen-recording", "screenrecord"
    };

    private MediaUtils() {
    }

    @NonNull
    public static String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
    // NEW (Stage 5): true if the folder name OR the file name looks like a screen capture

    public static boolean isScreenCapture(@NonNull MediaItem item) {
        String folder = item.getBucketName().toLowerCase(Locale.ROOT);
        for (String hint : SCREEN_CAPTURE_FOLDER_HINTS) {
            if (folder.contains(hint)) {
                return true;
            }
        }

        String name = item.getDisplayName().toLowerCase(Locale.ROOT);
        for (String prefix : SCREEN_CAPTURE_NAME_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    // NEW (Stage 9): makes the list match the saved favourites.
    // Items that differ are REPLACED by copies so that DiffUtil notices the change.
    // Returns true if anything changed.

    public static boolean applyFavourites(@NonNull List<MediaItem> items, @NonNull Set<String> favouriteUris) {
        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            MediaItem item = items.get(i);
            boolean favourite = favouriteUris.contains(item.getUri().toString());
            if (favourite != item.isFavourite()) {
                items.set(i, item.copyWithFavourite(favourite));
                changed = true;
            }
        }
        return changed;
    }

}

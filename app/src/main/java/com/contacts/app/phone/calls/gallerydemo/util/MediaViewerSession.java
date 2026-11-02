package com.contacts.app.phone.calls.gallerydemo.util;

import androidx.annotation.NonNull;

import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Hands the list of media to the viewer. An Intent is too small for thousands of items.
// The viewer clears it when it closes.
public final class MediaViewerSession {

    private static List<MediaItem> items = Collections.emptyList();

    private MediaViewerSession() {
    }

    public static synchronized void setItems(@NonNull List<MediaItem> newItems) {
        items = new ArrayList<>(newItems);
    }

    @NonNull
    public static synchronized List<MediaItem> getItems() {
        return items;
    }

    public static synchronized void clear() {
        items = Collections.emptyList();
    }
}
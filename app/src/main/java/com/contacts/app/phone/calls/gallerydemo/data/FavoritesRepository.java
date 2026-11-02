package com.contacts.app.phone.calls.gallerydemo.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FavoritesRepository {

    private static final String KEY_FAVOURITE_URIS = "favourite_uris";

    private final SharedPreferences preferences;

    public FavoritesRepository(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFavourite(@NonNull Uri uri) {
        return readUris().contains(uri.toString());
    }

    public void setFavourite(@NonNull Uri uri, boolean favourite) {
        setFavourites(Collections.singletonList(uri), favourite);
    }

    // NEW (Stage 11): change many items with ONE save (selection mode)
    public void setFavourites(@NonNull Collection<Uri> uris, boolean favourite) {
        Set<String> updated = new HashSet<>(readUris());
        boolean changed = false;
        for (Uri uri : uris) {
            changed |= favourite ? updated.add(uri.toString()) : updated.remove(uri.toString());
        }
        if (changed) {
            preferences.edit().putStringSet(KEY_FAVOURITE_URIS, updated).apply();
        }
    }

    // NEW (Stage 9): a copy of all favourite URIs, so screens can re-check themselves after the viewer closes
    @NonNull
    public Set<String> getAllUriStrings() {
        return new HashSet<>(readUris());
    }

    @NonNull
    private Set<String> readUris() {
        return preferences.getStringSet(KEY_FAVOURITE_URIS, Collections.<String>emptySet());
    }
}
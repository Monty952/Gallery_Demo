package com.contacts.app.phone.calls.gallerydemo.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Albums owned by this app: a list of names, plus a set of media URIs per name.
// These are real, saved data (not just UI), but other gallery apps cannot see them.
public class VirtualAlbumRepository {

    private static final String KEY_ALBUM_NAMES = "virtual_album_names";
    private static final String KEY_ITEMS_PREFIX = "virtual_album_items_";

    private final SharedPreferences preferences;

    public VirtualAlbumRepository(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public List<String> getAlbumNames() {
        List<String> names = new ArrayList<>(readNames());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    // Returns false if the name is empty or already used (ignoring upper/lower case)
    public boolean createAlbum(@NonNull String rawName) {
        String name = rawName.trim();
        if (name.isEmpty() || findExistingName(name) != null) {
            return false;
        }
        Set<String> names = new HashSet<>(readNames());
        names.add(name);
        preferences.edit().putStringSet(KEY_ALBUM_NAMES, names).apply();
        return true;
    }

    @NonNull
    public Set<String> getItemUris(@NonNull String albumName) {
        return new HashSet<>(preferences.getStringSet(itemsKey(albumName), Collections.<String>emptySet()));
    }

    // Returns how many items were newly added (items already inside are skipped)
    public int addItems(@NonNull String albumName, @NonNull Collection<Uri> uris) {
        Set<String> stored = getItemUris(albumName);
        int added = 0;
        for (Uri uri : uris) {
            if (stored.add(uri.toString())) {
                added++;
            }
        }
        if (added > 0) {
            preferences.edit().putStringSet(itemsKey(albumName), stored).apply();
        }
        return added;
    }

    @Nullable
    private String findExistingName(@NonNull String name) {
        for (String existing : readNames()) {
            if (existing.equalsIgnoreCase(name)) {
                return existing;
            }
        }
        return null;
    }

    @NonNull
    private Set<String> readNames() {
        return preferences.getStringSet(KEY_ALBUM_NAMES, Collections.<String>emptySet());
    }

    @NonNull
    private static String itemsKey(@NonNull String albumName) {
        return KEY_ITEMS_PREFIX + albumName;
    }
}
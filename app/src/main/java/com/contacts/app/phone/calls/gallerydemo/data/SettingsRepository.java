package com.contacts.app.phone.calls.gallerydemo.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.contacts.app.phone.calls.gallerydemo.model.SortOrder;

// Stores the sort order and a "refresh token". Screens listen for changes to these two keys.
public class SettingsRepository {

    public static final String PREFS_NAME = "gallery_demo_prefs";
    public static final String KEY_SORT_ORDER = "sort_order";
    public static final String KEY_REFRESH_TOKEN = "refresh_token";

    private final SharedPreferences preferences;

    public SettingsRepository(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public SortOrder getSortOrder() {
        return SortOrder.fromName(preferences.getString(KEY_SORT_ORDER, null), SortOrder.DATE_ADDED);
    }

    public void setSortOrder(@NonNull SortOrder order) {
        preferences.edit().putString(KEY_SORT_ORDER, order.name()).apply();
    }

    // Writing a new value makes every listening screen reload its data
    public void requestRefresh() {
        preferences.edit().putLong(KEY_REFRESH_TOKEN, System.nanoTime()).apply();
    }

    // SharedPreferences keeps listeners in a weak map, so the caller must hold the listener in a field
    public void registerListener(@NonNull SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(@NonNull SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
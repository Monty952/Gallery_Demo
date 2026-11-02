package com.contacts.app.phone.calls.gallerydemo.fragment;

import androidx.annotation.NonNull;

// Implemented by the screens that react to the toolbar. MainActivity talks to
// whichever screen is currently visible through this interface.
public interface ScreenActions {

    void onSearchQueryChanged(@NonNull String query);

    boolean supportsSelection();

    void startSelectionMode();
}

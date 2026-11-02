package com.contacts.app.phone.calls.gallerydemo.dialog;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.data.VirtualAlbumRepository;
import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

// A simple "pick an album" list dialog. No DialogFragment is needed because there is no typed input to keep.
public final class AddToAlbumDialog {

    private AddToAlbumDialog() {
    }

    public static void show(@NonNull Context context,
                            @NonNull VirtualAlbumRepository repository,
                            @NonNull List<MediaItem> items,
                            @Nullable Runnable onAdded) {
        List<String> albumNames = repository.getAlbumNames();
        if (albumNames.isEmpty()) {
            Toast.makeText(context, R.string.no_virtual_albums, Toast.LENGTH_LONG).show();
            return;
        }

        CharSequence[] names = albumNames.toArray(new CharSequence[0]);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.add_to_album_title)
                .setItems(names, (dialog, which) -> {
                    String albumName = albumNames.get(which);
                    List<Uri> uris = new ArrayList<>();
                    for (MediaItem item : items) {
                        uris.add(item.getUri());
                    }
                    int added = repository.addItems(albumName, uris);
                    String message = added > 0
                            ? context.getString(R.string.added_to_album, added, albumName)
                            : context.getString(R.string.already_in_album, albumName);
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                    if (added > 0 && onAdded != null) {
                        onAdded.run();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
package com.contacts.app.phone.calls.gallerydemo.dialog;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.data.SettingsRepository;
import com.contacts.app.phone.calls.gallerydemo.model.SortOrder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class SortDialog {

    private SortDialog() {
    }

    // Pass an Activity as "context" (dialogs need a themed context)
    public static void show(@NonNull Context context,
                            @NonNull SettingsRepository settings,
                            @Nullable Runnable onChanged) {
        SortOrder[] orders = SortOrder.values();
        CharSequence[] labels = new CharSequence[orders.length];
        int checked = 0;
        SortOrder current = settings.getSortOrder();
        for (int i = 0; i < orders.length; i++) {
            labels[i] = context.getString(orders[i].getLabelRes());
            if (orders[i] == current) {
                checked = i;
            }
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.sort_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    settings.setSortOrder(orders[which]); // every screen listens and re-sorts itself
                    dialog.dismiss();
                    if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
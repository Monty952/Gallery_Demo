package com.contacts.app.phone.calls.gallerydemo.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.databinding.DialogCreateAlbumBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class CreateAlbumDialogFragment extends DialogFragment {

    public static final String REQUEST_KEY = "create_album_request";
    public static final String RESULT_NAME = "album_name";

    private static final String ARG_EXISTING_NAMES = "existing_names";
    private static final int MAX_NAME_LENGTH = 40;

    @Nullable
    private DialogCreateAlbumBinding binding;
    private final List<String> existingNames = new ArrayList<>();

    // Data goes in arguments so Android can restore it after rotation
    @NonNull
    public static CreateAlbumDialogFragment newInstance(@NonNull ArrayList<String> existingNames) {
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_EXISTING_NAMES, existingNames);
        CreateAlbumDialogFragment fragment = new CreateAlbumDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        ArrayList<String> names = requireArguments().getStringArrayList(ARG_EXISTING_NAMES);
        existingNames.clear();
        if (names != null) {
            existingNames.addAll(names);
        }

        DialogCreateAlbumBinding dialogBinding =
                DialogCreateAlbumBinding.inflate(LayoutInflater.from(requireContext()));
        binding = dialogBinding;

        // Clear the red error as soon as the user edits the text
        dialogBinding.albumNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                dialogBinding.albumNameLayout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        dialogBinding.albumNameInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onSaveClicked();
                return true;
            }
            return false;
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.create_album_title)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.action_cancel, null) // null = just close
                .setPositiveButton(R.string.action_save, null)   // real listener set in onStart()
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        return dialog;
    }

    // A listener passed to setPositiveButton always closes the dialog.
    // Setting it here lets us keep the dialog open when the name is invalid.
    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> onSaveClicked());
        }
        if (binding != null) {
            binding.albumNameInput.requestFocus();
        }
    }

    private void onSaveClicked() {
        if (binding == null) {
            return;
        }
        CharSequence text = binding.albumNameInput.getText();
        String name = text == null ? "" : text.toString().trim();

        int errorRes = validate(name);
        if (errorRes != 0) {
            binding.albumNameLayout.setError(getString(errorRes));
            return;
        }

        Bundle result = new Bundle();
        result.putString(RESULT_NAME, name);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismiss();
    }

    // Returns an error string resource, or 0 if the name is fine
    private int validate(@NonNull String name) {
        if (name.isEmpty()) {
            return R.string.error_name_empty;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return R.string.error_name_long;
        }
        for (String existing : existingNames) {
            if (existing.equalsIgnoreCase(name)) {
                return R.string.error_name_duplicate;
            }
        }
        return 0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
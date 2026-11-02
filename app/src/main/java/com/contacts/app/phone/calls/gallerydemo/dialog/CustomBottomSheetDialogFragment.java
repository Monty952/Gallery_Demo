package com.contacts.app.phone.calls.gallerydemo.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.contacts.app.phone.calls.gallerydemo.databinding.BottomSheetAlbumsBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class CustomBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "albums_bottom_sheet";
    public static final String RESULT_OPTION = "option";

    public static final String OPTION_MORE_ALBUMS = "more_albums";
    public static final String OPTION_FEATURED = "featured";
    public static final String OPTION_MEDIA_TYPE = "media_type";
    public static final String OPTION_MORE = "more";

    @Nullable
    private BottomSheetAlbumsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAlbumsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (binding == null) {
            return;
        }
        binding.optionMoreAlbums.setOnClickListener(v -> deliver(OPTION_MORE_ALBUMS));
        binding.optionFeatured.setOnClickListener(v -> deliver(OPTION_FEATURED));
        binding.optionMediaType.setOnClickListener(v -> deliver(OPTION_MEDIA_TYPE));
        binding.optionMore.setOnClickListener(v -> deliver(OPTION_MORE));
    }

    // Tell AlbumsFragment which tile was tapped, then close the sheet
    private void deliver(@NonNull String option) {
        Bundle result = new Bundle();
        result.putString(RESULT_OPTION, option);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
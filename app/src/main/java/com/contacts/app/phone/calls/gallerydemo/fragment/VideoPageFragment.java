package com.contacts.app.phone.calls.gallerydemo.fragment;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.databinding.FragmentVideoPageBinding;

// One swipe page showing one video.
// FragmentStateAdapter keeps only the visible page RESUMED, so:
//   onResume() = this page is on screen -> play
//   onPause()  = user swiped away       -> pause
public class VideoPageFragment extends Fragment {

    private static final String ARG_URI = "uri";

    @Nullable
    private FragmentVideoPageBinding binding;
    @Nullable
    private MediaController mediaController;
    private boolean prepared;

    @NonNull
    public static VideoPageFragment newInstance(@NonNull Uri uri) {
        Bundle args = new Bundle();
        args.putString(ARG_URI, uri.toString());
        VideoPageFragment fragment = new VideoPageFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public VideoPageFragment() {
        super(R.layout.fragment_video_page);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentVideoPageBinding.bind(view);

        mediaController = new MediaController(requireContext());
        binding.videoView.setMediaController(mediaController); // play/pause bar and seek bar

        binding.videoView.setOnPreparedListener(player -> {
            prepared = true;
            if (isResumed() && binding != null) {
                binding.videoView.start();
            }
        });
        binding.videoView.setOnErrorListener((player, what, extra) -> {
            if (binding != null) {
                binding.pageError.setVisibility(View.VISIBLE);
            }
            return true;
        });
        binding.videoView.setVideoURI(Uri.parse(requireArguments().getString(ARG_URI, "")));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && prepared) {
            binding.videoView.start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mediaController != null) {
            mediaController.hide();
        }
        if (binding != null && binding.videoView.isPlaying()) {
            binding.videoView.pause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.videoView.stopPlayback(); // releases the MediaPlayer
        }
        mediaController = null;
        prepared = false;
        binding = null;
    }
}
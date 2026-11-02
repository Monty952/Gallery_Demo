package com.contacts.app.phone.calls.gallerydemo.fragment;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.databinding.FragmentImagePageBinding;
import com.contacts.app.phone.calls.gallerydemo.util.FullImageLoader;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// One swipe page showing one zoomable picture
public class ImagePageFragment extends Fragment {

    private static final String TAG = "ImagePageFragment";
    private static final String ARG_URI = "uri";

    @Nullable
    private FragmentImagePageBinding binding;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @NonNull
    public static ImagePageFragment newInstance(@NonNull Uri uri) {
        Bundle args = new Bundle();
        args.putString(ARG_URI, uri.toString());
        ImagePageFragment fragment = new ImagePageFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public ImagePageFragment() {
        super(R.layout.fragment_image_page);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentImagePageBinding.bind(view);
        loadImage();
    }

    private void loadImage() {
        if (binding == null) {
            return;
        }
        Uri uri = Uri.parse(requireArguments().getString(ARG_URI, ""));
        ContentResolver resolver = requireContext().getApplicationContext().getContentResolver();
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int maxDimension = Math.max(metrics.widthPixels, metrics.heightPixels);

        binding.pageProgress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = FullImageLoader.decode(resolver, uri, maxDimension);
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Could not load " + uri, e);
            }
            Bitmap result = bitmap;
            mainHandler.post(() -> {
                if (binding == null) {
                    return;
                }
                binding.pageProgress.setVisibility(View.GONE);
                if (result != null) {
                    binding.zoomImage.setImageBitmap(result);
                } else {
                    binding.pageError.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.zoomImage.setImageDrawable(null); // let the bitmap be freed
        }
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
package com.contacts.app.phone.calls.gallerydemo.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.adapter.MediaPagerAdapter;
import com.contacts.app.phone.calls.gallerydemo.data.FavoritesRepository;
import com.contacts.app.phone.calls.gallerydemo.databinding.ActivityMediaViewerBinding;
import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;
import com.contacts.app.phone.calls.gallerydemo.util.MediaViewerSession;

import java.util.List;

// Full-screen viewer: swipe left/right between photos and videos (ViewPager2).
public class MediaViewerActivity extends AppCompatActivity {

    private static final String EXTRA_POSITION = "extra_position";

    private ActivityMediaViewerBinding binding;
    private FavoritesRepository favoritesRepository;
    private List<MediaItem> items;

    // OnPageChangeCallback tells us whenever the visible page changes
    private final ViewPager2.OnPageChangeCallback pageChangeCallback =
            new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    updateTopBar(position);
                }
            };

    // The list can be thousands of items long, so it is handed over through MediaViewerSession
    public static void start(@NonNull Context context, @NonNull List<MediaItem> items, int position) {
        MediaViewerSession.setItems(items);
        Intent intent = new Intent(context, MediaViewerActivity.class);
        intent.putExtra(EXTRA_POSITION, position);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        items = MediaViewerSession.getItems();
        if (items.isEmpty()) {
            // The app was restarted by Android and the list is gone: just close
            finish();
            return;
        }

        binding = ActivityMediaViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        favoritesRepository = new FavoritesRepository(this);

        // The background is black, so the status/navigation bar icons must be light
        Window window = getWindow();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.viewerTopBar.setPadding(bars.left, bars.top, bars.right, 0);
            return WindowInsetsCompat.CONSUMED;
        });

        binding.viewPager.setAdapter(new MediaPagerAdapter(this, items));
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback);

        if (savedInstanceState == null) {
            int start = Math.max(0, Math.min(
                    getIntent().getIntExtra(EXTRA_POSITION, 0), items.size() - 1));
            binding.viewPager.setCurrentItem(start, false); // false = no scroll animation
        }
        updateTopBar(binding.viewPager.getCurrentItem());

        binding.viewerBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.viewerFavourite.setOnClickListener(v -> toggleFavourite());
    }

    private void updateTopBar(int position) {
        if (binding == null || position < 0 || position >= items.size()) {
            return;
        }
        MediaItem item = items.get(position);
        binding.viewerTitle.setText(item.getDisplayName());
        binding.viewerPosition.setText(getString(R.string.viewer_position, position + 1, items.size()));
        updateFavouriteIcon(item);
    }

    private void updateFavouriteIcon(@NonNull MediaItem item) {
        boolean favourite = favoritesRepository.isFavourite(item.getUri());
        binding.viewerFavourite.setImageResource(
                favourite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
    }

    private void toggleFavourite() {
        int position = binding.viewPager.getCurrentItem();
        if (position < 0 || position >= items.size()) {
            return;
        }
        MediaItem item = items.get(position);
        // Saved in SharedPreferences; the grids re-read it when this screen closes
        favoritesRepository.setFavourite(item.getUri(), !favoritesRepository.isFavourite(item.getUri()));
        updateFavouriteIcon(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (binding != null) {
            binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        if (isFinishing()) {
            MediaViewerSession.clear();
        }
    }
}
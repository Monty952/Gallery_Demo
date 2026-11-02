package com.contacts.app.phone.calls.gallerydemo.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.contacts.app.phone.calls.gallerydemo.fragment.ImagePageFragment;
import com.contacts.app.phone.calls.gallerydemo.fragment.VideoPageFragment;
import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;

import java.util.List;

// Each swipe page is a Fragment. FragmentStateAdapter creates pages on demand
// and destroys the ones that are far away, which frees their memory.
public class MediaPagerAdapter extends FragmentStateAdapter {

    private final List<MediaItem> items;

    public MediaPagerAdapter(@NonNull FragmentActivity activity, @NonNull List<MediaItem> items) {
        super(activity);
        this.items = items;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        MediaItem item = items.get(position);
        return item.isVideo()
                ? VideoPageFragment.newInstance(item.getUri())
                : ImagePageFragment.newInstance(item.getUri());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
package com.contacts.app.phone.calls.gallerydemo.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.contacts.app.phone.calls.gallerydemo.databinding.ItemToolBinding;

import java.util.ArrayList;
import java.util.List;

public class ToolsListAdapter extends BaseAdapter {

    public static class ToolItem {
        public final int id;
        @DrawableRes
        public final int iconRes;
        public final String title;
        public final String subtitle;

        public ToolItem(int id, @DrawableRes int iconRes, @NonNull String title, @NonNull String subtitle) {
            this.id = id;
            this.iconRes = iconRes;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private final List<ToolItem> items = new ArrayList<>();

    public void setItems(@NonNull List<ToolItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    // ListView asks these three questions about the data
    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public ToolItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    // ListView calls this for every visible row. convertView is an old row it wants us to REUSE.
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ItemToolBinding binding;
        if (convertView == null) {
            binding = ItemToolBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            binding.getRoot().setTag(binding); // keep the binding so we don't look up views again
        } else {
            binding = (ItemToolBinding) convertView.getTag();
        }

        ToolItem item = getItem(position);
        binding.toolIcon.setImageResource(item.iconRes);
        binding.toolTitle.setText(item.title);
        binding.toolSubtitle.setText(item.subtitle);
        return binding.getRoot();
    }
}
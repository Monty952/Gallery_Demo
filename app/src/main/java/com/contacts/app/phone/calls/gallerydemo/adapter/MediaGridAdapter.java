package com.contacts.app.phone.calls.gallerydemo.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.contacts.app.phone.calls.gallerydemo.databinding.ItemMediaGridBinding;
import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;
import com.contacts.app.phone.calls.gallerydemo.util.MediaUtils;
import com.contacts.app.phone.calls.gallerydemo.util.ThumbnailLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MediaGridAdapter extends ListAdapter<MediaItem, MediaGridAdapter.MediaViewHolder> {

    public interface OnMediaClickListener {
        void onMediaClick(@NonNull MediaItem item, int position);
    }

    public interface OnMediaLongClickListener {
        void onMediaLongClick(@NonNull MediaItem item, @NonNull View anchor);
    }

    // NEW (Stage 11)
    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount);
    }

    private static final Object PAYLOAD_SELECTION = new Object();

    private static final DiffUtil.ItemCallback<MediaItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MediaItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull MediaItem oldItem, @NonNull MediaItem newItem) {
                    return oldItem.getUri().equals(newItem.getUri());
                }

                @Override
                public boolean areContentsTheSame(@NonNull MediaItem oldItem, @NonNull MediaItem newItem) {
                    return oldItem.getDisplayName().equals(newItem.getDisplayName())
                            && oldItem.getDateAddedSeconds() == newItem.getDateAddedSeconds()
                            && oldItem.isFavourite() == newItem.isFavourite();
                }
            };

    private final ThumbnailLoader thumbnailLoader;
    @Nullable
    private OnMediaClickListener clickListener;
    @Nullable
    private OnMediaLongClickListener longClickListener;
    @Nullable
    private OnSelectionChangedListener selectionListener;

    // NEW (Stage 11): selection state
    private boolean selectionMode;
    private final Set<Uri> selectedUris = new HashSet<>();

    public MediaGridAdapter(@NonNull ThumbnailLoader thumbnailLoader) {
        super(DIFF_CALLBACK);
        this.thumbnailLoader = thumbnailLoader;
    }

    public void setOnMediaClickListener(@Nullable OnMediaClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnMediaLongClickListener(@Nullable OnMediaLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnSelectionChangedListener(@Nullable OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    // ===== NEW (Stage 11): selection mode =====

    public void setSelectionMode(boolean enabled) {
        if (selectionMode == enabled) {
            return;
        }
        selectionMode = enabled;
        selectedUris.clear();
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        notifySelectionChanged();
    }

    public void selectAll() {
        for (MediaItem item : getCurrentList()) {
            selectedUris.add(item.getUri());
        }
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        notifySelectionChanged();
    }

    @NonNull
    public List<MediaItem> getSelectedItems() {
        List<MediaItem> selected = new ArrayList<>();
        for (MediaItem item : getCurrentList()) {
            if (selectedUris.contains(item.getUri())) {
                selected.add(item);
            }
        }
        return selected;
    }

    private void toggleSelection(@NonNull MediaItem item, int position) {
        if (!selectedUris.remove(item.getUri())) {
            selectedUris.add(item.getUri());
        }
        // The payload makes RecyclerView update only the check mark (no flicker)
        notifyItemChanged(position, PAYLOAD_SELECTION);
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(getSelectedItems().size());
        }
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMediaGridBinding binding = ItemMediaGridBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MediaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    // NEW (Stage 11): if only the selection changed, update just the check mark
    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelection(getItem(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    @Override
    public void onViewRecycled(@NonNull MediaViewHolder holder) {
        super.onViewRecycled(holder);
        holder.clear();
    }

    class MediaViewHolder extends RecyclerView.ViewHolder {

        private final ItemMediaGridBinding binding;

        MediaViewHolder(@NonNull ItemMediaGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.getRoot().setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                // CHANGED (Stage 11): in selection mode a tap selects instead of opening
                if (selectionMode) {
                    toggleSelection(getItem(position), position);
                } else if (clickListener != null) {
                    clickListener.onMediaClick(getItem(position), position);
                }
            });

            binding.getRoot().setOnLongClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return false;
                }
                if (selectionMode) {
                    toggleSelection(getItem(position), position);
                    return true;
                }
                if (longClickListener == null) {
                    return false;
                }
                longClickListener.onMediaLongClick(getItem(position), view);
                return true;
            });
        }

        void bind(@NonNull MediaItem item) {
            binding.thumbnail.setContentDescription(item.getDisplayName());
            thumbnailLoader.load(item, binding.thumbnail);

            if (item.isVideo()) {
                binding.videoBadge.setVisibility(View.VISIBLE);
                binding.videoDuration.setText(MediaUtils.formatDuration(item.getDurationMs()));
            } else {
                binding.videoBadge.setVisibility(View.GONE);
            }
            binding.favouriteBadge.setVisibility(item.isFavourite() ? View.VISIBLE : View.GONE);
            bindSelection(item);
        }

        void bindSelection(@NonNull MediaItem item) {
            boolean selected = selectionMode && selectedUris.contains(item.getUri());
            binding.selectionOverlay.setVisibility(selected ? View.VISIBLE : View.GONE);
        }

        void clear() {
            binding.thumbnail.setTag(null);
            binding.thumbnail.setImageDrawable(null);
        }
    }
}
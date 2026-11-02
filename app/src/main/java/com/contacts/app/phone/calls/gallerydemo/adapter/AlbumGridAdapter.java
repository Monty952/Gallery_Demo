package com.contacts.app.phone.calls.gallerydemo.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.contacts.app.phone.calls.gallerydemo.R;
import com.contacts.app.phone.calls.gallerydemo.databinding.ItemAlbumGridBinding;
import com.contacts.app.phone.calls.gallerydemo.model.Album;
import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;
import com.contacts.app.phone.calls.gallerydemo.util.ThumbnailLoader;

import java.util.Objects;

public class AlbumGridAdapter extends ListAdapter<Album, AlbumGridAdapter.AlbumViewHolder> {

    public interface OnAlbumClickListener {
        void onAlbumClick(@NonNull Album album);
    }

    private static final DiffUtil.ItemCallback<Album> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Album>() {
                @Override
                public boolean areItemsTheSame(@NonNull Album oldItem, @NonNull Album newItem) {
                    return oldItem.getKey().equals(newItem.getKey());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Album oldItem, @NonNull Album newItem) {
                    MediaItem oldCover = oldItem.getCover();
                    MediaItem newCover = newItem.getCover();
                    return oldItem.getName().equals(newItem.getName())
                            && oldItem.getItemCount() == newItem.getItemCount()
                            && Objects.equals(oldCover == null ? null : oldCover.getUri(),
                            newCover == null ? null : newCover.getUri());
                }
            };

    private final ThumbnailLoader thumbnailLoader;
    @Nullable
    private OnAlbumClickListener clickListener;

    public AlbumGridAdapter(@NonNull ThumbnailLoader thumbnailLoader) {
        super(DIFF_CALLBACK);
        this.thumbnailLoader = thumbnailLoader;
    }

    public void setOnAlbumClickListener(@Nullable OnAlbumClickListener listener) {
        this.clickListener = listener;
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAlbumGridBinding binding = ItemAlbumGridBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AlbumViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public void onViewRecycled(@NonNull AlbumViewHolder holder) {
        super.onViewRecycled(holder);
        holder.clear();
    }

    class AlbumViewHolder extends RecyclerView.ViewHolder {

        private final ItemAlbumGridBinding binding;

        AlbumViewHolder(@NonNull ItemAlbumGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.getRoot().setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onAlbumClick(getItem(position));
                }
            });
        }

        void bind(@NonNull Album album) {
            Context context = binding.getRoot().getContext();
            String name = album.getDisplayName(context);

            String count = context.getResources().getQuantityString(
                    R.plurals.item_count, album.getItemCount(), album.getItemCount());
            // CHANGED (Stage 7): virtual albums are labelled "Custom"
            if (album.isVirtual()) {
                count = count + " · " + context.getString(R.string.album_custom);
            }

            binding.albumName.setText(name);
            binding.albumCount.setText(count);
            binding.albumCover.setContentDescription(name);

            MediaItem cover = album.getCover();
            if (cover != null) {
                thumbnailLoader.load(cover, binding.albumCover);
            } else {
                // Empty custom album: just show the grey placeholder from the background
                binding.albumCover.setTag(null);
                binding.albumCover.setImageDrawable(null);
            }
        }

        void clear() {
            binding.albumCover.setTag(null);
            binding.albumCover.setImageDrawable(null);
        }
    }
}
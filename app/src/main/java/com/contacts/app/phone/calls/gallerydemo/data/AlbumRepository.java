package com.contacts.app.phone.calls.gallerydemo.data;

import androidx.annotation.NonNull;

import com.contacts.app.phone.calls.gallerydemo.model.Album;
import com.contacts.app.phone.calls.gallerydemo.model.MediaItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AlbumRepository {

    public interface LoadCallback {
        void onLoaded(@NonNull List<Album> albums);

        void onError(@NonNull Exception error);
    }

    private final MediaStoreRepository mediaStoreRepository;
    private final VirtualAlbumRepository virtualAlbumRepository;

    public AlbumRepository(@NonNull MediaStoreRepository mediaStoreRepository,
                           @NonNull VirtualAlbumRepository virtualAlbumRepository) {
        this.mediaStoreRepository = mediaStoreRepository;
        this.virtualAlbumRepository = virtualAlbumRepository;
    }

    public void loadAlbums(@NonNull LoadCallback callback) {
        mediaStoreRepository.loadAllMedia(new MediaStoreRepository.LoadCallback() {
            @Override
            public void onLoaded(@NonNull List<MediaItem> items) {
                List<Album> albums = groupIntoAlbums(items);
                albums.addAll(buildVirtualAlbums(items)); // NEW (Stage 7)
                callback.onLoaded(albums);
            }

            @Override
            public void onError(@NonNull Exception error) {
                callback.onError(error);
            }
        });
    }

    // Real albums: one per folder (BUCKET_ID), cover = newest item
    @NonNull
    public static List<Album> groupIntoAlbums(@NonNull List<MediaItem> items) {
        Map<Long, MediaItem> newestByBucket = new HashMap<>();
        Map<Long, Integer> countByBucket = new HashMap<>();

        for (MediaItem item : items) {
            long bucketId = item.getBucketId();
            MediaItem newest = newestByBucket.get(bucketId);
            if (newest == null || item.getDateAddedSeconds() > newest.getDateAddedSeconds()) {
                newestByBucket.put(bucketId, item);
            }
            Integer count = countByBucket.get(bucketId);
            countByBucket.put(bucketId, count == null ? 1 : count + 1);
        }

        List<Album> albums = new ArrayList<>(newestByBucket.size());
        for (Map.Entry<Long, MediaItem> entry : newestByBucket.entrySet()) {
            MediaItem cover = entry.getValue();
            Integer count = countByBucket.get(entry.getKey());
            albums.add(Album.real(entry.getKey(), cover.getBucketName(), cover,
                    count != null ? count : 0));
        }
        return albums;
    }

    // NEW (Stage 7): virtual albums = names saved by this app, counted against the media we can see
    @NonNull
    private List<Album> buildVirtualAlbums(@NonNull List<MediaItem> items) {
        List<Album> result = new ArrayList<>();
        for (String name : virtualAlbumRepository.getAlbumNames()) {
            Set<String> uris = virtualAlbumRepository.getItemUris(name);
            MediaItem cover = null;
            int count = 0;
            for (MediaItem item : items) {
                if (uris.contains(item.getUri().toString())) {
                    count++;
                    if (cover == null || item.getDateAddedSeconds() > cover.getDateAddedSeconds()) {
                        cover = item;
                    }
                }
            }
            result.add(Album.virtual(name, cover, count));
        }
        return result;
    }
}
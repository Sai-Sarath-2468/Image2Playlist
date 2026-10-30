package com.image2playlist.provider;

import com.image2playlist.song.SongCandidate;

import java.util.List;

public interface MusicProvider {

    MusicProviderType type();

    List<MusicTrackMatch> searchTracks(SongCandidate candidate, String storefront, String userAccessToken);

    PlaylistCreationResult createPlaylist(String name, String description, String userAccessToken);

    void addTracksToPlaylist(String playlistId, List<String> trackIds, String userAccessToken);
}

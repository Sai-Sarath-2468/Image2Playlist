package com.image2playlist.dto;

import com.image2playlist.provider.MusicProviderType;

import java.util.List;

public record PlaylistCreationResponse(
        String playlistId,
        String playlistName,
        String playlistUrl,
        MusicProviderType provider,
        int addedTracks,
        int skippedTracks,
        List<FailedPlaylistTrack> failedTracks
) {
    public record FailedPlaylistTrack(String title, String artist, String reason) { }
}

package com.image2playlist.dto;

import com.image2playlist.provider.MusicProviderType;

import java.util.List;

public record SessionResponse(
        String sessionId,
        String imageId,
        MusicProviderType provider,
        String status,
        String rawOcrText,
        List<SongReview> songs
) {
    public record SongReview(
            String candidateId,
            String rawText,
            String title,
            String artist,
            double extractionConfidence,
            String status,
            List<TrackReview> matches
    ) { }

    public record TrackReview(
            String matchId,
            String providerTrackId,
            String title,
            String artist,
            String album,
            String artworkUrl,
            String trackUrl,
            double matchScore,
            boolean selected
    ) { }
}

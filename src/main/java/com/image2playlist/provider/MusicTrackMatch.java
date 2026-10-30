package com.image2playlist.provider;

public record MusicTrackMatch(
        MusicProviderType provider,
        String providerTrackId,
        String title,
        String artist,
        String album,
        String artworkUrl,
        String trackUrl,
        double matchScore
) {
}

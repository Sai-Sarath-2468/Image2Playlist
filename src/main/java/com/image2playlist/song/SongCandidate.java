package com.image2playlist.song;

public record SongCandidate(
        int index,
        String title,
        String artist,
        String rawText,
        double extractionConfidence
) {
}

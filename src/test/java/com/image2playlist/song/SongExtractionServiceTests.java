package com.image2playlist.song;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SongExtractionServiceTests {

    private final SongExtractionService service = new SongExtractionService();

    @Test
    void extractsTitleArtistLinesAndPreservesOrder() {
        List<SongCandidate> songs = service.extractCandidates(
                "Blinding Lights\nThe Weeknd\n\nAs It Was\nHarry Styles"
        );

        assertEquals(2, songs.size());
        assertEquals("Blinding Lights", songs.get(0).title());
        assertEquals("The Weeknd", songs.get(0).artist());
        assertEquals(1, songs.get(0).index());
        assertEquals("As It Was", songs.get(1).title());
    }

    @Test
    void parsesDelimitedAndNumberedLines() {
        List<SongCandidate> songs = service.extractCandidates(
                "1. Blinding Lights - The Weeknd\n2. Starboy — The Weeknd"
        );

        assertEquals(2, songs.size());
        assertEquals("Starboy", songs.get(1).title());
        assertEquals("The Weeknd", songs.get(1).artist());
    }

    @Test
    void removesOnlyExactNormalizedDuplicatesAndKeepsUnclearLines() {
        List<SongCandidate> songs = service.extractCandidates(
                "Song Name - Artist\n song name — artist \nUnclear OCR"
        );

        assertEquals(2, songs.size());
        assertEquals("Unclear OCR", songs.get(1).title());
        assertNull(songs.get(1).artist());
    }
}

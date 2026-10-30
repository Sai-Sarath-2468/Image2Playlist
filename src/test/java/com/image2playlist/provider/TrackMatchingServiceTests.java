package com.image2playlist.provider;

import com.image2playlist.song.SongCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackMatchingServiceTests {

    private final TrackMatchingService service = new TrackMatchingService();

    @Test
    void ranksExactTitleAndArtistAheadOfNearMatches() {
        SongCandidate candidate = new SongCandidate(1, "HALO!", "Beyonce", "", 0.9);
        List<MusicTrackMatch> ranked = service.rankMatches(candidate, List.of(
                track("Halo", "Beyoncé"),
                track("Halo", "Adele")
        ));

        assertEquals(100.0, ranked.get(0).matchScore());
        assertEquals("Beyoncé", ranked.get(0).artist());
        org.junit.jupiter.api.Assertions.assertTrue(ranked.get(0).matchScore() > ranked.get(1).matchScore());
    }

    private MusicTrackMatch track(String title, String artist) {
        return new MusicTrackMatch(
                MusicProviderType.APPLE_MUSIC, title + artist, title, artist,
                null, null, null, 0
        );
    }
}

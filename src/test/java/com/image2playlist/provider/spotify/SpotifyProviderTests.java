package com.image2playlist.provider.spotify;

import com.image2playlist.provider.MusicProviderType;
import com.image2playlist.song.SongCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class SpotifyProviderTests {

    @Test
    void searchesAndMapsSpotifyCatalogTracks() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/search?")))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer user-access-token"))
                .andRespond(withSuccess("""
                        {"tracks":{"items":[{"id":"track-123","name":"Blinding Lights",
                          "artists":[{"name":"The Weeknd"}],"album":{"name":"After Hours",
                          "images":[{"url":"https://example.com/art.jpg"}]},
                          "external_urls":{"spotify":"https://open.spotify.com/track/track-123"}}]}}
                        """, MediaType.APPLICATION_JSON));

        SpotifyProvider provider = new SpotifyProvider(builder);
        var results = provider.searchTracks(
                new SongCandidate(1, "Blinding Lights", "The Weeknd", "", 0.8), "US", "user-access-token"
        );

        assertEquals(1, results.size());
        assertEquals(MusicProviderType.SPOTIFY, results.get(0).provider());
        assertEquals("track-123", results.get(0).providerTrackId());
        assertEquals("The Weeknd", results.get(0).artist());
        assertEquals("https://example.com/art.jpg", results.get(0).artworkUrl());
        server.verify();
    }

    @Test
    void createsPrivatePlaylistAndAddsItemsUsingCurrentEndpoints() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.spotify.com/v1/me/playlists"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer user-access-token"))
                .andExpect(content().json("{\"name\":\"Image mix\",\"public\":false}"))
                .andRespond(withSuccess("""
                        {"id":"playlist-123","external_urls":{"spotify":"https://open.spotify.com/playlist/playlist-123"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.spotify.com/v1/playlists/playlist-123/items"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer user-access-token"))
                .andExpect(content().json("{\"uris\":[\"spotify:track:track-123\"]}"))
                .andRespond(withStatus(CREATED));

        SpotifyProvider provider = new SpotifyProvider(builder);
        var created = provider.createPlaylist("Image mix", null, "user-access-token");
        provider.addTracksToPlaylist(created.providerPlaylistId(), List.of("track-123"), "user-access-token");

        assertEquals("playlist-123", created.providerPlaylistId());
        assertEquals("https://open.spotify.com/playlist/playlist-123", created.playlistUrl());
        server.verify();
    }
}

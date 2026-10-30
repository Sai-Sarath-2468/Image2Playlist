package com.image2playlist.provider.apple;

import com.image2playlist.provider.MusicProviderType;
import com.image2playlist.song.SongCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;

class AppleMusicProviderTests {

    @Test
    void searchesSongsWithDeveloperTokenAndMapsCatalogResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/catalog/us/search")))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer developer-token"))
                .andRespond(withSuccess("""
                        {"results":{"songs":{"data":[{"id":"123","attributes":{
                          "name":"Blinding Lights","artistName":"The Weeknd",
                          "albumName":"After Hours","url":"https://music.apple.com/song/123",
                          "artwork":{"url":"https://example.com/{w}x{h}bb.jpg"}
                        }}]}}}
                        """, MediaType.APPLICATION_JSON));

        AppleMusicProvider provider = new AppleMusicProvider(builder, "developer-token");
        var results = provider.searchTracks(
                new SongCandidate(1, "Blinding Lights", "The Weeknd", "", 0.8), "US", null
        );

        assertEquals(1, results.size());
        assertEquals(MusicProviderType.APPLE_MUSIC, results.get(0).provider());
        assertEquals("123", results.get(0).providerTrackId());
        assertEquals("After Hours", results.get(0).album());
        assertEquals("https://example.com/500x500bb.jpg", results.get(0).artworkUrl());
        server.verify();
    }

    @Test
    void createsPlaylistAndAddsOnlyDistinctCatalogTrackIds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/me/library/playlists")))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer developer-token"))
                .andExpect(header("Music-User-Token", "user-token"))
                .andExpect(content().json("""
                        {"attributes":{"name":"My Mix","description":"Made from a photo"}}
                        """))
                .andRespond(withStatus(CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"data":[{"id":"p.playlist123"}]}
                        """));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/me/library/playlists/p.playlist123/tracks")))
                .andExpect(method(POST))
                .andExpect(header("Music-User-Token", "user-token"))
                .andExpect(content().json("""
                        {"data":[{"id":"song1","type":"songs"},{"id":"song2","type":"songs"}]}
                        """))
                .andRespond(withStatus(NO_CONTENT));

        AppleMusicProvider provider = new AppleMusicProvider(builder, "developer-token");
        var created = provider.createPlaylist("My Mix", "Made from a photo", "user-token");
        provider.addTracksToPlaylist(
                created.providerPlaylistId(), List.of("song1", "song1", "song2"), "user-token"
        );

        assertEquals("p.playlist123", created.providerPlaylistId());
        assertEquals("https://music.apple.com/library/playlist/p.playlist123", created.playlistUrl());
        server.verify();
    }
}

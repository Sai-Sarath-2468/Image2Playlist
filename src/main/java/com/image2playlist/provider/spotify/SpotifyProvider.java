package com.image2playlist.provider.spotify;

import com.image2playlist.provider.MusicProvider;
import com.image2playlist.provider.MusicProviderException;
import com.image2playlist.provider.MusicProviderType;
import com.image2playlist.provider.MusicTrackMatch;
import com.image2playlist.provider.PlaylistCreationResult;
import com.image2playlist.song.SongCandidate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpotifyProvider implements MusicProvider {

    private static final int SEARCH_LIMIT = 10;
    private final RestClient restClient;

    public SpotifyProvider(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://api.spotify.com/v1").build();
    }

    @Override
    public MusicProviderType type() {
        return MusicProviderType.SPOTIFY;
    }

    @Override
    public List<MusicTrackMatch> searchTracks(
            SongCandidate candidate, String storefront, String userAccessToken
    ) {
        requireToken(userAccessToken);
        if (candidate == null || candidate.title() == null || candidate.title().isBlank()) {
            return List.of();
        }
        String term = candidate.artist() == null || candidate.artist().isBlank()
                ? candidate.title() : "track:" + candidate.title() + " artist:" + candidate.artist();
        try {
            JsonNode response = restClient.get()
                    .uri(uri -> searchUri(uri, term, storefront))
                    .header("Authorization", "Bearer " + userAccessToken)
                    .retrieve()
                    .body(JsonNode.class);
            return readMatches(response);
        } catch (RestClientResponseException exception) {
            throw mapError(exception, "Spotify catalog search failed");
        } catch (RestClientException exception) {
            throw new MusicProviderException("MUSIC_PROVIDER_ERROR", "Spotify catalog search failed", exception);
        }
    }

    @Override
    public PlaylistCreationResult createPlaylist(String name, String description, String userAccessToken) {
        requireToken(userAccessToken);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("public", false);
        if (description != null && !description.isBlank()) body.put("description", description);
        try {
            JsonNode response = restClient.post()
                    .uri("/me/playlists")
                    .header("Authorization", "Bearer " + userAccessToken)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String id = textOrNull(response == null ? null : response.path("id"));
            String url = textOrNull(response == null ? null : response.path("external_urls").path("spotify"));
            if (id == null) {
                throw new MusicProviderException("PLAYLIST_CREATION_FAILED", "Spotify did not return a playlist ID");
            }
            return new PlaylistCreationResult(id, url == null ? "https://open.spotify.com/playlist/" + id : url);
        } catch (RestClientResponseException exception) {
            throw mapError(exception, "Spotify playlist creation failed");
        } catch (RestClientException exception) {
            throw new MusicProviderException("PLAYLIST_CREATION_FAILED", "Spotify playlist creation failed", exception);
        }
    }

    @Override
    public void addTracksToPlaylist(String playlistId, List<String> trackIds, String userAccessToken) {
        requireToken(userAccessToken);
        if (playlistId == null || playlistId.isBlank() || trackIds == null || trackIds.isEmpty()) return;
        List<String> uris = trackIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .map(id -> id.startsWith("spotify:track:") ? id : "spotify:track:" + id)
                .toList();
        for (int offset = 0; offset < uris.size(); offset += 100) {
            Map<String, Object> body = Map.of("uris", uris.subList(offset, Math.min(offset + 100, uris.size())));
            try {
                restClient.post()
                        .uri(uri -> uri.path("/playlists/{id}/items").build(playlistId))
                        .header("Authorization", "Bearer " + userAccessToken)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException exception) {
                throw mapError(exception, "Spotify could not add playlist items");
            } catch (RestClientException exception) {
                throw new MusicProviderException("PLAYLIST_CREATION_FAILED", "Spotify could not add playlist items", exception);
            }
        }
    }

    private java.net.URI searchUri(UriBuilder uri, String term, String market) {
        var builder = uri.path("/search")
                .queryParam("q", term)
                .queryParam("type", "track")
                .queryParam("limit", SEARCH_LIMIT);
        if (market != null && market.matches("[a-zA-Z]{2}")) builder.queryParam("market", market.toUpperCase());
        return builder.build();
    }

    private List<MusicTrackMatch> readMatches(JsonNode response) {
        JsonNode items = response == null ? null : response.path("tracks").path("items");
        if (items == null || !items.isArray()) return List.of();
        List<MusicTrackMatch> results = new ArrayList<>();
        for (JsonNode track : items) {
            JsonNode artists = track.path("artists");
            List<String> names = new ArrayList<>();
            if (artists.isArray()) for (JsonNode artist : artists) {
                String name = textOrNull(artist.path("name"));
                if (name != null) names.add(name);
            }
            JsonNode images = track.path("album").path("images");
            String artwork = images.isArray() && !images.isEmpty() ? textOrNull(images.get(0).path("url")) : null;
            results.add(new MusicTrackMatch(
                    MusicProviderType.SPOTIFY,
                    textOrNull(track.path("id")),
                    textOrNull(track.path("name")),
                    String.join(", ", names),
                    textOrNull(track.path("album").path("name")),
                    artwork,
                    textOrNull(track.path("external_urls").path("spotify")),
                    0.0
            ));
        }
        return List.copyOf(results);
    }

    private void requireToken(String userAccessToken) {
        if (userAccessToken == null || userAccessToken.isBlank()) {
            throw new MusicProviderException("MUSIC_AUTH_REQUIRED", "Spotify authorization is required");
        }
    }

    private MusicProviderException mapError(RestClientResponseException exception, String message) {
        int status = exception.getStatusCode().value();
        String code = status == 401 || status == 403 ? "MUSIC_AUTH_REQUIRED"
                : status == 429 ? "MUSIC_RATE_LIMITED" : "MUSIC_PROVIDER_ERROR";
        String retry = exception.getResponseHeaders() == null ? null
                : exception.getResponseHeaders().getFirst("Retry-After");
        String suffix = status == 429 && retry != null ? " Try again in " + retry + " seconds." : "";
        return new MusicProviderException(code, message + " (HTTP " + status + ")." + suffix, exception);
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}

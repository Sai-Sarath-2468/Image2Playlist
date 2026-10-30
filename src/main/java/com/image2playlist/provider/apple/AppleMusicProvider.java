package com.image2playlist.provider.apple;

import com.image2playlist.exception.ValidationException;
import com.image2playlist.provider.MusicProvider;
import com.image2playlist.provider.MusicProviderException;
import com.image2playlist.provider.MusicProviderType;
import com.image2playlist.provider.MusicTrackMatch;
import com.image2playlist.provider.PlaylistCreationResult;
import com.image2playlist.song.SongCandidate;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Objects;

@Component
public class AppleMusicProvider implements MusicProvider {

    private static final int SEARCH_LIMIT = 10;

    private final RestClient restClient;
    private final String developerToken;

    public AppleMusicProvider(
            RestClient.Builder restClientBuilder,
            @Value("${app.apple-music.developer-token:}") String developerToken
    ) {
        this.restClient = restClientBuilder.baseUrl("https://api.music.apple.com").build();
        this.developerToken = developerToken;
    }

    @Override
    public MusicProviderType type() {
        return MusicProviderType.APPLE_MUSIC;
    }

    @Override
    public List<MusicTrackMatch> searchTracks(SongCandidate candidate, String storefront, String userAccessToken) {
        requireDeveloperToken();
        if (candidate == null || candidate.title() == null || candidate.title().isBlank()) {
            return List.of();
        }
        if (storefront == null || !storefront.matches("[a-zA-Z]{2}")) {
            throw new ValidationException("Storefront must be a two-letter country code");
        }

        String term = candidate.artist() == null || candidate.artist().isBlank()
                ? candidate.title() : candidate.title() + " " + candidate.artist();
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> searchUri(uriBuilder, storefront.toLowerCase(), term))
                    .header("Authorization", "Bearer " + developerToken)
                    .retrieve()
                    .body(JsonNode.class);
            return readMatches(response);
        } catch (RestClientResponseException exception) {
            throw toProviderException(exception, "MUSIC_PROVIDER_ERROR", "Apple Music catalog search failed");
        } catch (RestClientException exception) {
            throw new MusicProviderException(
                    "MUSIC_PROVIDER_ERROR", "Apple Music catalog search failed", exception
            );
        }
    }

    @Override
    public PlaylistCreationResult createPlaylist(String name, String description, String musicUserToken) {
        requireUserToken(musicUserToken);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", name);
        if (description != null && !description.isBlank()) {
            attributes.put("description", description);
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/v1/me/library/playlists")
                    .header("Authorization", "Bearer " + developerToken)
                    .header("Music-User-Token", musicUserToken)
                    .body(Map.of("attributes", attributes))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode playlist = response == null ? null : response.path("data").path(0);
            String playlistId = textOrNull(playlist == null ? null : playlist.path("id"));
            if (playlistId == null) {
                throw new MusicProviderException(
                        "PLAYLIST_CREATION_FAILED", "Apple Music did not return a playlist ID"
                );
            }
            return new PlaylistCreationResult(
                    playlistId, "https://music.apple.com/library/playlist/" + playlistId
            );
        } catch (RestClientResponseException exception) {
            throw toProviderException(exception, "PLAYLIST_CREATION_FAILED", "Apple Music playlist creation failed");
        } catch (RestClientException exception) {
            throw new MusicProviderException(
                    "PLAYLIST_CREATION_FAILED", "Apple Music playlist creation failed", exception
            );
        }
    }

    @Override
    public void addTracksToPlaylist(String playlistId, List<String> trackIds, String musicUserToken) {
        requireUserToken(musicUserToken);
        if (playlistId == null || playlistId.isBlank() || trackIds == null || trackIds.isEmpty()) {
            return;
        }
        List<Map<String, String>> data = trackIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .distinct()
                .map(id -> Map.of("id", id, "type", "songs"))
                .toList();
        if (data.isEmpty()) {
            return;
        }
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/v1/me/library/playlists/{id}/tracks").build(playlistId))
                    .header("Authorization", "Bearer " + developerToken)
                    .header("Music-User-Token", musicUserToken)
                    .body(Map.of("data", data))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw toProviderException(exception, "PLAYLIST_CREATION_FAILED", "Apple Music could not add tracks");
        } catch (RestClientException exception) {
            throw new MusicProviderException(
                    "PLAYLIST_CREATION_FAILED", "Apple Music could not add tracks", exception
            );
        }
    }

    private java.net.URI searchUri(UriBuilder uriBuilder, String storefront, String term) {
        return uriBuilder
                .path("/v1/catalog/{storefront}/search")
                .queryParam("term", term)
                .queryParam("types", "songs")
                .queryParam("limit", SEARCH_LIMIT)
                .build(storefront);
    }

    private List<MusicTrackMatch> readMatches(JsonNode response) {
        JsonNode songs = response == null ? null : response.path("results").path("songs").path("data");
        if (songs == null || !songs.isArray()) {
            return List.of();
        }

        List<MusicTrackMatch> matches = new ArrayList<>();
        for (JsonNode song : songs) {
            JsonNode attributes = song.path("attributes");
            String artworkUrl = textOrNull(attributes.path("artwork").path("url"));
            if (artworkUrl != null) {
                artworkUrl = artworkUrl.replace("{w}", "500").replace("{h}", "500");
            }
            matches.add(new MusicTrackMatch(
                    MusicProviderType.APPLE_MUSIC,
                    textOrNull(song.path("id")),
                    textOrNull(attributes.path("name")),
                    textOrNull(attributes.path("artistName")),
                    textOrNull(attributes.path("albumName")),
                    artworkUrl,
                    textOrNull(attributes.path("url")),
                    0.0
            ));
        }
        return List.copyOf(matches);
    }

    private void requireDeveloperToken() {
        if (developerToken == null || developerToken.isBlank()) {
            throw new MusicProviderException(
                    "MUSIC_PROVIDER_NOT_CONFIGURED",
                    "Apple Music requires APPLE_MUSIC_DEVELOPER_TOKEN"
            );
        }
    }

    private void requireUserToken(String musicUserToken) {
        requireDeveloperToken();
        if (musicUserToken == null || musicUserToken.isBlank()) {
            throw new MusicProviderException(
                    "MUSIC_AUTH_REQUIRED", "Apple Music user authorization is required"
            );
        }
    }

    private MusicProviderException toProviderException(
            RestClientResponseException exception,
            String fallbackCode,
            String message
    ) {
        int status = exception.getStatusCode().value();
        String errorCode = status == 401 || status == 403 ? "MUSIC_AUTH_REQUIRED" : fallbackCode;
        return new MusicProviderException(errorCode, message + " (HTTP " + status + ")", exception);
    }

    private String textOrNull(JsonNode value) {
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}

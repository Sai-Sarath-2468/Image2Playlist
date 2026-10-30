package com.image2playlist.controller;

import com.image2playlist.dto.CreatePlaylistRequest;
import com.image2playlist.dto.PlaylistCreationResponse;
import com.image2playlist.service.PlaylistService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class PlaylistController {

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    @PostMapping("/{sessionId}/playlist")
    public PlaylistCreationResponse createPlaylist(
            @PathVariable String sessionId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Music-User-Token", required = false) String musicUserToken,
            @RequestBody CreatePlaylistRequest request
    ) {
        return playlistService.create(
                sessionId, request.playlistName(), request.description(), bearerToken(authorization, musicUserToken)
        );
    }

    private String bearerToken(String authorization, String musicUserToken) {
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return musicUserToken;
    }
}

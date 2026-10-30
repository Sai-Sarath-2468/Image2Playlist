package com.image2playlist.controller;

import com.image2playlist.dto.SessionResponse;
import com.image2playlist.dto.UpdateSongRequest;
import com.image2playlist.service.ImageProcessingService;
import com.image2playlist.service.SessionReviewService;
import com.image2playlist.provider.MusicProviderType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final ImageProcessingService processingService;
    private final SessionReviewService reviewService;

    public SessionController(ImageProcessingService processingService, SessionReviewService reviewService) {
        this.processingService = processingService;
        this.reviewService = reviewService;
    }

    @PostMapping("/images/{imageId}/process")
    public SessionResponse processImage(
            @PathVariable String imageId,
            @RequestParam(defaultValue = "APPLE_MUSIC") MusicProviderType provider,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Music-User-Token", required = false) String musicUserToken
    ) {
        return processingService.processImage(imageId, provider, bearerToken(authorization, musicUserToken));
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionResponse getSession(@PathVariable String sessionId) {
        return reviewService.get(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/search")
    public SessionResponse searchAgain(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "us") String storefront,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Music-User-Token", required = false) String musicUserToken
    ) {
        return reviewService.searchAgain(sessionId, storefront, bearerToken(authorization, musicUserToken));
    }

    @PostMapping("/sessions/{sessionId}/songs/{candidateId}/search")
    public SessionResponse searchSong(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            @RequestParam(defaultValue = "us") String storefront,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Music-User-Token", required = false) String musicUserToken
    ) {
        return reviewService.searchSong(sessionId, candidateId, storefront, bearerToken(authorization, musicUserToken));
    }

    @PutMapping("/sessions/{sessionId}/songs/{candidateId}")
    public SessionResponse updateSong(
            @PathVariable String sessionId,
            @PathVariable String candidateId,
            @RequestBody UpdateSongRequest request
    ) {
        return reviewService.updateSong(sessionId, candidateId, request);
    }

    @DeleteMapping("/sessions/{sessionId}/songs/{candidateId}")
    public SessionResponse removeSong(
            @PathVariable String sessionId,
            @PathVariable String candidateId
    ) {
        return reviewService.removeSong(sessionId, candidateId);
    }

    private String bearerToken(String authorization, String musicUserToken) {
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return musicUserToken;
    }
}

package com.image2playlist.service;

import com.image2playlist.dto.SessionResponse;
import com.image2playlist.exception.SessionNotFoundException;
import com.image2playlist.ocr.OcrService;
import com.image2playlist.persistence.entity.ImageSessionEntity;
import com.image2playlist.persistence.entity.SongCandidateEntity;
import com.image2playlist.persistence.entity.TrackMatchEntity;
import com.image2playlist.persistence.repository.ImageSessionRepository;
import com.image2playlist.persistence.repository.SongCandidateRepository;
import com.image2playlist.persistence.repository.TrackMatchRepository;
import com.image2playlist.provider.MusicProviderException;
import com.image2playlist.provider.MusicProviderType;
import com.image2playlist.provider.MusicTrackMatch;
import com.image2playlist.provider.TrackSearchService;
import com.image2playlist.song.SongCandidate;
import com.image2playlist.song.SongExtractionService;
import com.image2playlist.storage.StoredImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ImageProcessingService {

    private final ImageService imageService;
    private final OcrService ocrService;
    private final SongExtractionService extractionService;
    private final TrackSearchService trackSearchService;
    private final ImageSessionRepository sessionRepository;
    private final SongCandidateRepository candidateRepository;
    private final TrackMatchRepository matchRepository;
    private final String defaultStorefront;

    public ImageProcessingService(
            ImageService imageService,
            OcrService ocrService,
            SongExtractionService extractionService,
            TrackSearchService trackSearchService,
            ImageSessionRepository sessionRepository,
            SongCandidateRepository candidateRepository,
            TrackMatchRepository matchRepository,
            @Value("${app.apple-music.storefront:us}") String defaultStorefront
    ) {
        this.imageService = imageService;
        this.ocrService = ocrService;
        this.extractionService = extractionService;
        this.trackSearchService = trackSearchService;
        this.sessionRepository = sessionRepository;
        this.candidateRepository = candidateRepository;
        this.matchRepository = matchRepository;
        this.defaultStorefront = defaultStorefront;
    }

    public SessionResponse processImage(String imageId, MusicProviderType provider, String userAccessToken) {
        StoredImage image = imageService.getStoredImage(imageId);
        String rawText = ocrService.extractText(image.path());
        String sessionId = UUID.randomUUID().toString();
        ImageSessionEntity session = sessionRepository.save(new ImageSessionEntity(
                sessionId, image.imageId(), provider, "PROCESSING", rawText, Instant.now()
        ));

        List<SongCandidate> candidates = extractionService.extractCandidates(rawText);
        List<SongCandidateEntity> storedCandidates = new ArrayList<>();
        for (SongCandidate candidate : candidates) {
            String candidateId = UUID.randomUUID().toString();
            storedCandidates.add(candidateRepository.save(new SongCandidateEntity(
                    candidateId,
                    sessionId,
                    candidate.rawText(),
                    candidate.title(),
                    candidate.artist(),
                    candidate.extractionConfidence(),
                    candidate.index(),
                    "NEEDS_REVIEW"
            )));
        }

        String status = "READY_FOR_REVIEW";
        try {
            for (int i = 0; i < candidates.size(); i++) {
                SongCandidate candidate = candidates.get(i);
                SongCandidateEntity storedCandidate = storedCandidates.get(i);
                List<MusicTrackMatch> matches = trackSearchService.search(
                        candidate, provider, defaultStorefront, userAccessToken
                );
                for (MusicTrackMatch match : matches) {
                    matchRepository.save(toEntity(storedCandidate.getCandidateId(), match));
                }
            }
        } catch (MusicProviderException exception) {
            status = "MUSIC_PROVIDER_NOT_CONFIGURED".equals(exception.getErrorCode())
                    ? "AWAITING_PROVIDER_CONFIGURATION" : "MATCHING_FAILED";
            if ("MUSIC_AUTH_REQUIRED".equals(exception.getErrorCode())) {
                status = "AWAITING_USER_AUTHORIZATION";
            }
        }

        session.setStatus(status);
        sessionRepository.save(session);
        return toResponse(session);
    }

    public SessionResponse getSession(String sessionId) {
        ImageSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        return toResponse(session);
    }

    private SessionResponse toResponse(ImageSessionEntity session) {
        List<SessionResponse.SongReview> songs = candidateRepository
                .findBySessionIdOrderByPositionAsc(session.getSessionId())
                .stream()
                .map(candidate -> new SessionResponse.SongReview(
                        candidate.getCandidateId(),
                        candidate.getOriginalOcrText(),
                        candidate.getTitle(),
                        candidate.getArtist(),
                        candidate.getExtractionConfidence(),
                        candidate.getStatus(),
                        matchRepository.findByCandidateIdOrderByMatchScoreDesc(candidate.getCandidateId())
                                .stream()
                                .map(match -> new SessionResponse.TrackReview(
                                        match.getMatchId(), match.getProviderTrackId(), match.getTitle(),
                                        match.getArtist(), match.getAlbum(), match.getArtworkUrl(),
                                        match.getTrackUrl(), match.getMatchScore(), match.isSelected()
                                )).toList()
                )).toList();

        return new SessionResponse(
                session.getSessionId(), session.getImageId(), session.getProvider(),
                session.getStatus(), session.getRawOcrText(), songs
        );
    }

    private TrackMatchEntity toEntity(String candidateId, MusicTrackMatch match) {
        return new TrackMatchEntity(
                UUID.randomUUID().toString(), candidateId, match.provider(), match.providerTrackId(),
                match.title(), match.artist(), match.album(), match.artworkUrl(), match.trackUrl(),
                match.matchScore(), false
        );
    }
}

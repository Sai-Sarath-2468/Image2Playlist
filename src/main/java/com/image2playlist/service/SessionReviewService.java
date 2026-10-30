package com.image2playlist.service;

import com.image2playlist.dto.SessionResponse;
import com.image2playlist.dto.UpdateSongRequest;
import com.image2playlist.exception.SessionNotFoundException;
import com.image2playlist.exception.SongCandidateNotFoundException;
import com.image2playlist.exception.ValidationException;
import com.image2playlist.persistence.entity.ImageSessionEntity;
import com.image2playlist.persistence.entity.SongCandidateEntity;
import com.image2playlist.persistence.entity.TrackMatchEntity;
import com.image2playlist.persistence.repository.ImageSessionRepository;
import com.image2playlist.persistence.repository.SongCandidateRepository;
import com.image2playlist.persistence.repository.TrackMatchRepository;
import com.image2playlist.provider.MusicProviderException;
import com.image2playlist.provider.MusicTrackMatch;
import com.image2playlist.provider.TrackSearchService;
import com.image2playlist.song.SongCandidate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SessionReviewService {

    private final ImageProcessingService processingService;
    private final ImageSessionRepository sessionRepository;
    private final SongCandidateRepository candidateRepository;
    private final TrackMatchRepository matchRepository;
    private final TrackSearchService trackSearchService;

    public SessionReviewService(
            ImageProcessingService processingService,
            ImageSessionRepository sessionRepository,
            SongCandidateRepository candidateRepository,
            TrackMatchRepository matchRepository,
            TrackSearchService trackSearchService
    ) {
        this.processingService = processingService;
        this.sessionRepository = sessionRepository;
        this.candidateRepository = candidateRepository;
        this.matchRepository = matchRepository;
        this.trackSearchService = trackSearchService;
    }

    public SessionResponse get(String sessionId) {
        requireSession(sessionId);
        return processingService.getSession(sessionId);
    }

    public SessionResponse updateSong(String sessionId, String candidateId, UpdateSongRequest request) {
        requireSession(sessionId);
        SongCandidateEntity candidate = requireCandidate(sessionId, candidateId);
        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty() || title.length() > 500) {
                throw new ValidationException("Song title must contain 1 to 500 characters");
            }
            candidate.setTitle(title);
        }
        if (request.artist() != null) {
            String artist = request.artist().trim();
            if (artist.length() > 500) {
                throw new ValidationException("Artist must be no longer than 500 characters");
            }
            candidate.setArtist(artist.isEmpty() ? null : artist);
        }

        List<TrackMatchEntity> matches = matchRepository
                .findByCandidateIdOrderByMatchScoreDesc(candidateId);
        for (TrackMatchEntity match : matches) {
            boolean selected = match.getMatchId().equals(request.selectedMatchId());
            if (selected && !match.getCandidateId().equals(candidateId)) {
                throw new SongCandidateNotFoundException(candidateId);
            }
            match.setSelected(selected);
        }
        if (request.selectedMatchId() != null
                && matches.stream().noneMatch(match -> match.getMatchId().equals(request.selectedMatchId()))) {
            throw new SongCandidateNotFoundException(candidateId);
        }
        candidate.setStatus(request.selectedMatchId() == null ? "NEEDS_REVIEW" : "MATCHED");
        candidateRepository.save(candidate);
        matchRepository.saveAll(matches);
        return processingService.getSession(sessionId);
    }

    public SessionResponse removeSong(String sessionId, String candidateId) {
        requireSession(sessionId);
        SongCandidateEntity candidate = requireCandidate(sessionId, candidateId);
        matchRepository.deleteByCandidateId(candidateId);
        candidateRepository.delete(candidate);
        return processingService.getSession(sessionId);
    }

    public SessionResponse searchAgain(String sessionId, String storefront, String userAccessToken) {
        ImageSessionEntity session = requireSession(sessionId);
        List<SongCandidateEntity> candidates = candidateRepository
                .findBySessionIdOrderByPositionAsc(sessionId);
        try {
            java.util.Map<String, List<MusicTrackMatch>> refreshedMatches = new java.util.LinkedHashMap<>();
            for (SongCandidateEntity candidate : candidates) {
                SongCandidate song = new SongCandidate(
                        candidate.getPosition(), candidate.getTitle(), candidate.getArtist(),
                        candidate.getOriginalOcrText(), candidate.getExtractionConfidence()
                );
                refreshedMatches.put(candidate.getCandidateId(), trackSearchService.search(
                        song, session.getProvider(), storefront, userAccessToken
                ));
            }
            for (var entry : refreshedMatches.entrySet()) {
                replaceMatches(entry.getKey(), entry.getValue());
            }
            session.setStatus("READY_FOR_REVIEW");
        } catch (MusicProviderException exception) {
            session.setStatus("MATCHING_FAILED");
            sessionRepository.save(session);
            throw exception;
        }
        sessionRepository.save(session);
        return processingService.getSession(sessionId);
    }

    public SessionResponse searchSong(String sessionId, String candidateId, String storefront, String userAccessToken) {
        ImageSessionEntity session = requireSession(sessionId);
        SongCandidateEntity candidate = requireCandidate(sessionId, candidateId);
        SongCandidate song = new SongCandidate(
                candidate.getPosition(), candidate.getTitle(), candidate.getArtist(),
                candidate.getOriginalOcrText(), candidate.getExtractionConfidence()
        );
        List<MusicTrackMatch> matches = trackSearchService.search(
                song, session.getProvider(), storefront, userAccessToken
        );
        replaceMatches(candidateId, matches);
        candidate.setStatus("NEEDS_REVIEW");
        candidateRepository.save(candidate);
        session.setStatus("READY_FOR_REVIEW");
        sessionRepository.save(session);
        return processingService.getSession(sessionId);
    }

    private void replaceMatches(String candidateId, List<MusicTrackMatch> matches) {
        matchRepository.deleteByCandidateId(candidateId);
        for (MusicTrackMatch match : matches) {
            matchRepository.save(new TrackMatchEntity(
                    java.util.UUID.randomUUID().toString(), candidateId,
                    match.provider(), match.providerTrackId(), match.title(), match.artist(),
                    match.album(), match.artworkUrl(), match.trackUrl(), match.matchScore(), false
            ));
        }
    }

    private ImageSessionEntity requireSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    private SongCandidateEntity requireCandidate(String sessionId, String candidateId) {
        SongCandidateEntity candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new SongCandidateNotFoundException(candidateId));
        if (!candidate.getSessionId().equals(sessionId)) {
            throw new SongCandidateNotFoundException(candidateId);
        }
        return candidate;
    }
}

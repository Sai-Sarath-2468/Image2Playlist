package com.image2playlist.service;

import com.image2playlist.dto.PlaylistCreationResponse;
import com.image2playlist.exception.SessionNotFoundException;
import com.image2playlist.exception.ValidationException;
import com.image2playlist.persistence.entity.ImageSessionEntity;
import com.image2playlist.persistence.entity.SongCandidateEntity;
import com.image2playlist.persistence.entity.TrackMatchEntity;
import com.image2playlist.persistence.repository.ImageSessionRepository;
import com.image2playlist.persistence.repository.SongCandidateRepository;
import com.image2playlist.persistence.repository.TrackMatchRepository;
import com.image2playlist.provider.MusicProvider;
import com.image2playlist.provider.MusicProviderRegistry;
import com.image2playlist.provider.MusicProviderException;
import com.image2playlist.provider.PlaylistCreationResult;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class PlaylistService {

    private static final Logger logger = LoggerFactory.getLogger(PlaylistService.class);

    private final ImageSessionRepository sessionRepository;
    private final SongCandidateRepository candidateRepository;
    private final TrackMatchRepository matchRepository;
    private final MusicProviderRegistry providerRegistry;

    public PlaylistService(
            ImageSessionRepository sessionRepository,
            SongCandidateRepository candidateRepository,
            TrackMatchRepository matchRepository,
            MusicProviderRegistry providerRegistry
    ) {
        this.sessionRepository = sessionRepository;
        this.candidateRepository = candidateRepository;
        this.matchRepository = matchRepository;
        this.providerRegistry = providerRegistry;
    }

    public PlaylistCreationResponse create(
            String sessionId,
            String playlistName,
            String description,
            String musicUserToken
    ) {
        if (playlistName == null || playlistName.isBlank() || playlistName.length() > 150) {
            throw new ValidationException("Playlist name must contain 1 to 150 characters");
        }
        ImageSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        List<SongCandidateEntity> candidates = candidateRepository
                .findBySessionIdOrderByPositionAsc(sessionId);
        List<TrackMatchEntity> selectedMatches = candidates.stream()
                .flatMap(candidate -> matchRepository
                        .findByCandidateIdOrderByMatchScoreDesc(candidate.getCandidateId())
                        .stream()
                        .filter(TrackMatchEntity::isSelected)
                        .limit(1))
                .toList();
        if (selectedMatches.isEmpty()) {
            throw new ValidationException("Select at least one match before creating a playlist");
        }

        LinkedHashSet<String> uniqueTrackIds = new LinkedHashSet<>();
        List<TrackMatchEntity> tracksToAdd = new ArrayList<>();
        int skipped = Math.max(0, candidates.size() - selectedMatches.size());
        for (TrackMatchEntity match : selectedMatches) {
            if (uniqueTrackIds.add(match.getProviderTrackId())) {
                tracksToAdd.add(match);
            } else {
                skipped++;
            }
        }

        MusicProvider provider = providerRegistry.get(session.getProvider());
        logger.info("Playlist creation started sessionId={} provider={} selectedTrackCount={}",
                sessionId, session.getProvider(), tracksToAdd.size());
        PlaylistCreationResult created = provider.createPlaylist(
                playlistName.trim(), description, musicUserToken
        );
        List<PlaylistCreationResponse.FailedPlaylistTrack> failed = new ArrayList<>();
        int added = 0;
        for (int index = 0; index < tracksToAdd.size(); index++) {
            TrackMatchEntity match = tracksToAdd.get(index);
            try {
                provider.addTracksToPlaylist(
                        created.providerPlaylistId(), List.of(match.getProviderTrackId()), musicUserToken
                );
                added++;
            } catch (MusicProviderException exception) {
                failed.add(new PlaylistCreationResponse.FailedPlaylistTrack(
                        match.getTitle(), match.getArtist(), exception.getMessage()
                ));
                if ("MUSIC_RATE_LIMITED".equals(exception.getErrorCode())) {
                    for (TrackMatchEntity remaining : tracksToAdd.subList(index + 1, tracksToAdd.size())) {
                        failed.add(new PlaylistCreationResponse.FailedPlaylistTrack(
                                remaining.getTitle(), remaining.getArtist(), exception.getMessage()
                        ));
                    }
                    break;
                }
            }
        }
        session.setStatus(failed.isEmpty() && skipped == 0 ? "PLAYLIST_CREATED" : "PLAYLIST_PARTIAL");
        sessionRepository.save(session);
        logger.info("Playlist creation completed sessionId={} addedTracks={} skippedTracks={} failedTracks={}",
                sessionId, added, skipped, failed.size());

        return new PlaylistCreationResponse(
                created.providerPlaylistId(), playlistName.trim(), created.playlistUrl(),
                session.getProvider(), added, skipped, List.copyOf(failed)
        );
    }
}

package com.image2playlist.provider;

import com.image2playlist.song.SongCandidate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class TrackSearchService {

    private static final Logger logger = LoggerFactory.getLogger(TrackSearchService.class);

    private final MusicProviderRegistry providerRegistry;
    private final TrackMatchingService matchingService;

    public TrackSearchService(
            MusicProviderRegistry providerRegistry,
            TrackMatchingService matchingService
    ) {
        this.providerRegistry = providerRegistry;
        this.matchingService = matchingService;
    }

    public List<MusicTrackMatch> search(
            SongCandidate candidate,
            MusicProviderType providerType,
            String storefront,
            String userAccessToken
    ) {
        logger.info("Track search started provider={} candidateIndex={}", providerType,
                candidate == null ? null : candidate.index());
        List<MusicTrackMatch> matches = providerRegistry.get(providerType)
                .searchTracks(candidate, storefront, userAccessToken);
        List<MusicTrackMatch> rankedMatches = matchingService.rankMatches(candidate, matches);
        logger.info("Track search completed provider={} resultCount={}", providerType, rankedMatches.size());
        return rankedMatches;
    }
}

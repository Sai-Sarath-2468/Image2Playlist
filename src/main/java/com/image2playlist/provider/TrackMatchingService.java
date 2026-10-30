package com.image2playlist.provider;

import com.image2playlist.song.SongCandidate;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TrackMatchingService {

    public List<MusicTrackMatch> rankMatches(SongCandidate candidate, List<MusicTrackMatch> matches) {
        if (candidate == null || matches == null || matches.isEmpty()) {
            return List.of();
        }

        return matches.stream()
                .map(match -> withScore(match, score(candidate, match)))
                .sorted(Comparator.comparingDouble(MusicTrackMatch::matchScore).reversed())
                .toList();
    }

    private double score(SongCandidate candidate, MusicTrackMatch match) {
        double titleScore = similarity(candidate.title(), match.title());
        if (candidate.artist() == null || candidate.artist().isBlank()) {
            return roundPercent(titleScore);
        }
        double artistScore = similarity(candidate.artist(), match.artist());
        return roundPercent((titleScore * 0.7) + (artistScore * 0.3));
    }

    private double similarity(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) {
            return 0;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return 1;
        }

        int distance = levenshteinDistance(normalizedLeft.replace(" ", ""), normalizedRight.replace(" ", ""));
        double editSimilarity = Math.max(
                0,
                1.0 - ((double) distance / Math.max(normalizedLeft.length(), normalizedRight.length()))
        );
        Set<String> leftTokens = tokens(normalizedLeft);
        Set<String> rightTokens = tokens(normalizedRight);
        Set<String> intersection = leftTokens.stream().filter(rightTokens::contains).collect(Collectors.toSet());
        Set<String> union = new java.util.HashSet<>(leftTokens);
        union.addAll(rightTokens);
        double tokenSimilarity = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
        return (editSimilarity * 0.75) + (tokenSimilarity * 0.25);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private Set<String> tokens(String value) {
        return Arrays.stream(value.split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitutionCost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitutionCost
                );
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private double roundPercent(double score) {
        return Math.round(score * 1000.0) / 10.0;
    }

    private MusicTrackMatch withScore(MusicTrackMatch match, double score) {
        return new MusicTrackMatch(
                match.provider(), match.providerTrackId(), match.title(), match.artist(),
                match.album(), match.artworkUrl(), match.trackUrl(), score
        );
    }
}

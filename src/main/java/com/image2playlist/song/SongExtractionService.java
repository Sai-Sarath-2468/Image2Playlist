package com.image2playlist.song;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SongExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(SongExtractionService.class);

    private static final Pattern NUMBERED_PREFIX = Pattern.compile("^\\s*(?:\\d+[.)]|[-*•])\\s*");
    private static final Pattern SEPARATOR = Pattern.compile("\\s+[-–—]\\s+");

    public List<SongCandidate> extractCandidates(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        List<String> lines = rawText.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> NUMBERED_PREFIX.matcher(line).replaceFirst("").trim())
                .filter(line -> !line.isEmpty())
                .toList();

        List<SongCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < lines.size();) {
            String line = lines.get(i);
            String[] pair = SEPARATOR.split(line, 2);
            if (pair.length == 2 && !pair[0].isBlank() && !pair[1].isBlank()) {
                addIfUnique(candidates, seen, pair[0].trim(), pair[1].trim(), line, 0.85);
                i++;
                continue;
            }

            if (i + 1 < lines.size() && !hasSeparator(lines.get(i + 1))) {
                String artist = lines.get(i + 1);
                String rawPair = line + "\n" + artist;
                addIfUnique(candidates, seen, line, artist, rawPair, 0.70);
                i += 2;
            } else {
                addIfUnique(candidates, seen, line, null, line, 0.35);
                i++;
            }
        }

        logger.info("Song extraction completed candidateCount={}", candidates.size());
        return List.copyOf(candidates);
    }

    private boolean hasSeparator(String line) {
        return SEPARATOR.split(line, 2).length == 2;
    }

    private void addIfUnique(
            List<SongCandidate> candidates,
            Set<String> seen,
            String title,
            String artist,
            String rawText,
            double confidence
    ) {
        String key = normalize(title) + "|" + normalize(artist == null ? "" : artist);
        if (seen.add(key)) {
            candidates.add(new SongCandidate(
                    candidates.size() + 1,
                    title,
                    artist,
                    rawText,
                    confidence
            ));
        }
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}

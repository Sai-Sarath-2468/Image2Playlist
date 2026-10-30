package com.image2playlist.exception;

public class SongCandidateNotFoundException extends RuntimeException {
    public SongCandidateNotFoundException(String candidateId) {
        super("Song candidate not found: " + candidateId);
    }
}

package com.image2playlist.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "song_candidates")
public class SongCandidateEntity {

    @Id
    @Column(length = 36)
    private String candidateId;

    @Column(nullable = false, length = 36)
    private String sessionId;

    @Lob
    private String originalOcrText;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String artist;

    @Column(nullable = false)
    private double extractionConfidence;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 30)
    private String status;

    protected SongCandidateEntity() {
    }

    public SongCandidateEntity(String candidateId, String sessionId, String originalOcrText,
                               String title, String artist, double extractionConfidence,
                               int position, String status) {
        this.candidateId = candidateId;
        this.sessionId = sessionId;
        this.originalOcrText = originalOcrText;
        this.title = title;
        this.artist = artist;
        this.extractionConfidence = extractionConfidence;
        this.position = position;
        this.status = status;
    }

    public String getCandidateId() { return candidateId; }
    public String getSessionId() { return sessionId; }
    public String getOriginalOcrText() { return originalOcrText; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public double getExtractionConfidence() { return extractionConfidence; }
    public int getPosition() { return position; }
    public String getStatus() { return status; }
    public void setTitle(String title) { this.title = title; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setStatus(String status) { this.status = status; }
}

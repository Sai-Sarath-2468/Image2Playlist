package com.image2playlist.persistence.entity;

import com.image2playlist.provider.MusicProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "track_matches")
public class TrackMatchEntity {

    @Id
    @Column(length = 36)
    private String matchId;

    @Column(nullable = false, length = 36)
    private String candidateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MusicProviderType provider;

    @Column(nullable = false, length = 100)
    private String providerTrackId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 500)
    private String artist;

    @Column(length = 500)
    private String album;

    @Column(length = 1000)
    private String artworkUrl;

    @Column(length = 1000)
    private String trackUrl;

    @Column(nullable = false)
    private double matchScore;

    @Column(nullable = false)
    private boolean selected;

    protected TrackMatchEntity() {
    }

    public TrackMatchEntity(String matchId, String candidateId, MusicProviderType provider,
                            String providerTrackId, String title, String artist, String album,
                            String artworkUrl, String trackUrl, double matchScore, boolean selected) {
        this.matchId = matchId;
        this.candidateId = candidateId;
        this.provider = provider;
        this.providerTrackId = providerTrackId;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.artworkUrl = artworkUrl;
        this.trackUrl = trackUrl;
        this.matchScore = matchScore;
        this.selected = selected;
    }

    public String getMatchId() { return matchId; }
    public String getCandidateId() { return candidateId; }
    public MusicProviderType getProvider() { return provider; }
    public String getProviderTrackId() { return providerTrackId; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getArtworkUrl() { return artworkUrl; }
    public String getTrackUrl() { return trackUrl; }
    public double getMatchScore() { return matchScore; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}

package com.image2playlist.persistence.entity;

import com.image2playlist.provider.MusicProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "image_sessions")
public class ImageSessionEntity {

    @Id
    @Column(length = 36)
    private String sessionId;

    @Column(nullable = false, length = 36)
    private String imageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MusicProviderType provider;

    @Column(nullable = false, length = 30)
    private String status;

    @Lob
    private String rawOcrText;

    @Column(nullable = false)
    private Instant createdAt;

    protected ImageSessionEntity() {
    }

    public ImageSessionEntity(String sessionId, String imageId, MusicProviderType provider,
                              String status, String rawOcrText, Instant createdAt) {
        this.sessionId = sessionId;
        this.imageId = imageId;
        this.provider = provider;
        this.status = status;
        this.rawOcrText = rawOcrText;
        this.createdAt = createdAt;
    }

    public String getSessionId() { return sessionId; }
    public String getImageId() { return imageId; }
    public MusicProviderType getProvider() { return provider; }
    public String getStatus() { return status; }
    public String getRawOcrText() { return rawOcrText; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(String status) { this.status = status; }
}

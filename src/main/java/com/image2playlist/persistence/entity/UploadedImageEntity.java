package com.image2playlist.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "uploaded_images")
public class UploadedImageEntity {

    @Id
    @Column(length = 36)
    private String imageId;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String storedFileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private Instant createdAt;

    protected UploadedImageEntity() {
    }

    public UploadedImageEntity(String imageId, String originalFileName, String storedFileName,
                               String contentType, long size, Instant createdAt) {
        this.imageId = imageId;
        this.originalFileName = originalFileName == null ? "image" : originalFileName;
        this.storedFileName = storedFileName;
        this.contentType = contentType;
        this.size = size;
        this.createdAt = createdAt;
    }

    public String getImageId() { return imageId; }
    public String getOriginalFileName() { return originalFileName; }
    public String getStoredFileName() { return storedFileName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public Instant getCreatedAt() { return createdAt; }
}

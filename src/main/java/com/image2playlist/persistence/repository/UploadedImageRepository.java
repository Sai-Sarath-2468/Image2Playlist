package com.image2playlist.persistence.repository;

import com.image2playlist.persistence.entity.UploadedImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedImageRepository extends JpaRepository<UploadedImageEntity, String> {
}

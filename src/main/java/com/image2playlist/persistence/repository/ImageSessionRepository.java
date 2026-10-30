package com.image2playlist.persistence.repository;

import com.image2playlist.persistence.entity.ImageSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageSessionRepository extends JpaRepository<ImageSessionEntity, String> {
}

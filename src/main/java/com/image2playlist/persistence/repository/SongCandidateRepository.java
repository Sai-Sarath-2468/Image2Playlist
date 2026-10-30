package com.image2playlist.persistence.repository;

import com.image2playlist.persistence.entity.SongCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SongCandidateRepository extends JpaRepository<SongCandidateEntity, String> {
    List<SongCandidateEntity> findBySessionIdOrderByPositionAsc(String sessionId);
}

package com.image2playlist.persistence.repository;

import com.image2playlist.persistence.entity.TrackMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackMatchRepository extends JpaRepository<TrackMatchEntity, String> {
    List<TrackMatchEntity> findByCandidateIdOrderByMatchScoreDesc(String candidateId);
    List<TrackMatchEntity> findByCandidateIdIn(List<String> candidateIds);
    void deleteByCandidateId(String candidateId);
}

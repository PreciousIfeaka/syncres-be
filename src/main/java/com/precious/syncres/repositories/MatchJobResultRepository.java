package com.precious.syncres.repositories;

import com.precious.syncres.entities.MatchJobResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchJobResultRepository extends JpaRepository<MatchJobResult, UUID> {
    Optional<MatchJobResult> findByJobrunrJobId(String jobId);
    Optional<MatchJobResult> findBySessionId(String sessionId);
    List<MatchJobResult> findByUserIdOrderByCreatedAtDesc(UUID userId);
}

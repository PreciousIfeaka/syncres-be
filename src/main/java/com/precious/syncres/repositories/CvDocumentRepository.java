package com.precious.syncres.repositories;

import com.precious.syncres.entities.CvDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CvDocumentRepository extends JpaRepository<CvDocument, UUID> {
    List<CvDocument> findAllByUserId(UUID userId);
    Optional<CvDocument> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByIdAndUserId(UUID id, UUID userId);
}

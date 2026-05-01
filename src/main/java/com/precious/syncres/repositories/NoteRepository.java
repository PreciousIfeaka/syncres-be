package com.precious.syncres.repositories;

import com.precious.syncres.entities.ApplicationNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<ApplicationNote, UUID> {
    List<ApplicationNote> findAllByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
    Optional<ApplicationNote> findByIdAndApplicationId(UUID id, UUID applicationId);
}

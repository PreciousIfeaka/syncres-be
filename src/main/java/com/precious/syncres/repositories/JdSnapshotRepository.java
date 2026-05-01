package com.precious.syncres.repositories;

import com.precious.syncres.entities.JdSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JdSnapshotRepository extends JpaRepository<JdSnapshot, UUID> {
    List<JdSnapshot> findAllByUserIdOrderByCapturedAtDesc(UUID userId);
    Optional<JdSnapshot> findByIdAndUserId(UUID id, UUID userId);
}

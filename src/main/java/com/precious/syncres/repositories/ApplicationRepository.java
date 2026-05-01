package com.precious.syncres.repositories;

import com.precious.syncres.entities.ApplicationStatus;
import com.precious.syncres.entities.JobApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<JobApplication, UUID> {

    @Query("SELECT j FROM JobApplication j WHERE j.user.id = :userId AND j.deletedAt IS NULL " +
           "AND (:status IS NULL OR j.applicationStatus = :status) " +
           "AND (:company IS NULL OR LOWER(j.companyName) LIKE LOWER(CONCAT('%', :company, '%'))) " +
           "ORDER BY j.createdAt DESC")
    Page<JobApplication> findAllFiltered(
            @Param("userId") UUID userId,
            @Param("status") ApplicationStatus status,
            @Param("company") String company,
            Pageable pageable);

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    @Query("SELECT j.applicationStatus as status, COUNT(j) as count FROM JobApplication j " +
           "WHERE j.user.id = :userId AND j.deletedAt IS NULL GROUP BY j.applicationStatus")
    List<Map<String, Object>> countByStatusForUser(@Param("userId") UUID userId);

    @Query("SELECT AVG(j.matchScore) FROM JobApplication j WHERE j.user.id = :userId AND j.deletedAt IS NULL AND j.matchScore IS NOT NULL")
    Double getAverageMatchScoreForUser(@Param("userId") UUID userId);

    long countByUserIdAndCreatedAtAfterAndDeletedAtIsNull(UUID userId, OffsetDateTime after);

    Optional<JobApplication> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    boolean existsByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}

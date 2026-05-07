package com.precious.syncres.repositories;

import com.precious.syncres.entities.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {
    
    @Query("SELECT t FROM OtpToken t WHERE t.user.id = :userId AND t.purpose = :purpose AND t.used = false ORDER BY t.createdAt DESC LIMIT 1")
    Optional<OtpToken> findLatestValidToken(UUID userId, OtpToken.OtpPurpose purpose);

    void deleteAllByUserIdAndPurpose(UUID userId, OtpToken.OtpPurpose purpose);
}

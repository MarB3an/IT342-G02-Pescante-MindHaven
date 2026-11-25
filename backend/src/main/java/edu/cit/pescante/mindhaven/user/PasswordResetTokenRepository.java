package edu.cit.pescante.mindhaven.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true, t.usedAt = :now WHERE t.ownerType = :ownerType AND t.ownerId = :ownerId AND t.used = false")
    int invalidateAllActiveByOwner(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerId") UUID ownerId,
            @Param("now") Instant now
    );
}

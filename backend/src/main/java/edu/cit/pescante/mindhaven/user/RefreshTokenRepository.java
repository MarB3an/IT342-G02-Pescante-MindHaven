package edu.cit.pescante.mindhaven.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.ownerType = :ownerType AND r.ownerId = :ownerId AND r.revoked = false")
    int revokeAllActiveByOwner(@Param("ownerType") OwnerType ownerType, @Param("ownerId") UUID ownerId);
}

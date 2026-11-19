package edu.cit.pescante.mindhaven.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    Optional<StudentProfile> findByAccountAccountId(UUID accountId);
    boolean existsByPseudonymHandleIgnoreCase(String pseudonymHandle);
}

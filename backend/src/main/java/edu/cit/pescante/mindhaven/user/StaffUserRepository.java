package edu.cit.pescante.mindhaven.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {
    Optional<StaffUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<StaffUser> findAllByOrderByCreatedAtDesc();
    long countByRole(Role role);
    long countByRoleAndOnCallTrueAndActiveTrue(Role role);
}

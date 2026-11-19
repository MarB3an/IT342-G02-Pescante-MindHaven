package edu.cit.pescante.mindhaven.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentAccountRepository extends JpaRepository<StudentAccount, UUID> {
    boolean existsByEmailHash(String emailHash);
    Optional<StudentAccount> findByEmailHash(String emailHash);
    long countByActiveTrue();
}

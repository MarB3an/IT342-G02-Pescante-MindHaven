package edu.cit.pescante.mindhaven.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "student_profile")
public class StudentProfile {

    @Id
    @Column(name = "pseudonym_id", nullable = false, updatable = false)
    private UUID pseudonymId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private StudentAccount account;

    @Column(name = "pseudonym_handle", nullable = false, unique = true, length = 60)
    private String pseudonymHandle;

    @Column(name = "avatar_code", nullable = false, length = 40)
    private String avatarCode = "sparrow";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @PrePersist
    void onCreate() {
        if (pseudonymId == null) {
            pseudonymId = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }

    public UUID getPseudonymId() {
        return pseudonymId;
    }

    public void setPseudonymId(UUID pseudonymId) {
        this.pseudonymId = pseudonymId;
    }

    public StudentAccount getAccount() {
        return account;
    }

    public void setAccount(StudentAccount account) {
        this.account = account;
    }

    public String getPseudonymHandle() {
        return pseudonymHandle;
    }

    public void setPseudonymHandle(String pseudonymHandle) {
        this.pseudonymHandle = pseudonymHandle;
    }

    public String getAvatarCode() {
        return avatarCode;
    }

    public void setAvatarCode(String avatarCode) {
        this.avatarCode = avatarCode;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}

package com.techenglish.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Soft-delete flag: 0 = live, 1 = deleted. Rows are never removed from the table. */
    @Column(nullable = false)
    private Integer deleted = 0;

    /**
     * Lifecycle status: 0 = enabled, 1 = disabled (e.g. an account that must not log in).
     * Named enableStatus / enable_status because four entities already own a domain
     * {@code status} column (grammar progress, vocab SRS state, task state, AI log outcome).
     */
    @Column(name = "enable_status", nullable = false)
    private Integer enableStatus = 0;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

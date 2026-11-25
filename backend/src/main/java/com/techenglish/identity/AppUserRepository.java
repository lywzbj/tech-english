package com.techenglish.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Soft-deleted users are invisible here thanks to the entity's @SQLRestriction. */
    Optional<AppUser> findByEmailIgnoreCase(String email);

    /**
     * Native query on purpose: the unique index counts soft-deleted rows, and
     * @SQLRestriction does not apply to native SQL, so this sees every row.
     */
    @Query(value = "SELECT COUNT(*) FROM app_user WHERE LOWER(email) = LOWER(:email)", nativeQuery = true)
    long countByEmailIncludingDeleted(String email);
}

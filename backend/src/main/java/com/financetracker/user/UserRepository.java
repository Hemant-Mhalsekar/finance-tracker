package com.financetracker.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Single atomic update so two simultaneous onboarding requests cannot both succeed.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.payDay = :payDay where u.id = :id and u.payDay is null")
    int claimPayDay(@Param("id") UUID id, @Param("payDay") short payDay);

    // SELECT ... FOR UPDATE: serializes cycle roll-forward, salary changes and pay day changes
    // for one user, so concurrent requests never compute cycles from different pay days.
    // Must be the first load of the user in the transaction, otherwise a stale cached copy is returned.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}

package com.financetracker.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Single atomic update so two simultaneous onboarding requests cannot both succeed.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.payDay = :payDay where u.id = :id and u.payDay is null")
    int claimPayDay(@Param("id") UUID id, @Param("payDay") short payDay);
}

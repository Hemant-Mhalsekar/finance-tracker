package com.financetracker.cycle;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Deliberately extends Repository (not JpaRepository/CrudRepository) so no method that
// fetches by id alone is exposed; every query must explicitly filter by userId.
public interface CycleRepository extends Repository<Cycle, UUID> {

    Cycle save(Cycle cycle);

    Optional<Cycle> findTopByUserIdOrderByPeriodStartDesc(UUID userId);

    @Query("select c from Cycle c where c.userId = :userId and c.periodStart <= :date and c.periodEnd >= :date")
    Optional<Cycle> findContaining(@Param("userId") UUID userId, @Param("date") LocalDate date);

    List<Cycle> findAllByUserIdOrderByPeriodStartDesc(UUID userId);
}


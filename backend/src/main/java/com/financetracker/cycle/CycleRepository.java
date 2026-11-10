package com.financetracker.cycle;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Deliberately extends Repository (not JpaRepository/CrudRepository) so no method that
// fetches by id alone is exposed; every query must explicitly filter by userId.
public interface CycleRepository extends Repository<Cycle, UUID> {

    Cycle save(Cycle cycle);

    Cycle saveAndFlush(Cycle cycle);

    Optional<Cycle> findTopByUserIdOrderByPeriodStartDesc(UUID userId);

    @Query("select c from Cycle c where c.userId = :userId and c.periodStart <= :date and c.periodEnd >= :date")
    Optional<Cycle> findContaining(@Param("userId") UUID userId, @Param("date") LocalDate date);

    List<Cycle> findAllByUserIdOrderByPeriodStartDesc(UUID userId);

    // ON CONFLICT DO NOTHING keeps this safe even if two requests create the same cycle.
    @Modifying
    @Query(value = """
            insert into cycles (user_id, period_start, period_end, salary_paise,
                                needs_pct, wants_pct, savings_pct,
                                needs_paise, wants_paise, savings_paise)
            values (:userId, :periodStart, :periodEnd, :salaryPaise,
                    :needsPct, :wantsPct, :savingsPct,
                    :needsPaise, :wantsPaise, :savingsPaise)
            on conflict (user_id, period_start) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") UUID userId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("salaryPaise") long salaryPaise,
            @Param("needsPct") short needsPct,
            @Param("wantsPct") short wantsPct,
            @Param("savingsPct") short savingsPct,
            @Param("needsPaise") long needsPaise,
            @Param("wantsPaise") long wantsPaise,
            @Param("savingsPaise") long savingsPaise);
}

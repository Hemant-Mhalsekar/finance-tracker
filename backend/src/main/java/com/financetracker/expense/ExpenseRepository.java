package com.financetracker.expense;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

// Deliberately extends Repository (not JpaRepository/CrudRepository) so no method that
// fetches by id alone is exposed; every query must explicitly filter by userId.
public interface ExpenseRepository extends Repository<Expense, UUID> {

    Expense save(Expense expense);

    Expense saveAndFlush(Expense expense);

    Optional<Expense> findByIdAndUserId(UUID id, UUID userId);

    long deleteByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndCategoryId(UUID userId, UUID categoryId);

    long countByUserId(UUID userId);

    Page<Expense> findByUserIdAndExpenseDateBetween(
            UUID userId,
            LocalDate from,
            LocalDate to,
            Pageable pageable);

    Page<Expense> findByUserIdAndCategoryIdAndExpenseDateBetween(
            UUID userId,
            UUID categoryId,
            LocalDate from,
            LocalDate to,
            Pageable pageable);
}


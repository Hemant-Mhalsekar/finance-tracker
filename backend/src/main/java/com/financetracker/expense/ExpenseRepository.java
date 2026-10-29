package com.financetracker.expense;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

// Deliberately extends Repository (not JpaRepository/CrudRepository) so no method that
// fetches by id alone is exposed; every query must explicitly filter by userId.
public interface ExpenseRepository extends Repository<Expense, UUID> {

    Expense save(Expense expense);

    Optional<Expense> findByIdAndUserId(UUID id, UUID userId);

    long deleteByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndCategoryId(UUID userId, UUID categoryId);
}


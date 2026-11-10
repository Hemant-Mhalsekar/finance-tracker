package com.financetracker.expense;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

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

    // Aggregated in the database so a report never loads individual expenses.
    // The join also checks the user, matching the composite foreign key.
    @Query("""
            select new com.financetracker.expense.CategorySpend(
                e.categoryId, c.name, c.archived, e.categoryType, sum(e.amountPaise), count(e))
            from Expense e join Category c on c.id = e.categoryId and c.userId = e.userId
            where e.userId = :userId and e.expenseDate between :from and :to
            group by e.categoryId, c.name, c.archived, e.categoryType
            order by sum(e.amountPaise) desc, c.name asc
            """)
    List<CategorySpend> sumByCategory(
            @Param("userId") UUID userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("select max(e.createdAt) from Expense e where e.userId = :userId")
    Optional<OffsetDateTime> findLastCreatedAt(@Param("userId") UUID userId);
}

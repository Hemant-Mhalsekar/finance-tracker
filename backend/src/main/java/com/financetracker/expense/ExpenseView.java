package com.financetracker.expense;

import java.time.LocalDate;
import java.util.UUID;

import com.financetracker.common.BudgetType;

public record ExpenseView(
        UUID id,
        UUID categoryId,
        BudgetType type,
        long amountPaise,
        LocalDate expenseDate,
        String description) {

    public static ExpenseView from(Expense expense) {
        return new ExpenseView(
                expense.getId(),
                expense.getCategoryId(),
                expense.getCategoryType(),
                expense.getAmountPaise(),
                expense.getExpenseDate(),
                expense.getDescription());
    }
}


package com.financetracker.expense;

import java.util.UUID;

import com.financetracker.common.BudgetType;

// One row per (category, logged type). "Other" can appear once per type because each of its
// expenses carries the type chosen when it was logged.
public record CategorySpend(
        UUID categoryId,
        String name,
        boolean archived,
        BudgetType type,
        long spentPaise,
        long expenseCount) {
}

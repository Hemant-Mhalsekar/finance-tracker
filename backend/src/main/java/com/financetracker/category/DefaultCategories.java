package com.financetracker.category;

import java.util.List;

import com.financetracker.common.BudgetType;

public final class DefaultCategories {

    public record DefaultCategory(String name, BudgetType type) {
    }

    private static final List<DefaultCategory> DEFAULTS = List.of(
            new DefaultCategory("Rent", BudgetType.NEED),
            new DefaultCategory("Groceries", BudgetType.NEED),
            new DefaultCategory("Transport", BudgetType.NEED),
            new DefaultCategory("Utilities and Bills", BudgetType.NEED),
            new DefaultCategory("Health", BudgetType.NEED),
            new DefaultCategory("EMI and Loans", BudgetType.NEED),
            new DefaultCategory("Dining", BudgetType.WANT),
            new DefaultCategory("Shopping", BudgetType.WANT),
            new DefaultCategory("Entertainment", BudgetType.WANT),
            new DefaultCategory("Subscriptions", BudgetType.WANT),
            new DefaultCategory("Investments", BudgetType.INVESTMENT),
            new DefaultCategory("Courses and Learning", BudgetType.INVESTMENT),
            // NULL type means the user chooses the type per expense.
            new DefaultCategory("Other", null));

    private DefaultCategories() {
    }

    public static List<DefaultCategory> all() {
        return DEFAULTS;
    }
}


package com.financetracker.expense;

import java.util.List;

public record ExpensePage(
        List<ExpenseView> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}


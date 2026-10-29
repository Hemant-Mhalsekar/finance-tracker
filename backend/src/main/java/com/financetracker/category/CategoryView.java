package com.financetracker.category;

import java.util.UUID;

import com.financetracker.common.BudgetType;

// type is null for the protected Other category.
public record CategoryView(UUID id, String name, BudgetType type, boolean archived) {

    public static CategoryView from(Category category) {
        return new CategoryView(
                category.getId(),
                category.getName(),
                category.getType(),
                category.isArchived());
    }
}


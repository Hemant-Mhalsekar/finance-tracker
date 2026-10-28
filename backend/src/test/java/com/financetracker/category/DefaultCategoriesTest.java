package com.financetracker.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.financetracker.category.DefaultCategories.DefaultCategory;
import com.financetracker.common.BudgetType;

class DefaultCategoriesTest {

    @Test
    void defaultCategoriesHaveExpectedOrderTypesUniquenessAndImmutability() {
        List<DefaultCategory> categories = DefaultCategories.all();

        assertEquals(13, categories.size());

        List<DefaultCategory> expected = List.of(
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
                new DefaultCategory("Other", null));
        assertEquals(expected, categories);

        Set<String> lowerNames = categories.stream()
                .map(c -> c.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        assertEquals(13, lowerNames.size());

        for (DefaultCategory c : categories) {
            if ("Other".equals(c.name())) {
                assertNull(c.type());
            } else {
                assertNotNull(c.type());
            }
        }

        assertThrows(
                UnsupportedOperationException.class,
                () -> categories.add(new DefaultCategory("Extra", BudgetType.NEED)));
    }
}


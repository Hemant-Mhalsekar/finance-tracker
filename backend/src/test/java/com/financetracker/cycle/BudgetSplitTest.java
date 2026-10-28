package com.financetracker.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class BudgetSplitTest {

    @Test
    void exactSplits() {
        assertEquals(new BudgetSplit(2_500_000, 1_500_000, 1_000_000), BudgetSplit.of(5_000_000));
        assertEquals(new BudgetSplit(50, 30, 20), BudgetSplit.of(100));
        assertEquals(new BudgetSplit(166, 99, 68), BudgetSplit.of(333));
        assertEquals(new BudgetSplit(0, 0, 1), BudgetSplit.of(1));
    }

    @Test
    void randomSalariesSumExactlyAndAreNonNegative() {
        Random random = new Random(42L);
        for (int i = 0; i < 10_000; i++) {
            long salary = 1L + (Math.abs(random.nextLong()) % 100_000_000_000L);
            int needsPct = random.nextInt(101);
            int wantsPct = random.nextInt(101 - needsPct);
            int savingsPct = 100 - needsPct - wantsPct;

            BudgetSplit split = BudgetSplit.of(salary, needsPct, wantsPct, savingsPct);
            assertTrue(split.needsPaise() >= 0);
            assertTrue(split.wantsPaise() >= 0);
            assertTrue(split.savingsPaise() >= 0);
            assertEquals(salary, split.totalPaise());
            assertEquals(salary, split.needsPaise() + split.wantsPaise() + split.savingsPaise());
        }
    }

    @Test
    void invalidInputsThrow() {
        assertThrows(IllegalArgumentException.class, () -> BudgetSplit.of(0));
        assertThrows(IllegalArgumentException.class, () -> BudgetSplit.of(-1));
        assertThrows(IllegalArgumentException.class, () -> BudgetSplit.of(100, 50, 30, 19));
        assertThrows(IllegalArgumentException.class, () -> BudgetSplit.of(100, 50, 30, 21));
        assertThrows(IllegalArgumentException.class, () -> BudgetSplit.of(100, -10, 60, 50));
        assertThrows(IllegalArgumentException.class, () -> BudgetSplit.of(100, 101, 0, -1));
        assertThrows(ArithmeticException.class, () -> BudgetSplit.of(Long.MAX_VALUE));
    }
}


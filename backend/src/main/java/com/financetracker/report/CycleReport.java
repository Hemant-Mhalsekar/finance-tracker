package com.financetracker.report;

import java.time.LocalDate;
import java.util.List;

import com.financetracker.cycle.CycleView;
import com.financetracker.expense.CategorySpend;

// All money in paise. Negative values are meaningful: a negative remainingPaise is an overspend
// and a negative savedPaise is a deficit. They are never clamped to zero.
public record CycleReport(
        CycleView cycle,
        Days days,
        Budget needs,
        Budget wants,
        Savings savings,
        // Every logged expense in the cycle, investments included.
        long totalLoggedPaise,
        boolean spendingExceedsSalary,
        long expenseCount,
        List<CategorySpend> categories,
        // Across all of the user's expenses, by the date they were logged (not the expense date).
        LocalDate lastExpenseLoggedOn,
        Long daysSinceLastExpenseLogged) {

    // For a past cycle, elapsed equals total and remaining is 0.
    public record Days(long total, long elapsed, long remaining) {
    }

    // overspent is true whenever spent exceeds allocated, even if savings cover it.
    public record Budget(long allocatedPaise, long spentPaise, long remainingPaise, boolean overspent) {
    }

    // savedPaise = salary - needs spent - wants spent, so unspent money counts as saved.
    // investedPaise is a breakdown inside savings, not subtracted from it.
    public record Savings(long plannedPaise, long savedPaise, long investedPaise, boolean deficit) {
    }
}

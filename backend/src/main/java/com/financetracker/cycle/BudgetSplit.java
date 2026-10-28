package com.financetracker.cycle;

public record BudgetSplit(long needsPaise, long wantsPaise, long savingsPaise) {

    public static BudgetSplit of(long salaryPaise) {
        return of(salaryPaise, 50, 30, 20);
    }

    public static BudgetSplit of(long salaryPaise, int needsPct, int wantsPct, int savingsPct) {
        if (salaryPaise <= 0) {
            throw new IllegalArgumentException("salaryPaise must be greater than 0, got: " + salaryPaise);
        }
        if (needsPct < 0 || needsPct > 100 || wantsPct < 0 || wantsPct > 100 || savingsPct < 0 || savingsPct > 100) {
            throw new IllegalArgumentException("Each percentage must be between 0 and 100");
        }
        if (needsPct + wantsPct + savingsPct != 100) {
            throw new IllegalArgumentException("Percentages must add up to 100");
        }

        // Floor division for needs and wants; savings gets the remainder so the three always sum to salaryPaise.
        long needs = Math.multiplyExact(salaryPaise, (long) needsPct) / 100;
        long wants = Math.multiplyExact(salaryPaise, (long) wantsPct) / 100;
        long savings = salaryPaise - needs - wants;
        return new BudgetSplit(needs, wants, savings);
    }

    public long totalPaise() {
        return needsPaise + wantsPaise + savingsPaise;
    }
}


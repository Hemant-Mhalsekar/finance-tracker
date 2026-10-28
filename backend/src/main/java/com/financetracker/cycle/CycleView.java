package com.financetracker.cycle;

import java.time.LocalDate;

public record CycleView(
        LocalDate periodStart,
        LocalDate periodEnd,
        long salaryPaise,
        long needsPaise,
        long wantsPaise,
        long savingsPaise) {

    public static CycleView from(Cycle cycle) {
        return new CycleView(
                cycle.getPeriodStart(),
                cycle.getPeriodEnd(),
                cycle.getSalaryPaise(),
                cycle.getNeedsPaise(),
                cycle.getWantsPaise(),
                cycle.getSavingsPaise());
    }
}


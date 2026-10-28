package com.financetracker.cycle;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

public final class CycleCalculator {

    private CycleCalculator() {
    }

    // Clamps payDay to the month's length when the month is shorter (e.g. payDay 31 in February becomes Feb 28/29).
    public static LocalDate payDateInMonth(YearMonth month, int payDay) {
        Objects.requireNonNull(month, "month must not be null");
        validatePayDay(payDay);
        int day = Math.min(payDay, month.lengthOfMonth());
        return month.atDay(day);
    }

    public static CyclePeriod containing(LocalDate date, int payDay) {
        Objects.requireNonNull(date, "date must not be null");
        validatePayDay(payDay);

        YearMonth month = YearMonth.from(date);
        LocalDate payDateThisMonth = payDateInMonth(month, payDay);

        LocalDate start;
        LocalDate nextPayDate;
        if (!date.isBefore(payDateThisMonth)) {
            start = payDateThisMonth;
            nextPayDate = payDateInMonth(month.plusMonths(1), payDay);
        } else {
            start = payDateInMonth(month.minusMonths(1), payDay);
            nextPayDate = payDateThisMonth;
        }
        return new CyclePeriod(start, nextPayDate.minusDays(1));
    }

    // Builds a transition cycle starting on an arbitrary date (not necessarily a pay date) and
    // ending the day before the next pay date strictly after start. It can be as short as 1 day
    // if start is the day before the next pay date.
    public static CyclePeriod startingOn(LocalDate start, int payDay) {
        Objects.requireNonNull(start, "start must not be null");
        validatePayDay(payDay);

        YearMonth month = YearMonth.from(start);
        LocalDate payDateThisMonth = payDateInMonth(month, payDay);
        LocalDate firstPayDateAfterStart = payDateThisMonth.isAfter(start)
                ? payDateThisMonth
                : payDateInMonth(month.plusMonths(1), payDay);

        return new CyclePeriod(start, firstPayDateAfterStart.minusDays(1));
    }

    public static CyclePeriod nextAfter(CyclePeriod previous, int payDay) {
        Objects.requireNonNull(previous, "previous must not be null");
        return startingOn(previous.end().plusDays(1), payDay);
    }

    private static void validatePayDay(int payDay) {
        if (payDay < 1 || payDay > 31) {
            throw new IllegalArgumentException("payDay must be between 1 and 31, got: " + payDay);
        }
    }
}


package com.financetracker.cycle;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record CyclePeriod(LocalDate start, LocalDate end) {

    public CyclePeriod {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end (" + end + ") must not be before start (" + start + ")");
        }
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(start) && !date.isAfter(end);
    }

    // Inclusive of both start and end.
    public long lengthInDays() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }
}


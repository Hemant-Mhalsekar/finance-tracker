package com.financetracker.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class CycleCalculatorTest {

    @Test
    void payDay1() {
        CyclePeriod oct = new CyclePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(oct, CycleCalculator.containing(LocalDate.of(2026, 10, 1), 1));
        assertEquals(oct, CycleCalculator.containing(LocalDate.of(2026, 10, 15), 1));
        assertEquals(oct, CycleCalculator.containing(LocalDate.of(2026, 10, 31), 1));

        CyclePeriod nov = new CyclePeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        assertEquals(nov, CycleCalculator.containing(LocalDate.of(2026, 11, 1), 1));
    }

    @Test
    void payDay25() {
        assertEquals(
                new CyclePeriod(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 24)),
                CycleCalculator.containing(LocalDate.of(2026, 10, 24), 25));
        assertEquals(
                new CyclePeriod(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24)),
                CycleCalculator.containing(LocalDate.of(2026, 10, 25), 25));

        CyclePeriod yearBoundary = new CyclePeriod(LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 24));
        assertEquals(yearBoundary, CycleCalculator.containing(LocalDate.of(2026, 12, 31), 25));
        assertEquals(yearBoundary, CycleCalculator.containing(LocalDate.of(2027, 1, 1), 25));
    }

    @Test
    void payDay31InNonLeapYear2027() {
        assertEquals(
                new CyclePeriod(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 27)),
                CycleCalculator.containing(LocalDate.of(2027, 2, 15), 31));

        CyclePeriod febToMar = new CyclePeriod(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 30));
        assertEquals(febToMar, CycleCalculator.containing(LocalDate.of(2027, 2, 28), 31));
        assertEquals(febToMar, CycleCalculator.containing(LocalDate.of(2027, 3, 30), 31));

        assertEquals(
                new CyclePeriod(LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 29)),
                CycleCalculator.containing(LocalDate.of(2027, 3, 31), 31));
        assertEquals(
                new CyclePeriod(LocalDate.of(2027, 4, 30), LocalDate.of(2027, 5, 30)),
                CycleCalculator.containing(LocalDate.of(2027, 4, 30), 31));
    }

    @Test
    void payDay31InLeapYear2028() {
        assertEquals(
                new CyclePeriod(LocalDate.of(2028, 1, 31), LocalDate.of(2028, 2, 28)),
                CycleCalculator.containing(LocalDate.of(2028, 2, 28), 31));
        assertEquals(
                new CyclePeriod(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 30)),
                CycleCalculator.containing(LocalDate.of(2028, 2, 29), 31));
    }

    @Test
    void payDay30In2027() {
        assertEquals(
                new CyclePeriod(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 29)),
                CycleCalculator.containing(LocalDate.of(2027, 2, 28), 30));
        assertEquals(
                new CyclePeriod(LocalDate.of(2027, 3, 30), LocalDate.of(2027, 4, 29)),
                CycleCalculator.containing(LocalDate.of(2027, 3, 30), 30));
    }

    @Test
    void payDay29In2027() {
        assertEquals(
                new CyclePeriod(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 28)),
                CycleCalculator.containing(LocalDate.of(2027, 2, 28), 29));
    }

    @Test
    void startingOnTransition() {
        assertEquals(
                new CyclePeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 24)),
                CycleCalculator.startingOn(LocalDate.of(2026, 11, 1), 25));

        CyclePeriod oneDay = CycleCalculator.startingOn(LocalDate.of(2026, 11, 24), 25);
        assertEquals(new CyclePeriod(LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 24)), oneDay);
        assertEquals(1, oneDay.lengthInDays());

        assertEquals(
                new CyclePeriod(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24)),
                CycleCalculator.startingOn(LocalDate.of(2026, 10, 25), 25));
    }

    @Test
    void nextAfter() {
        CyclePeriod previous = new CyclePeriod(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24));
        assertEquals(
                new CyclePeriod(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 24)),
                CycleCalculator.nextAfter(previous, 25));
    }

    @Test
    void propertiesAcrossAllPayDaysAndDates() {
        LocalDate firstDate = LocalDate.of(2026, 1, 1);
        LocalDate lastDate = LocalDate.of(2029, 12, 31);

        for (int payDay = 1; payDay <= 31; payDay++) {
            CyclePeriod previousCycle = null;
            for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
                CyclePeriod cycle = CycleCalculator.containing(date, payDay);

                assertTrue(cycle.contains(date));
                assertEquals(
                        CycleCalculator.payDateInMonth(YearMonth.from(cycle.start()), payDay),
                        cycle.start());
                long len = cycle.lengthInDays();
                assertTrue(len >= 28 && len <= 31, "unexpected length " + len + " for " + cycle);

                if (previousCycle != null && !cycle.equals(previousCycle)) {
                    assertEquals(CycleCalculator.nextAfter(previousCycle, payDay), cycle);
                    assertEquals(previousCycle.end().plusDays(1), date);
                    assertEquals(cycle.start(), date);
                }
                previousCycle = cycle;
            }
        }
    }

    @Test
    void invalidInputsThrow() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        assertThrows(IllegalArgumentException.class, () -> CycleCalculator.containing(d, 0));
        assertThrows(IllegalArgumentException.class, () -> CycleCalculator.containing(d, 32));
        assertThrows(IllegalArgumentException.class, () -> CycleCalculator.startingOn(d, 0));
        assertThrows(IllegalArgumentException.class, () -> CycleCalculator.startingOn(d, 32));
        assertThrows(IllegalArgumentException.class, () -> CycleCalculator.payDateInMonth(YearMonth.of(2026, 10), 0));
        assertThrows(IllegalArgumentException.class, () -> CycleCalculator.payDateInMonth(YearMonth.of(2026, 10), 32));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CyclePeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 4)));

        CyclePeriod p = new CyclePeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertFalse(p.contains(LocalDate.of(2026, 9, 30)));
        assertFalse(p.contains(LocalDate.of(2026, 11, 1)));
    }
}


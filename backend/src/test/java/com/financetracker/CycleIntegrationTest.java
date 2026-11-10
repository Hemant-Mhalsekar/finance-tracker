package com.financetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.financetracker.cycle.BudgetSplit;
import com.financetracker.cycle.CycleCalculator;
import com.financetracker.cycle.CyclePeriod;

import tools.jackson.databind.JsonNode;

// Uses the real clock. Time passing is simulated by replacing a user's cycles with older ones
// through JDBC, which is exactly what a user returning after months away looks like.
class CycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private Clock clock;

    private record StoredCycle(LocalDate start, LocalDate end, long salary, long needs, long wants, long savings) {
    }

    // ---- GET /api/cycles/current ----

    @Test
    void currentCycleRequiresAuthAndOnboardingAndIsIdempotent() throws Exception {
        mockMvc.perform(get("/api/cycles/current")).andExpect(status().isUnauthorized());

        String token = registerUserAndGetToken();
        mockMvc.perform(get("/api/cycles/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Complete setup first"));

        onboardUser(token, 5_000_000L, 25);
        CyclePeriod expected = CycleCalculator.containing(today(), 25);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/cycles/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.periodStart").value(expected.start().toString()))
                    .andExpect(jsonPath("$.periodEnd").value(expected.end().toString()))
                    .andExpect(jsonPath("$.salaryPaise").value(5_000_000L));
        }
        assertEquals(1, storedCycles(userId(token)).size());
    }

    @Test
    void rollForwardFillsAGapOfSeveralCyclesContiguouslyCopiesTheBudgetAndIsRaceSafe() throws Exception {
        String token = registerUserAndGetToken();
        onboardUser(token, 5_000_000L, 25);
        UUID userId = userId(token);

        // The user was last seen about six months ago, with a different salary than at onboarding.
        CyclePeriod old = CycleCalculator.containing(today().minusMonths(6), 25);
        replaceCyclesWith(userId, old, 7_000_001L);

        List<String> bodies = runConcurrently(3, () -> {
            MvcResult result = mockMvc.perform(get("/api/cycles/current")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andReturn();
            return result.getResponse().getStatus() + " " + result.getResponse().getContentAsString();
        });
        assertEquals(1, Set.copyOf(bodies).size(), "All concurrent requests must see the same cycle: " + bodies);
        assertTrue(bodies.get(0).startsWith("200 "), bodies.get(0));

        List<StoredCycle> cycles = storedCycles(userId);
        assertEquals(expectedCycleCount(old, 25), cycles.size());
        assertContiguous(cycles);
        assertEquals(old.start(), cycles.get(0).start());
        assertTrue(contains(cycles.get(cycles.size() - 1), today()));

        BudgetSplit split = BudgetSplit.of(7_000_001L);
        for (StoredCycle cycle : cycles) {
            assertTrue(isPayDate(cycle.start(), 25), "Cycle should start on a pay date: " + cycle);
            assertEquals(7_000_001L, cycle.salary());
            assertEquals(split.needsPaise(), cycle.needs());
            assertEquals(split.wantsPaise(), cycle.wants());
            assertEquals(split.savingsPaise(), cycle.savings());
        }
    }

    // ---- PUT /api/cycles/current/salary ----

    @Test
    void salaryChangeUpdatesOnlyTheCurrentCycleIsCopiedForwardAndValidatesInput() throws Exception {
        String token = registerUserAndGetToken();
        // A pay day that is never today, so the current cycle started before today (needed below).
        int payDay = today().getDayOfMonth() <= 14 ? 20 : 5;
        onboardUser(token, 5_000_000L, payDay);
        UUID userId = userId(token);
        replaceCyclesWith(userId, CycleCalculator.containing(today().minusMonths(3), payDay), 5_000_000L);

        BudgetSplit split = BudgetSplit.of(6_000_001L);
        mockMvc.perform(put("/api/cycles/current/salary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salaryPaise\":6000001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value(CycleCalculator.containing(today(), payDay).start().toString()))
                .andExpect(jsonPath("$.salaryPaise").value(6_000_001L))
                .andExpect(jsonPath("$.needsPaise").value(split.needsPaise()))
                .andExpect(jsonPath("$.wantsPaise").value(split.wantsPaise()))
                .andExpect(jsonPath("$.savingsPaise").value(split.savingsPaise()));

        List<StoredCycle> cycles = storedCycles(userId);
        for (int i = 0; i < cycles.size() - 1; i++) {
            assertEquals(5_000_000L, cycles.get(i).salary(), "Past cycles keep their salary");
        }
        assertEquals(6_000_001L, cycles.get(cycles.size() - 1).salary());

        // Simulate the current cycle ending yesterday: the next cycle copies the new salary.
        jdbcTemplate.update(
                "UPDATE cycles SET period_end = ? WHERE user_id = ? AND period_start <= ? AND period_end >= ?",
                today().minusDays(1), userId, today(), today());
        mockMvc.perform(get("/api/cycles/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value(today().toString()))
                .andExpect(jsonPath("$.salaryPaise").value(6_000_001L));

        for (String body : List.of(
                "{\"salaryPaise\":99}",
                "{\"salaryPaise\":1000000001}",
                "{\"salaryPaise\":5000.5}",
                "{}")) {
            mockMvc.perform(put("/api/cycles/current/salary")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---- PUT /api/settings/pay-day ----

    @Test
    void payDayChangeIsScheduledOneFullCycleAheadCanBeCancelledAndValidatesInput() throws Exception {
        String token = registerUserAndGetToken();
        mockMvc.perform(put("/api/settings/pay-day")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payDay\":10}"))
                .andExpect(status().isConflict());

        onboardUser(token, 5_000_000L, 25);
        CyclePeriod current = CycleCalculator.containing(today(), 25);
        LocalDate from = CycleCalculator.nextAfter(current, 25).end().plusDays(1);

        // Sent twice: the second request must give the same answer.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/settings/pay-day")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"payDay\":10}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.payDay").value(25))
                    .andExpect(jsonPath("$.pendingPayDay").value(10))
                    .andExpect(jsonPath("$.pendingPayDayFrom").value(from.toString()));
        }

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payDay").value(25))
                .andExpect(jsonPath("$.pendingPayDay").value(10))
                .andExpect(jsonPath("$.pendingPayDayFrom").value(from.toString()));

        // The current cycle is not touched.
        mockMvc.perform(get("/api/cycles/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value(current.start().toString()))
                .andExpect(jsonPath("$.periodEnd").value(current.end().toString()));

        // Choosing the current pay day again cancels the pending change.
        mockMvc.perform(put("/api/settings/pay-day")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payDay\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payDay").value(25))
                .andExpect(jsonPath("$.pendingPayDay").doesNotExist())
                .andExpect(jsonPath("$.pendingPayDayFrom").doesNotExist());

        for (String body : List.of("{\"payDay\":0}", "{\"payDay\":32}", "{\"payDay\":10.5}", "{}")) {
            mockMvc.perform(put("/api/settings/pay-day")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rollForwardAppliesAPendingPayDayChangeAtTheScheduledCycle() throws Exception {
        int[][] changes = {{25, 10}, {31, 1}, {1, 31}, {29, 28}, {10, 25}, {30, 31}};
        for (int[] change : changes) {
            int oldPayDay = change[0];
            int newPayDay = change[1];
            String label = oldPayDay + " -> " + newPayDay;

            String token = registerUserAndGetToken();
            onboardUser(token, 5_000_000L, oldPayDay);
            UUID userId = userId(token);

            // The change was made eight months ago, during the then-current cycle.
            CyclePeriod old = CycleCalculator.containing(today().minusMonths(8), oldPayDay);
            replaceCyclesWith(userId, old, 5_000_000L);
            LocalDate from = CycleCalculator.nextAfter(old, oldPayDay).end().plusDays(1);
            jdbcTemplate.update("UPDATE users SET pending_pay_day = ?, pending_pay_day_from = ? WHERE id = ?",
                    (short) newPayDay, from, userId);

            MvcResult result = mockMvc.perform(get("/api/cycles/current")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode current = jsonMapper.readTree(result.getResponse().getContentAsString());

            List<StoredCycle> cycles = storedCycles(userId);
            assertContiguous(cycles);
            StoredCycle last = cycles.get(cycles.size() - 1);
            assertTrue(contains(last, today()), label);
            assertEquals(last.start().toString(), current.get("periodStart").asText(), label);

            boolean sawTransition = false;
            for (StoredCycle cycle : cycles) {
                if (cycle.start().isBefore(from)) {
                    // The current cycle and one full cycle after it keep the old pay day.
                    assertTrue(isPayDate(cycle.start(), oldPayDay), label + " old pay day: " + cycle);
                    assertTrue(isPayDate(cycle.end().plusDays(1), oldPayDay), label + " old pay day: " + cycle);
                } else if (cycle.start().equals(from)) {
                    sawTransition = true;
                    assertTrue(isPayDate(cycle.end().plusDays(1), newPayDay), label + " transition: " + cycle);
                } else {
                    assertTrue(isPayDate(cycle.start(), newPayDay), label + " new pay day: " + cycle);
                }
            }
            assertTrue(sawTransition, label + ": no cycle starts on " + from);
            assertEquals(2, cycles.stream().filter(c -> c.start().isBefore(from)).count(), label);

            Short payDay = jdbcTemplate.queryForObject("SELECT pay_day FROM users WHERE id = ?", Short.class, userId);
            assertEquals((short) newPayDay, payDay, label);
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT pending_pay_day FROM users WHERE id = ?", Short.class, userId), label);
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT pending_pay_day_from FROM users WHERE id = ?", LocalDate.class, userId), label);
        }
    }

    // ---- helpers ----

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private UUID userId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void replaceCyclesWith(UUID userId, CyclePeriod period, long salaryPaise) {
        BudgetSplit split = BudgetSplit.of(salaryPaise);
        jdbcTemplate.update("DELETE FROM cycles WHERE user_id = ?", userId);
        jdbcTemplate.update("""
                INSERT INTO cycles (user_id, period_start, period_end, salary_paise,
                                    needs_paise, wants_paise, savings_paise)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                userId, period.start(), period.end(), salaryPaise,
                split.needsPaise(), split.wantsPaise(), split.savingsPaise());
    }

    private List<StoredCycle> storedCycles(UUID userId) {
        return jdbcTemplate.query("""
                SELECT period_start, period_end, salary_paise, needs_paise, wants_paise, savings_paise
                FROM cycles WHERE user_id = ? ORDER BY period_start
                """,
                (rs, rowNum) -> new StoredCycle(
                        rs.getObject("period_start", LocalDate.class),
                        rs.getObject("period_end", LocalDate.class),
                        rs.getLong("salary_paise"),
                        rs.getLong("needs_paise"),
                        rs.getLong("wants_paise"),
                        rs.getLong("savings_paise")),
                userId);
    }

    private int expectedCycleCount(CyclePeriod first, int payDay) {
        int count = 1;
        CyclePeriod period = first;
        while (period.end().isBefore(today())) {
            period = CycleCalculator.nextAfter(period, payDay);
            count++;
        }
        return count;
    }

    private static void assertContiguous(List<StoredCycle> cycles) {
        for (int i = 1; i < cycles.size(); i++) {
            assertEquals(cycles.get(i - 1).end().plusDays(1), cycles.get(i).start(),
                    "Gap or overlap between " + cycles.get(i - 1) + " and " + cycles.get(i));
        }
    }

    private static boolean contains(StoredCycle cycle, LocalDate date) {
        return !date.isBefore(cycle.start()) && !date.isAfter(cycle.end());
    }

    private static boolean isPayDate(LocalDate date, int payDay) {
        return date.equals(CycleCalculator.payDateInMonth(YearMonth.from(date), payDay));
    }

    private static <T> List<T> runConcurrently(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return task.call();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<T> results = new ArrayList<>(threads);
            for (Future<T> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}

package com.financetracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.financetracker.cycle.BudgetSplit;
import com.financetracker.cycle.CycleCalculator;
import com.financetracker.cycle.CyclePeriod;

class CycleReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private Clock clock;

    @Test
    void reportRequiresAuthAndOnboarding() throws Exception {
        mockMvc.perform(get("/api/cycles/current/report")).andExpect(status().isUnauthorized());

        String token = registerUserAndGetToken();
        mockMvc.perform(get("/api/cycles/current/report").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/cycles/report").param("date", today().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void currentReportSumsByTypeAndCategoryFlagsOverspendAndIgnoresOtherCycles() throws Exception {
        String token = registerUserAndGetToken();
        onboardUser(token, 1_000_000L, 25);
        CyclePeriod current = CycleCalculator.containing(today(), 25);
        BudgetSplit split = BudgetSplit.of(1_000_000L);

        // Empty cycle first: everything zero, nothing logged yet.
        mockMvc.perform(get("/api/cycles/current/report").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needs.spentPaise").value(0))
                .andExpect(jsonPath("$.savings.savedPaise").value(1_000_000L))
                .andExpect(jsonPath("$.expenseCount").value(0))
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(jsonPath("$.lastExpenseLoggedOn").doesNotExist())
                .andExpect(jsonPath("$.daysSinceLastExpenseLogged").doesNotExist());

        logExpense(token, "Rent", 600_000L, null, null);
        logExpense(token, "Dining", 350_000L, null, null);
        logExpense(token, "Investments", 100_000L, null, null);
        logExpense(token, "Other", 10_000L, "WANT", null);
        logExpense(token, "Other", 5_000L, "NEED", null);
        // The day before the cycle started: belongs to no stored cycle, so it is not counted.
        logExpense(token, "Rent", 999_999L, null, current.start().minusDays(1));

        long needsSpent = 605_000L;
        long wantsSpent = 360_000L;
        long totalDays = ChronoUnit.DAYS.between(current.start(), current.end()) + 1;
        long elapsed = ChronoUnit.DAYS.between(current.start(), today()) + 1;

        mockMvc.perform(get("/api/cycles/current/report").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycle.periodStart").value(current.start().toString()))
                .andExpect(jsonPath("$.days.total").value(totalDays))
                .andExpect(jsonPath("$.days.elapsed").value(elapsed))
                .andExpect(jsonPath("$.days.remaining").value(totalDays - elapsed))
                .andExpect(jsonPath("$.needs.allocatedPaise").value(split.needsPaise()))
                .andExpect(jsonPath("$.needs.spentPaise").value(needsSpent))
                .andExpect(jsonPath("$.needs.remainingPaise").value(split.needsPaise() - needsSpent))
                .andExpect(jsonPath("$.needs.overspent").value(true))
                .andExpect(jsonPath("$.wants.allocatedPaise").value(split.wantsPaise()))
                .andExpect(jsonPath("$.wants.spentPaise").value(wantsSpent))
                .andExpect(jsonPath("$.wants.remainingPaise").value(split.wantsPaise() - wantsSpent))
                .andExpect(jsonPath("$.wants.overspent").value(true))
                .andExpect(jsonPath("$.savings.plannedPaise").value(split.savingsPaise()))
                .andExpect(jsonPath("$.savings.savedPaise").value(1_000_000L - needsSpent - wantsSpent))
                .andExpect(jsonPath("$.savings.investedPaise").value(100_000L))
                .andExpect(jsonPath("$.savings.deficit").value(false))
                .andExpect(jsonPath("$.totalLoggedPaise").value(1_065_000L))
                .andExpect(jsonPath("$.spendingExceedsSalary").value(true))
                .andExpect(jsonPath("$.expenseCount").value(5))
                // Sorted by amount, highest first. Other appears once per logged type.
                .andExpect(jsonPath("$.categories.length()").value(5))
                .andExpect(jsonPath("$.categories[0].name").value("Rent"))
                .andExpect(jsonPath("$.categories[0].type").value("NEED"))
                .andExpect(jsonPath("$.categories[0].spentPaise").value(600_000L))
                .andExpect(jsonPath("$.categories[0].expenseCount").value(1))
                .andExpect(jsonPath("$.categories[1].name").value("Dining"))
                .andExpect(jsonPath("$.categories[2].name").value("Investments"))
                .andExpect(jsonPath("$.categories[2].type").value("INVESTMENT"))
                .andExpect(jsonPath("$.categories[3].name").value("Other"))
                .andExpect(jsonPath("$.categories[3].type").value("WANT"))
                .andExpect(jsonPath("$.categories[4].name").value("Other"))
                .andExpect(jsonPath("$.categories[4].type").value("NEED"))
                .andExpect(jsonPath("$.lastExpenseLoggedOn").value(today().toString()))
                .andExpect(jsonPath("$.daysSinceLastExpenseLogged").value(0));
    }

    @Test
    void reportShowsADeficitInsteadOfZeroSavingsAndNoFlagsWhenWithinPlan() throws Exception {
        String deficitUser = registerUserAndGetToken();
        onboardUser(deficitUser, 10_000L, 25);
        logExpense(deficitUser, "Rent", 20_000L, null, null);

        mockMvc.perform(get("/api/cycles/current/report").header(HttpHeaders.AUTHORIZATION, bearer(deficitUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings.savedPaise").value(-10_000L))
                .andExpect(jsonPath("$.savings.deficit").value(true))
                .andExpect(jsonPath("$.wants.overspent").value(false));

        String carefulUser = registerUserAndGetToken();
        onboardUser(carefulUser, 1_000_000L, 25);
        logExpense(carefulUser, "Groceries", 100_000L, null, null);
        logExpense(carefulUser, "Investments", 50_000L, null, null);

        mockMvc.perform(get("/api/cycles/current/report").header(HttpHeaders.AUTHORIZATION, bearer(carefulUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needs.overspent").value(false))
                .andExpect(jsonPath("$.wants.overspent").value(false))
                .andExpect(jsonPath("$.savings.savedPaise").value(900_000L))
                .andExpect(jsonPath("$.savings.investedPaise").value(50_000L))
                .andExpect(jsonPath("$.savings.deficit").value(false))
                .andExpect(jsonPath("$.spendingExceedsSalary").value(false));
    }

    @Test
    void reportForDateFindsPastCyclesAndRejectsBadDates() throws Exception {
        String token = registerUserAndGetToken();
        onboardUser(token, 1_000_000L, 25);
        UUID userId = userId(token);

        // The user started three months ago; the roll-forward fills in the cycles since then.
        CyclePeriod old = CycleCalculator.containing(today().minusMonths(3), 25);
        BudgetSplit split = BudgetSplit.of(1_000_000L);
        jdbcTemplate.update("DELETE FROM cycles WHERE user_id = ?", userId);
        jdbcTemplate.update("""
                INSERT INTO cycles (user_id, period_start, period_end, salary_paise,
                                    needs_paise, wants_paise, savings_paise)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                userId, old.start(), old.end(), 1_000_000L,
                split.needsPaise(), split.wantsPaise(), split.savingsPaise());

        logExpense(token, "Groceries", 12_345L, null, old.start());
        logExpense(token, "Groceries", 1_000L, null, old.end());
        logExpense(token, "Groceries", 7L, null, old.end().plusDays(1));

        long totalDays = ChronoUnit.DAYS.between(old.start(), old.end()) + 1;
        mockMvc.perform(get("/api/cycles/report").param("date", old.start().plusDays(3).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycle.periodStart").value(old.start().toString()))
                .andExpect(jsonPath("$.cycle.periodEnd").value(old.end().toString()))
                .andExpect(jsonPath("$.days.total").value(totalDays))
                .andExpect(jsonPath("$.days.elapsed").value(totalDays))
                .andExpect(jsonPath("$.days.remaining").value(0))
                .andExpect(jsonPath("$.needs.spentPaise").value(13_345L))
                .andExpect(jsonPath("$.expenseCount").value(2));

        // Today resolves to the current cycle.
        mockMvc.perform(get("/api/cycles/report").param("date", today().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycle.periodStart").value(CycleCalculator.containing(today(), 25).start().toString()));

        // Before the first cycle and after today there is no stored cycle.
        for (LocalDate date : new LocalDate[] {old.start().minusDays(1), today().plusMonths(2)}) {
            mockMvc.perform(get("/api/cycles/report").param("date", date.toString())
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(get("/api/cycles/report").param("date", "not-a-date")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/cycles/report").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportOnlyCountsTheCallersOwnExpenses() throws Exception {
        String alice = registerUserAndGetToken();
        String bob = registerUserAndGetToken();
        onboardUser(alice, 1_000_000L, 25);
        onboardUser(bob, 1_000_000L, 25);
        logExpense(alice, "Rent", 400_000L, null, null);

        mockMvc.perform(get("/api/cycles/current/report").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenseCount").value(0))
                .andExpect(jsonPath("$.lastExpenseLoggedOn").doesNotExist());
    }

    // ---- helpers ----

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private UUID userId(String token) throws Exception {
        var result = mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void logExpense(String token, String category, long amountPaise, String type, LocalDate date)
            throws Exception {
        UUID categoryId = findCategoryIdByName(token, category);
        StringBuilder body = new StringBuilder()
                .append("{\"amountPaise\":").append(amountPaise)
                .append(",\"categoryId\":\"").append(categoryId).append('"')
                .append(",\"description\":\"Test expense\"");
        if (type != null) {
            body.append(",\"type\":\"").append(type).append('"');
        }
        if (date != null) {
            body.append(",\"expenseDate\":\"").append(date).append('"');
        }
        body.append('}');
        mockMvc.perform(post("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isCreated());
    }
}

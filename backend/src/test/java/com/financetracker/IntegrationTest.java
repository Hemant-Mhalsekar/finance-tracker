package com.financetracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private Clock clock;

    // ---- 1. Auth ----

    @Test
    void authRegisterLoginAndMeFlowCaseInsensitiveDuplicateWrongCredentialsAndMissingToken() throws Exception {
        String email = nextUniqueEmail();
        String tokenFromRegister = registerUserAndGetToken(email, "password123");
        assertNotNull(tokenFromRegister);

        // Registering the same email in uppercase returns 409.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));

        // Login succeeds and returns a working token for /api/me.
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  " + email.toUpperCase() + "  \",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String loginToken = jsonMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        MvcResult meResult = mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();
        JsonNode meJson = jsonMapper.readTree(meResult.getResponse().getContentAsString());
        assertNotNull(UUID.fromString(meJson.get("id").asText()));
        assertTrue(meJson.get("payDay").isNull());

        // Wrong password and unknown email both return 401 with identical JSON bodies.
        String wrongPasswordBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String unknownEmailBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + nextUniqueEmail() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(wrongPasswordBody, unknownEmailBody);

        // Request without a token returns 401 with a JSON body.
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // ---- 2. Refresh tokens ----

    @Test
    void refreshRotatesDetectsReuseHandlesLogoutAndRejectsMissingCookie() throws Exception {
        String email = nextUniqueEmail();
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie firstCookie = extractRefreshCookie(regResult);

        // Refresh rotates the token and issues a new cookie.
        MvcResult refresh1 = mockMvc.perform(post("/api/auth/refresh").cookie(firstCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();
        Cookie secondCookie = extractRefreshCookie(refresh1);

        // Reusing the old refresh token returns 401 and revokes the new token too.
        mockMvc.perform(post("/api/auth/refresh").cookie(firstCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));

        mockMvc.perform(post("/api/auth/refresh").cookie(secondCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));

        // Login again to get a fresh refresh cookie, then logout revokes it.
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie thirdCookie = extractRefreshCookie(loginResult);

        mockMvc.perform(post("/api/auth/logout").cookie(thirdCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(thirdCookie))
                .andExpect(status().isUnauthorized());

        // Refresh with no cookie returns 401.
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void simultaneousRefreshesWithSameTokenProduceOne200AndOne401() throws Exception {
        String email = nextUniqueEmail();
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie cookie = extractRefreshCookie(regResult);

        List<Integer> statuses = runConcurrently(2, () -> mockMvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andReturn()
                .getResponse()
                .getStatus());

        assertEquals(Set.of(200, 401), Set.copyOf(statuses));
    }

    // ---- 3. Onboarding ----

    @Test
    void onboardingCreatesDefaultCategoriesAndCycleAndRejectsSecondAttempt() throws Exception {
        String token = registerUserAndGetToken();
        UUID userId = getUserId(token);

        MvcResult onboardResult = mockMvc.perform(post("/api/onboarding")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salaryPaise\":5000000,\"payDay\":25}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payDay").value(25))
                .andExpect(jsonPath("$.cycle.salaryPaise").value(5_000_000))
                .andExpect(jsonPath("$.cycle.needsPaise").value(2_500_000))
                .andExpect(jsonPath("$.cycle.wantsPaise").value(1_500_000))
                .andExpect(jsonPath("$.cycle.savingsPaise").value(1_000_000))
                .andReturn();
        assertNotNull(onboardResult.getResponse().getContentAsString());

        Integer categoryCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM categories WHERE user_id = ?", Integer.class, userId);
        Integer cycleCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cycles WHERE user_id = ?", Integer.class, userId);
        assertEquals(13, categoryCount);
        assertEquals(1, cycleCount);

        // Onboarding a second time returns 409.
        mockMvc.perform(post("/api/onboarding")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salaryPaise\":5000000,\"payDay\":25}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Setup already completed"));
    }

    @Test
    void simultaneousOnboardingRequestsProduceOne201AndOne409With13CategoriesAnd1Cycle() throws Exception {
        String token = registerUserAndGetToken();
        UUID userId = getUserId(token);

        List<Integer> statuses = runConcurrently(2, () -> mockMvc.perform(post("/api/onboarding")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salaryPaise\":5000000,\"payDay\":25}"))
                .andReturn()
                .getResponse()
                .getStatus());

        assertEquals(Set.of(201, 409), Set.copyOf(statuses));

        Integer categoryCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM categories WHERE user_id = ?", Integer.class, userId);
        Integer cycleCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM cycles WHERE user_id = ?", Integer.class, userId);
        assertEquals(13, categoryCount);
        assertEquals(1, cycleCount);
    }

    // ---- 4. Isolation ----

    @Test
    void userIsolationAcrossCategoriesAndExpensesAndCompositeForeignKeyAtDatabaseLevel() throws Exception {
        String tokenA = registerUserAndGetToken();
        String tokenB = registerUserAndGetToken();
        UUID userBId = getUserId(tokenB);

        onboardUser(tokenA);
        onboardUser(tokenB);

        // User A creates a custom category and an expense.
        MvcResult catAResult = mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A Private Category\",\"type\":\"WANT\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID catAId = UUID.fromString(
                jsonMapper.readTree(catAResult.getResponse().getContentAsString()).get("id").asText());

        MvcResult expAResult = mockMvc.perform(post("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":12300,\"categoryId\":\"" + catAId
                                + "\",\"description\":\"A Secret Expense\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID expAId = UUID.fromString(
                jsonMapper.readTree(expAResult.getResponse().getContentAsString()).get("id").asText());

        UUID catBId = findCategoryIdByName(tokenB, "Dining");

        // User B gets 404 on PATCH and DELETE of A's category.
        mockMvc.perform(patch("/api/categories/" + catAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/categories/" + catAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        // User B gets 404 on PUT and DELETE of A's expense.
        mockMvc.perform(put("/api/expenses/" + expAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":100,\"categoryId\":\"" + catBId
                                + "\",\"description\":\"Hacked\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/expenses/" + expAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());

        // User B's category and expense lists do not contain A's data.
        String bCategories = mockMvc.perform(get("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(!bCategories.contains(catAId.toString()));
        assertTrue(!bCategories.contains("A Private Category"));

        String bExpenses = mockMvc.perform(get("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(!bExpenses.contains(expAId.toString()));

        // User B cannot create an expense using A's category (404).
        mockMvc.perform(post("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":500,\"categoryId\":\"" + catAId
                                + "\",\"description\":\"Cross user\"}"))
                .andExpect(status().isNotFound());

        // Database-level composite FK check: inserting an expense for B with A's categoryId fails.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO expenses (user_id, category_id, category_type, amount_paise, expense_date, description) "
                                + "VALUES (?, ?, 'WANT', 500, CURRENT_DATE, 'FK violation')",
                        userBId,
                        catAId));
    }

    // ---- 5. Categories ----

    @Test
    void categoriesEnforceUniqueNamesConcurrentCreatesProtectedOtherAndNonEmptyDelete() throws Exception {
        String token = registerUserAndGetToken();
        onboardUser(token);

        // Duplicate name ignoring case returns 409.
        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Books\",\"type\":\"WANT\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"BOOKS\",\"type\":\"NEED\"}"))
                .andExpect(status().isConflict());

        // Two simultaneous creates of the same name give one 201 and one 409, never 500.
        List<Integer> concurrentStatuses = runConcurrently(2, () -> mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ConcurrentCat\",\"type\":\"WANT\"}"))
                .andReturn()
                .getResponse()
                .getStatus());
        assertEquals(Set.of(201, 409), Set.copyOf(concurrentStatuses));

        // Deleting a category that has expenses returns 409 and leaves the category intact.
        UUID diningId = findCategoryIdByName(token, "Dining");
        mockMvc.perform(post("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":1000,\"categoryId\":\"" + diningId
                                + "\",\"description\":\"Pizza\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/categories/" + diningId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This category has expenses. Archive it instead."));

        assertNotNull(findCategoryIdByName(token, "Dining"));

        // Protected Other category cannot be modified or deleted.
        UUID otherId = findCategoryIdByName(token, "Other");
        mockMvc.perform(patch("/api/categories/" + otherId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RenamedOther\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This category cannot be changed"));

        mockMvc.perform(delete("/api/categories/" + otherId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This category cannot be changed"));
    }

    // ---- 6. Expenses ----

    @Test
    void expensesTypeSnapshotDateRulesPaginationFilteringAndDecimalRejection() throws Exception {
        String token = registerUserAndGetToken();
        onboardUser(token);

        LocalDate today = LocalDate.now(clock);
        UUID diningId = findCategoryIdByName(token, "Dining"); // starts as WANT

        // Create an expense in Dining (type WANT).
        MvcResult exp1Result = mockMvc.perform(post("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":2000,\"categoryId\":\"" + diningId
                                + "\",\"description\":\"Lunch\",\"expenseDate\":\"" + today.minusDays(2) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WANT"))
                .andReturn();
        UUID exp1Id = UUID.fromString(
                jsonMapper.readTree(exp1Result.getResponse().getContentAsString()).get("id").asText());

        // Change Dining's type from WANT to NEED.
        mockMvc.perform(patch("/api/categories/" + diningId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"NEED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("NEED"));

        // Editing the first expense without changing its category preserves its snapshotted WANT type.
        mockMvc.perform(put("/api/expenses/" + exp1Id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":2500,\"categoryId\":\"" + diningId
                                + "\",\"description\":\"Lunch updated\",\"expenseDate\":\"" + today.minusDays(2) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WANT"))
                .andExpect(jsonPath("$.amountPaise").value(2500));

        // A new expense in Dining gets the new NEED type, and today's date is accepted.
        mockMvc.perform(post("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":3000,\"categoryId\":\"" + diningId
                                + "\",\"description\":\"Dinner today\",\"expenseDate\":\"" + today + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("NEED"))
                .andExpect(jsonPath("$.expenseDate").value(today.toString()));

        // A future date (tomorrow in Asia/Kolkata) is rejected with 400.
        mockMvc.perform(post("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":1000,\"categoryId\":\"" + diningId
                                + "\",\"description\":\"Future\",\"expenseDate\":\"" + today.plusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Date cannot be in the future"));

        // Add one more expense in a different category and date for filtering & pagination checks.
        UUID rentId = findCategoryIdByName(token, "Rent");
        mockMvc.perform(post("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":50000,\"categoryId\":\"" + rentId
                                + "\",\"description\":\"Rent payment\",\"expenseDate\":\"" + today.minusDays(1) + "\"}"))
                .andExpect(status().isCreated());

        // Pagination: size=2, page=0 returns 2 items (today and yesterday) out of 3 total.
        mockMvc.perform(get("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].description").value("Dinner today"))
                .andExpect(jsonPath("$.items[1].description").value("Rent payment"));

        // Date filtering: from=today, to=today returns only today's expense.
        mockMvc.perform(get("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Dinner today"));

        // Category filtering: categoryId=rentId returns only the rent expense.
        mockMvc.perform(get("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("categoryId", rentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Rent payment"));

        // Amounts with decimals are rejected with 400.
        mockMvc.perform(post("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":123.45,\"categoryId\":\"" + diningId
                                + "\",\"description\":\"Fractional\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    // ---- 7. Database rules ----

    @Test
    void deletingUserCascadesCleanlyAndRowLevelSecurityIsEnabledOnAllApplicationTables() throws Exception {
        String token = registerUserAndGetToken();
        UUID userId = getUserId(token);
        onboardUser(token);

        UUID diningId = findCategoryIdByName(token, "Dining");
        mockMvc.perform(post("/api/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":1500,\"categoryId\":\"" + diningId
                                + "\",\"description\":\"Snack\"}"))
                .andExpect(status().isCreated());

        // Delete the user directly; ON DELETE CASCADE + NO ACTION composite FK must allow this in one statement.
        int deletedUsers = jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        assertEquals(1, deletedUsers);

        assertEquals(0, countForUser("categories", userId));
        assertEquals(0, countForUser("expenses", userId));
        assertEquals(0, countForUser("cycles", userId));
        assertEquals(0, countForUser("refresh_tokens", userId));

        // Every table in public except flyway_schema_history must have RLS enabled.
        List<String> tablesWithoutRls = jdbcTemplate.queryForList(
                "SELECT c.relname FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relkind = 'r' "
                        + "AND c.relname <> 'flyway_schema_history' "
                        + "AND NOT c.relrowsecurity",
                String.class);
        assertEquals(List.of(), tablesWithoutRls);

        Integer rlsTableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'public' AND c.relkind = 'r' "
                        + "AND c.relname <> 'flyway_schema_history' "
                        + "AND c.relrowsecurity",
                Integer.class);
        assertEquals(6, rlsTableCount);
    }

    // ---- helpers ----

    private UUID getUserId(String accessToken) throws Exception {
        MvcResult meResult = mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(
                jsonMapper.readTree(meResult.getResponse().getContentAsString()).get("id").asText());
    }

    private int countForUser(String table, UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
        return count != null ? count : 0;
    }

    private static Cookie extractRefreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertNotNull(cookie, "Expected refresh_token cookie to be set");
        return cookie;
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


package com.financetracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "JWT_SECRET=test-jwt-secret-key-at-least-thirty-two-bytes-long",
        "DB_URL=jdbc:postgresql://localhost:5432/unused",
        "DB_USER=unused",
        "DB_PASSWORD=unused",
        "COOKIE_SECURE=false"
})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final AtomicLong EMAIL_COUNTER = new AtomicLong();

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected JsonMapper jsonMapper;

    protected String nextUniqueEmail() {
        return "user-" + EMAIL_COUNTER.incrementAndGet() + "-" + UUID.randomUUID() + "@example.com";
    }

    protected String registerUserAndGetToken() throws Exception {
        return registerUserAndGetToken(nextUniqueEmail(), "password123");
    }

    protected String registerUserAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new AuthPayload(email, password))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = jsonMapper.readTree(result.getResponse().getContentAsString());
        return root.get("accessToken").asText();
    }

    protected void onboardUser(String accessToken) throws Exception {
        onboardUser(accessToken, 5_000_000L, 25);
    }

    protected void onboardUser(String accessToken, long salaryPaise, int payDay) throws Exception {
        mockMvc.perform(post("/api/onboarding")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salaryPaise\":" + salaryPaise + ",\"payDay\":" + payDay + "}"))
                .andExpect(status().isCreated());
    }

    protected UUID findCategoryIdByName(String accessToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/categories")
                        .param("includeArchived", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode categories = jsonMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode category : categories) {
            if (name.equalsIgnoreCase(category.get("name").asText())) {
                return UUID.fromString(category.get("id").asText());
            }
        }
        throw new IllegalStateException("Category not found: " + name);
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record AuthPayload(String email, String password) {
    }
}


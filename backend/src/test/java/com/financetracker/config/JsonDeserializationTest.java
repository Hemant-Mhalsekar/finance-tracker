package com.financetracker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.financetracker.onboarding.OnboardingController.OnboardingRequest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@JsonTest
class JsonDeserializationTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void acceptsWholeNumbersForIntegerFields() {
        OnboardingRequest request = jsonMapper.readValue(
                "{\"salaryPaise\":123456,\"payDay\":25}",
                OnboardingRequest.class);

        assertEquals(123456L, request.salaryPaise());
        assertEquals(25, request.payDay());
    }

    @Test
    void rejectsFractionalNumbersForIntegerFields() {
        assertThrows(
                JacksonException.class,
                () -> jsonMapper.readValue("{\"salaryPaise\":123456.78,\"payDay\":25}", OnboardingRequest.class));
        assertThrows(
                JacksonException.class,
                () -> jsonMapper.readValue("{\"salaryPaise\":123456,\"payDay\":25.5}", OnboardingRequest.class));
    }
}


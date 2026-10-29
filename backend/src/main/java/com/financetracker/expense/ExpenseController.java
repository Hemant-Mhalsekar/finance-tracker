package com.financetracker.expense;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.financetracker.common.BudgetType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    public record ExpenseRequest(
            @NotNull(message = "Amount is required")
            Long amountPaise,

            @NotNull(message = "Category is required")
            UUID categoryId,

            @NotBlank(message = "Description is required")
            @Size(max = 200, message = "Description must be at most 200 characters")
            String description,

            LocalDate expenseDate,

            BudgetType type) {

        public ExpenseRequest {
            if (description != null) {
                description = description.trim();
            }
        }
    }

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ExpenseView> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ExpenseRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ExpenseView created = expenseService.create(
                userId,
                request.amountPaise(),
                request.categoryId(),
                request.description(),
                request.expenseDate(),
                request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ExpenseView update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return expenseService.update(
                userId,
                id,
                request.amountPaise(),
                request.categoryId(),
                request.description(),
                request.expenseDate(),
                request.type());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        expenseService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ExpensePage list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return expenseService.list(userId, from, to, categoryId, page, size);
    }
}


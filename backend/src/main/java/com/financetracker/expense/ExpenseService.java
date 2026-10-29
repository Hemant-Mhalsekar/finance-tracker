package com.financetracker.expense;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.BudgetType;

@Service
public class ExpenseService {

    private static final long MAX_EXPENSES_PER_USER = 20_000;
    private static final long MIN_AMOUNT_PAISE = 1;
    private static final long MAX_AMOUNT_PAISE = 10_000_000_000L;
    private static final LocalDate MIN_EXPENSE_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate DEFAULT_FROM_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate DEFAULT_TO_DATE = LocalDate.of(9999, 12, 31);
    private static final Sort EXPENSE_SORT = Sort.by(
            Sort.Order.desc("expenseDate"),
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"));

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            CategoryRepository categoryRepository,
            Clock clock) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
    }

    @Transactional
    public ExpenseView create(
            UUID userId,
            Long amountPaise,
            UUID categoryId,
            String description,
            LocalDate expenseDate,
            BudgetType type) {
        ValidatedFields validated = validateFields(amountPaise, categoryId, description, expenseDate);

        if (expenseRepository.countByUserId(userId) >= MAX_EXPENSES_PER_USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Expense limit reached");
        }

        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(ExpenseService::categoryNotFound);
        if (category.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This category is archived");
        }

        BudgetType resolvedType = resolveType(category, type);
        Expense expense = expenseRepository.saveAndFlush(new Expense(
                userId,
                categoryId,
                resolvedType,
                validated.amountPaise(),
                validated.expenseDate(),
                validated.description()));
        return ExpenseView.from(expense);
    }

    @Transactional
    public ExpenseView update(
            UUID userId,
            UUID id,
            Long amountPaise,
            UUID categoryId,
            String description,
            LocalDate expenseDate,
            BudgetType type) {
        ValidatedFields validated = validateFields(amountPaise, categoryId, description, expenseDate);

        Expense expense = expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(ExpenseService::expenseNotFound);
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(ExpenseService::categoryNotFound);

        boolean sameCategory = categoryId.equals(expense.getCategoryId());
        if (category.isArchived() && !sameCategory) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This category is archived");
        }

        BudgetType resolvedType;
        if (sameCategory && category.getType() != null) {
            // Keep the expense's existing snapshotted type when the category is unchanged.
            if (type != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Type can only be set for categories without a fixed type");
            }
            resolvedType = expense.getCategoryType();
        } else {
            resolvedType = resolveType(category, type);
        }

        expense.setCategoryId(categoryId);
        expense.setCategoryType(resolvedType);
        expense.setAmountPaise(validated.amountPaise());
        expense.setExpenseDate(validated.expenseDate());
        expense.setDescription(validated.description());

        Expense saved = expenseRepository.saveAndFlush(expense);
        return ExpenseView.from(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        if (expenseRepository.deleteByIdAndUserId(id, userId) == 0) {
            throw expenseNotFound();
        }
    }

    public ExpensePage list(
            UUID userId,
            LocalDate from,
            LocalDate to,
            UUID categoryId,
            int page,
            int size) {
        LocalDate effectiveFrom = from != null ? from : DEFAULT_FROM_DATE;
        LocalDate effectiveTo = to != null ? to : DEFAULT_TO_DATE;

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after to date");
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be at least 0");
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size, EXPENSE_SORT);
        Page<Expense> result = categoryId != null
                ? expenseRepository.findByUserIdAndCategoryIdAndExpenseDateBetween(
                        userId, categoryId, effectiveFrom, effectiveTo, pageable)
                : expenseRepository.findByUserIdAndExpenseDateBetween(
                        userId, effectiveFrom, effectiveTo, pageable);

        List<ExpenseView> items = result.getContent().stream()
                .map(ExpenseView::from)
                .toList();
        return new ExpensePage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private record ValidatedFields(long amountPaise, LocalDate expenseDate, String description) {
    }

    private ValidatedFields validateFields(
            Long amountPaise,
            UUID categoryId,
            String description,
            LocalDate expenseDate) {
        if (amountPaise == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount is required");
        }
        if (amountPaise < MIN_AMOUNT_PAISE || amountPaise > MAX_AMOUNT_PAISE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount must be between ₹0.01 and ₹10,00,00,000");
        }
        if (categoryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is required");
        }
        if (description == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required");
        }
        String trimmedDescription = description.trim();
        if (trimmedDescription.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required");
        }
        if (trimmedDescription.length() > 200) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Description must be at most 200 characters");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveDate = expenseDate != null ? expenseDate : today;
        if (effectiveDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date cannot be in the future");
        }
        if (effectiveDate.isBefore(MIN_EXPENSE_DATE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date is too far in the past");
        }

        return new ValidatedFields(amountPaise, effectiveDate, trimmedDescription);
    }

    private static BudgetType resolveType(Category category, BudgetType requestedType) {
        if (category.getType() != null) {
            if (requestedType != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Type can only be set for categories without a fixed type");
            }
            return category.getType();
        }
        if (requestedType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type is required for this category");
        }
        return requestedType;
    }

    private static ResponseStatusException categoryNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
    }

    private static ResponseStatusException expenseNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found");
    }
}


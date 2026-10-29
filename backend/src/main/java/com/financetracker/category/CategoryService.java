package com.financetracker.category;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.financetracker.common.BudgetType;
import com.financetracker.expense.ExpenseRepository;

@Service
public class CategoryService {

    private static final long MAX_CATEGORIES_PER_USER = 50;
    private static final String DUPLICATE_NAME_MESSAGE =
            "A category with this name already exists. If it is archived, restore it instead.";
    private static final String PROTECTED_CATEGORY_MESSAGE = "This category cannot be changed";
    private static final String CATEGORY_HAS_EXPENSES_MESSAGE =
            "This category has expenses. Archive it instead.";

    // Sort order: NEED (0), WANT (1), INVESTMENT (2), null type last (3); then name ignoring case.
    private static final Comparator<CategoryView> CATEGORY_ORDER = Comparator
            .comparingInt((CategoryView c) -> typeRank(c.type()))
            .thenComparing(CategoryView::name, String.CASE_INSENSITIVE_ORDER);

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            ExpenseRepository expenseRepository) {
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
    }

    public List<CategoryView> list(UUID userId, boolean includeArchived) {
        return categoryRepository.findAllByUserId(userId).stream()
                .filter(c -> includeArchived || !c.isArchived())
                .map(CategoryView::from)
                .sorted(CATEGORY_ORDER)
                .toList();
    }

    @Transactional
    public CategoryView create(UUID userId, String name, BudgetType type) {
        String trimmedName = validateAndTrimName(name);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type is required");
        }
        if (categoryRepository.countByUserId(userId) >= MAX_CATEGORIES_PER_USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category limit reached");
        }
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, trimmedName)) {
            throw duplicateName();
        }

        Category category;
        try {
            category = categoryRepository.saveAndFlush(new Category(userId, trimmedName, type));
        } catch (DataIntegrityViolationException e) {
            throw duplicateName();
        }
        return CategoryView.from(category);
    }

    @Transactional
    public CategoryView update(UUID userId, UUID id, String name, BudgetType type, Boolean archived) {
        if (name == null && type == null && archived == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to update");
        }

        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(CategoryService::notFound);
        if (category.getType() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, PROTECTED_CATEGORY_MESSAGE);
        }

        if (name != null) {
            String trimmedName = validateAndTrimName(name);
            if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, trimmedName, id)) {
                throw duplicateName();
            }
            category.setName(trimmedName);
        }
        if (type != null) {
            category.setType(type);
        }
        if (archived != null) {
            category.setArchived(archived);
        }

        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw duplicateName();
        }
        return CategoryView.from(category);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(CategoryService::notFound);
        if (category.getType() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, PROTECTED_CATEGORY_MESSAGE);
        }
        if (expenseRepository.existsByUserIdAndCategoryId(userId, id)) {
            throw categoryHasExpenses();
        }

        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw categoryHasExpenses();
        }
    }

    private static String validateAndTrimName(String name) {
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (trimmed.length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must be at most 50 characters");
        }
        return trimmed;
    }

    private static int typeRank(BudgetType type) {
        if (type == null) {
            return 3;
        }
        return switch (type) {
            case NEED -> 0;
            case WANT -> 1;
            case INVESTMENT -> 2;
        };
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
    }

    private static ResponseStatusException duplicateName() {
        return new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_NAME_MESSAGE);
    }

    private static ResponseStatusException categoryHasExpenses() {
        return new ResponseStatusException(HttpStatus.CONFLICT, CATEGORY_HAS_EXPENSES_MESSAGE);
    }
}

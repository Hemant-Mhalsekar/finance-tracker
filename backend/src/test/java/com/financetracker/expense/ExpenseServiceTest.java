package com.financetracker.expense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.BudgetType;

class ExpenseServiceTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            ZonedDateTime.of(2026, 10, 29, 12, 0, 0, 0, KOLKATA).toInstant(),
            KOLKATA);

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID OTHER_CATEGORY_ID = UUID.randomUUID();
    private static final UUID EXPENSE_ID = UUID.randomUUID();

    private ExpenseRepository expenseRepository;
    private CategoryRepository categoryRepository;
    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseRepository = mock(ExpenseRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        expenseService = new ExpenseService(expenseRepository, categoryRepository, FIXED_CLOCK);

        when(expenseRepository.saveAndFlush(any(Expense.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createDefaultsToTodayWhenDateIsNullAndRejectsInvalidDatesAmountsAndDescriptions() {
        Category dining = new Category(USER_ID, "Dining", BudgetType.WANT);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(dining));

        ExpenseView created = expenseService.create(
                USER_ID, 250_00L, CATEGORY_ID, "  Lunch  ", null, null);
        assertEquals(TODAY, created.expenseDate());
        assertEquals("Lunch", created.description());
        assertEquals(250_00L, created.amountPaise());
        assertEquals(BudgetType.WANT, created.type());

        ResponseStatusException futureEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(
                        USER_ID, 100L, CATEGORY_ID, "Lunch", LocalDate.of(2026, 10, 30), null));
        assertEquals(HttpStatus.BAD_REQUEST, futureEx.getStatusCode());
        assertEquals("Date cannot be in the future", futureEx.getReason());

        ResponseStatusException pastEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(
                        USER_ID, 100L, CATEGORY_ID, "Lunch", LocalDate.of(1999, 12, 31), null));
        assertEquals(HttpStatus.BAD_REQUEST, pastEx.getStatusCode());
        assertEquals("Date is too far in the past", pastEx.getReason());

        ResponseStatusException zeroAmountEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(USER_ID, 0L, CATEGORY_ID, "Lunch", TODAY, null));
        assertEquals(HttpStatus.BAD_REQUEST, zeroAmountEx.getStatusCode());
        assertEquals("Amount must be between ₹0.01 and ₹10,00,00,000", zeroAmountEx.getReason());

        ResponseStatusException hugeAmountEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(USER_ID, 10_000_000_001L, CATEGORY_ID, "Lunch", TODAY, null));
        assertEquals(HttpStatus.BAD_REQUEST, hugeAmountEx.getStatusCode());
        assertEquals("Amount must be between ₹0.01 and ₹10,00,00,000", hugeAmountEx.getReason());

        ResponseStatusException blankDescEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(USER_ID, 100L, CATEGORY_ID, "   ", TODAY, null));
        assertEquals(HttpStatus.BAD_REQUEST, blankDescEx.getStatusCode());
        assertEquals("Description is required", blankDescEx.getReason());

        String desc201 = "a".repeat(201);
        ResponseStatusException longDescEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(USER_ID, 100L, CATEGORY_ID, desc201, TODAY, null));
        assertEquals(HttpStatus.BAD_REQUEST, longDescEx.getStatusCode());
        assertEquals("Description must be at most 200 characters", longDescEx.getReason());
    }

    @Test
    void createTypeResolutionForTypedAndNullTypeCategories() {
        Category dining = new Category(USER_ID, "Dining", BudgetType.WANT);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(dining));

        ResponseStatusException typedWithTypeEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(USER_ID, 500L, CATEGORY_ID, "Dinner", TODAY, BudgetType.NEED));
        assertEquals(HttpStatus.BAD_REQUEST, typedWithTypeEx.getStatusCode());
        assertEquals("Type can only be set for categories without a fixed type", typedWithTypeEx.getReason());

        Category other = new Category(USER_ID, "Other", null);
        when(categoryRepository.findByIdAndUserId(OTHER_CATEGORY_ID, USER_ID)).thenReturn(Optional.of(other));

        ResponseStatusException nullTypeWithoutTypeEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(USER_ID, 500L, OTHER_CATEGORY_ID, "Misc", TODAY, null));
        assertEquals(HttpStatus.BAD_REQUEST, nullTypeWithoutTypeEx.getStatusCode());
        assertEquals("Type is required for this category", nullTypeWithoutTypeEx.getReason());

        ExpenseView created = expenseService.create(
                USER_ID, 500L, OTHER_CATEGORY_ID, "Misc", TODAY, BudgetType.INVESTMENT);
        assertEquals(BudgetType.INVESTMENT, created.type());
    }

    @Test
    void createRejectsMissingCategoryArchivedCategoryAndLimitReached() {
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.empty());

        ResponseStatusException missingEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(USER_ID, 500L, CATEGORY_ID, "Lunch", TODAY, null));
        assertEquals(HttpStatus.NOT_FOUND, missingEx.getStatusCode());
        assertEquals("Category not found", missingEx.getReason());

        Category archived = new Category(USER_ID, "Old", BudgetType.WANT);
        archived.setArchived(true);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(archived));

        ResponseStatusException archivedEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(USER_ID, 500L, CATEGORY_ID, "Lunch", TODAY, null));
        assertEquals(HttpStatus.CONFLICT, archivedEx.getStatusCode());
        assertEquals("This category is archived", archivedEx.getReason());

        when(expenseRepository.countByUserId(USER_ID)).thenReturn(20_000L);
        ResponseStatusException limitEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.create(USER_ID, 500L, CATEGORY_ID, "Lunch", TODAY, null));
        assertEquals(HttpStatus.CONFLICT, limitEx.getStatusCode());
        assertEquals("Expense limit reached", limitEx.getReason());
        verify(expenseRepository, never()).saveAndFlush(any());
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void updateKeepsStoredTypeOnSameTypedCategoryTakesNewTypeOnCategoryChangeAndHandlesArchived() {
        // Expense was originally logged as WANT under CATEGORY_ID, whose type was later changed to NEED.
        Expense existing = new Expense(USER_ID, CATEGORY_ID, BudgetType.WANT, 1_000L, TODAY, "Meal");
        when(expenseRepository.findByIdAndUserId(EXPENSE_ID, USER_ID)).thenReturn(Optional.of(existing));

        Category sameCategoryNowNeed = new Category(USER_ID, "Dining", BudgetType.NEED);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(sameCategoryNowNeed));

        ExpenseView keptType = expenseService.update(
                USER_ID, EXPENSE_ID, 1_500L, CATEGORY_ID, "Updated meal", TODAY, null);
        assertEquals(BudgetType.WANT, keptType.type());
        assertEquals(1_500L, keptType.amountPaise());

        // Switching to a different typed category adopts the new category's type.
        Category transport = new Category(USER_ID, "Transport", BudgetType.NEED);
        when(categoryRepository.findByIdAndUserId(OTHER_CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(transport));

        ExpenseView switched = expenseService.update(
                USER_ID, EXPENSE_ID, 1_500L, OTHER_CATEGORY_ID, "Cab", TODAY, null);
        assertEquals(BudgetType.NEED, switched.type());
        assertEquals(OTHER_CATEGORY_ID, switched.categoryId());

        // Updating an expense while keeping the same (now archived) category is allowed.
        transport.setArchived(true);
        ExpenseView sameArchived = expenseService.update(
                USER_ID, EXPENSE_ID, 2_000L, OTHER_CATEGORY_ID, "Cab edited", TODAY, null);
        assertEquals(2_000L, sameArchived.amountPaise());

        // Moving an expense to a different archived category is rejected with 409.
        sameCategoryNowNeed.setArchived(true);
        ResponseStatusException moveArchivedEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.update(USER_ID, EXPENSE_ID, 2_000L, CATEGORY_ID, "Move", TODAY, null));
        assertEquals(HttpStatus.CONFLICT, moveArchivedEx.getStatusCode());
        assertEquals("This category is archived", moveArchivedEx.getReason());
    }

    @Test
    void deleteThrows404WhenZeroRowsDeleted() {
        when(expenseRepository.deleteByIdAndUserId(EXPENSE_ID, USER_ID)).thenReturn(0L);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.delete(USER_ID, EXPENSE_ID));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Expense not found", ex.getReason());
    }

    @Test
    void listRejectsInvalidDateRangeAndPagination() {
        ResponseStatusException rangeEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.list(
                        USER_ID, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 9), null, 0, 20));
        assertEquals(HttpStatus.BAD_REQUEST, rangeEx.getStatusCode());
        assertEquals("From date must not be after to date", rangeEx.getReason());

        ResponseStatusException pageEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.list(USER_ID, null, null, null, -1, 20));
        assertEquals(HttpStatus.BAD_REQUEST, pageEx.getStatusCode());

        ResponseStatusException sizeZeroEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.list(USER_ID, null, null, null, 0, 0));
        assertEquals(HttpStatus.BAD_REQUEST, sizeZeroEx.getStatusCode());

        ResponseStatusException sizeOverEx = assertThrows(
                ResponseStatusException.class,
                () -> expenseService.list(USER_ID, null, null, null, 0, 101));
        assertEquals(HttpStatus.BAD_REQUEST, sizeOverEx.getStatusCode());
    }
}


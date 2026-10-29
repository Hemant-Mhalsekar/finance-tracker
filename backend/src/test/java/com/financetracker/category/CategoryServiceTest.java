package com.financetracker.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.financetracker.common.BudgetType;
import com.financetracker.expense.ExpenseRepository;

class CategoryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private CategoryRepository categoryRepository;
    private ExpenseRepository expenseRepository;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        categoryService = new CategoryService(categoryRepository, expenseRepository);
    }

    @Test
    void createTranslatesDataIntegrityViolationFromSaveAndFlushToDuplicateName409() {
        when(categoryRepository.countByUserId(USER_ID)).thenReturn(5L);
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Coffee")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.create(USER_ID, "Coffee", BudgetType.WANT));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(
                "A category with this name already exists. If it is archived, restore it instead.",
                ex.getReason());
    }

    @Test
    void updateTranslatesDataIntegrityViolationFromFlushToDuplicateName409() {
        Category existing = new Category(USER_ID, "Dining", BudgetType.WANT);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Coffee", CATEGORY_ID))
                .thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate key")).when(categoryRepository).flush();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.update(USER_ID, CATEGORY_ID, "Coffee", null, null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(
                "A category with this name already exists. If it is archived, restore it instead.",
                ex.getReason());
    }

    @Test
    void deleteTranslatesDataIntegrityViolationFromFlushToHasExpenses409() {
        Category existing = new Category(USER_ID, "Dining", BudgetType.WANT);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(existing));
        when(expenseRepository.existsByUserIdAndCategoryId(USER_ID, CATEGORY_ID)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("fk violation")).when(categoryRepository).flush();

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.delete(USER_ID, CATEGORY_ID));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("This category has expenses. Archive it instead.", ex.getReason());
    }

    @Test
    void createWithCountAt50Throws409AndNeverCallsSaveAndFlush() {
        when(categoryRepository.countByUserId(USER_ID)).thenReturn(50L);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.create(USER_ID, "Coffee", BudgetType.WANT));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Category limit reached", ex.getReason());
        verify(categoryRepository, never()).saveAndFlush(any());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateAndDeleteOnProtectedCategoryThrow409AndNeverModifyOrDelete() {
        Category other = new Category(USER_ID, "Other", null);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID)).thenReturn(Optional.of(other));

        ResponseStatusException updateEx = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.update(USER_ID, CATEGORY_ID, "Renamed", BudgetType.NEED, true));
        assertEquals(HttpStatus.CONFLICT, updateEx.getStatusCode());
        assertEquals("This category cannot be changed", updateEx.getReason());
        assertEquals("Other", other.getName());
        assertNull(other.getType());
        assertEquals(false, other.isArchived());
        verify(categoryRepository, never()).flush();

        ResponseStatusException deleteEx = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.delete(USER_ID, CATEGORY_ID));
        assertEquals(HttpStatus.CONFLICT, deleteEx.getStatusCode());
        assertEquals("This category cannot be changed", deleteEx.getReason());
        verify(categoryRepository, never()).delete(any());
        verify(categoryRepository, never()).flush();
    }

    @Test
    void updateWithAllFieldsNullThrows400NothingToUpdate() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.update(USER_ID, CATEGORY_ID, null, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Nothing to update", ex.getReason());
        verify(categoryRepository, never()).findByIdAndUserId(any(), any());
    }
}


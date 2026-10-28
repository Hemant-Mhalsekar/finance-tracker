package com.financetracker.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

// Deliberately extends Repository (not JpaRepository/CrudRepository) so no method that
// fetches by id alone is exposed; every query must explicitly filter by userId.
public interface CategoryRepository extends Repository<Category, UUID> {

    Category save(Category category);

    void delete(Category category);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    List<Category> findAllByUserId(UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);
}


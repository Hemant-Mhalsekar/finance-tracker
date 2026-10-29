package com.financetracker.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

// Deliberately extends Repository (not JpaRepository/CrudRepository) so no method that
// fetches by id alone is exposed; every query must explicitly filter by userId.
// Calls go through the repository so Spring translates database errors into
// DataIntegrityViolationException; the service is not a @Repository, so errors from a
// directly injected EntityManager are not translated and would surface as HTTP 500.
public interface CategoryRepository extends Repository<Category, UUID> {

    Category save(Category category);

    Category saveAndFlush(Category category);

    void flush();

    void delete(Category category);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    List<Category> findAllByUserId(UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    long countByUserId(UUID userId);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(UUID userId, String name, UUID id);
}


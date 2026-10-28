package com.financetracker.category;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.financetracker.common.BudgetType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    // NULL means the user chooses the type for each expense.
    @Enumerated(EnumType.STRING)
    private BudgetType type;

    @Column(nullable = false)
    private boolean archived;

    // Set by the database default (now()), so it is never written by the app.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Category() {
        // required by JPA
    }

    public Category(UUID userId, String name, BudgetType type) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.archived = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public BudgetType getType() {
        return type;
    }

    public boolean isArchived() {
        return archived;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(BudgetType type) {
        this.type = type;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}


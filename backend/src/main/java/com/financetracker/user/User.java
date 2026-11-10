package com.financetracker.user;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // SMALLINT in the database, so Short (not Integer) to pass schema validation.
    @Column(name = "pay_day")
    private Short payDay;

    // A scheduled pay day change; both are null when nothing is pending.
    @Column(name = "pending_pay_day")
    private Short pendingPayDay;

    // Start date of the first cycle that uses pendingPayDay.
    @Column(name = "pending_pay_day_from")
    private LocalDate pendingPayDayFrom;

    // Set by the database default (now()), so it is never written by the app.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected User() {
        // required by JPA
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Short getPayDay() {
        return payDay;
    }

    public Short getPendingPayDay() {
        return pendingPayDay;
    }

    public LocalDate getPendingPayDayFrom() {
        return pendingPayDayFrom;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void schedulePayDayChange(short newPayDay, LocalDate from) {
        this.pendingPayDay = newPayDay;
        this.pendingPayDayFrom = from;
    }

    public void clearPendingPayDay() {
        this.pendingPayDay = null;
        this.pendingPayDayFrom = null;
    }

    public void applyPendingPayDay() {
        if (pendingPayDay == null) {
            throw new IllegalStateException("No pending pay day to apply");
        }
        this.payDay = pendingPayDay;
        clearPendingPayDay();
    }
}

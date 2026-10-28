package com.financetracker.cycle;

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
@Table(name = "cycles")
public class Cycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "salary_paise", nullable = false)
    private long salaryPaise;

    @Column(name = "needs_pct", nullable = false)
    private short needsPct;

    @Column(name = "wants_pct", nullable = false)
    private short wantsPct;

    @Column(name = "savings_pct", nullable = false)
    private short savingsPct;

    @Column(name = "needs_paise", nullable = false)
    private long needsPaise;

    @Column(name = "wants_paise", nullable = false)
    private long wantsPaise;

    @Column(name = "savings_paise", nullable = false)
    private long savingsPaise;

    // Set by the database default (now()), so it is never written by the app.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Cycle() {
        // required by JPA
    }

    public Cycle(
            UUID userId,
            LocalDate periodStart,
            LocalDate periodEnd,
            long salaryPaise,
            short needsPct,
            short wantsPct,
            short savingsPct,
            long needsPaise,
            long wantsPaise,
            long savingsPaise) {
        this.userId = userId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        applyBudget(salaryPaise, needsPct, wantsPct, savingsPct, needsPaise, wantsPaise, savingsPaise);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public long getSalaryPaise() {
        return salaryPaise;
    }

    public short getNeedsPct() {
        return needsPct;
    }

    public short getWantsPct() {
        return wantsPct;
    }

    public short getSavingsPct() {
        return savingsPct;
    }

    public long getNeedsPaise() {
        return needsPaise;
    }

    public long getWantsPaise() {
        return wantsPaise;
    }

    public long getSavingsPaise() {
        return savingsPaise;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void applyBudget(
            long salaryPaise,
            short needsPct,
            short wantsPct,
            short savingsPct,
            long needsPaise,
            long wantsPaise,
            long savingsPaise) {
        this.salaryPaise = salaryPaise;
        this.needsPct = needsPct;
        this.wantsPct = wantsPct;
        this.savingsPct = savingsPct;
        this.needsPaise = needsPaise;
        this.wantsPaise = wantsPaise;
        this.savingsPaise = savingsPaise;
    }
}


package com.financetracker.cycle;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.financetracker.user.User;
import com.financetracker.user.UserRepository;

@Service
public class CycleService {

    // 100 years of monthly cycles. Only reachable through a bug, so it fails loudly instead of looping.
    private static final int MAX_CYCLES_PER_ROLL_FORWARD = 1200;

    private final CycleRepository cycleRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public CycleService(CycleRepository cycleRepository, UserRepository userRepository, Clock clock) {
        this.cycleRepository = cycleRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public CycleView getCurrent(UUID userId) {
        // Fast path without a lock: most requests land in a cycle that already exists.
        Optional<Cycle> existing = cycleRepository.findContaining(userId, today());
        if (existing.isPresent()) {
            return CycleView.from(existing.get());
        }
        return CycleView.from(currentCycleForLockedUser(lockUser(userId)));
    }

    // Changes the current cycle only. Later cycles copy the newest cycle when they are created,
    // so they pick up the new salary; past cycles keep theirs.
    @Transactional
    public CycleView changeCurrentSalary(UUID userId, long salaryPaise) {
        Cycle current = currentCycleForLockedUser(lockUser(userId));
        BudgetSplit split = BudgetSplit.of(
                salaryPaise, current.getNeedsPct(), current.getWantsPct(), current.getSavingsPct());
        current.applyBudget(
                salaryPaise,
                current.getNeedsPct(),
                current.getWantsPct(),
                current.getSavingsPct(),
                split.needsPaise(),
                split.wantsPaise(),
                split.savingsPaise());
        return CycleView.from(cycleRepository.saveAndFlush(current));
    }

    // Takes the row lock on the user. Call it before anything else loads the user in this transaction.
    // MANDATORY: outside a transaction the lock would be released immediately.
    @Transactional(propagation = Propagation.MANDATORY)
    public User lockUser(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        if (user.getPayDay() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete setup first");
        }
        return user;
    }

    // The caller must hold the lock from lockUser. Stored cycles always win over the formula:
    // the formula only creates cycles after the newest stored one.
    @Transactional(propagation = Propagation.MANDATORY)
    public Cycle currentCycleForLockedUser(User user) {
        UUID userId = user.getId();
        LocalDate today = today();

        // Another request may have created the cycles while this one waited for the lock.
        Optional<Cycle> existing = cycleRepository.findContaining(userId, today);
        if (existing.isPresent()) {
            return existing.get();
        }

        Cycle newest = cycleRepository.findTopByUserIdOrderByPeriodStartDesc(userId)
                .orElseThrow(() -> new IllegalStateException("Onboarded user has no cycle"));
        if (!newest.getPeriodEnd().isBefore(today)) {
            throw new IllegalStateException("Newest cycle does not end before today but no cycle contains today");
        }

        CyclePeriod previous = new CyclePeriod(newest.getPeriodStart(), newest.getPeriodEnd());
        int created = 0;
        while (previous.end().isBefore(today)) {
            if (++created > MAX_CYCLES_PER_ROLL_FORWARD) {
                throw new IllegalStateException("Roll-forward exceeded " + MAX_CYCLES_PER_ROLL_FORWARD + " cycles");
            }

            LocalDate nextStart = previous.end().plusDays(1);
            if (user.getPendingPayDay() != null && !nextStart.isBefore(user.getPendingPayDayFrom())) {
                user.applyPendingPayDay();
            }

            CyclePeriod next = CycleCalculator.startingOn(nextStart, user.getPayDay());
            cycleRepository.insertIfAbsent(
                    userId,
                    next.start(),
                    next.end(),
                    newest.getSalaryPaise(),
                    newest.getNeedsPct(),
                    newest.getWantsPct(),
                    newest.getSavingsPct(),
                    newest.getNeedsPaise(),
                    newest.getWantsPaise(),
                    newest.getSavingsPaise());
            previous = next;
        }
        userRepository.saveAndFlush(user);

        return cycleRepository.findContaining(userId, today)
                .orElseThrow(() -> new IllegalStateException("Roll-forward did not create a cycle containing today"));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}

package com.financetracker.settings;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.financetracker.cycle.Cycle;
import com.financetracker.cycle.CycleCalculator;
import com.financetracker.cycle.CyclePeriod;
import com.financetracker.cycle.CycleService;
import com.financetracker.user.User;
import com.financetracker.user.UserRepository;

@Service
public class SettingsService {

    private final CycleService cycleService;
    private final UserRepository userRepository;

    public SettingsService(CycleService cycleService, UserRepository userRepository) {
        this.cycleService = cycleService;
        this.userRepository = userRepository;
    }

    // A pay day change waits one full cycle: the current cycle and the next one keep the old
    // pay day, and the cycle after that starts the new one. Choosing the current pay day again
    // cancels a pending change. A second change replaces the pending one.
    @Transactional
    public PayDaySettingsView changePayDay(UUID userId, int newPayDay) {
        User user = cycleService.lockUser(userId);
        // Rolls forward first, which may apply an earlier pending change, so the dates below
        // are computed from the pay day that is really in effect.
        Cycle current = cycleService.currentCycleForLockedUser(user);

        if (newPayDay == user.getPayDay()) {
            user.clearPendingPayDay();
        } else {
            CyclePeriod next = CycleCalculator.nextAfter(
                    new CyclePeriod(current.getPeriodStart(), current.getPeriodEnd()), user.getPayDay());
            user.schedulePayDayChange((short) newPayDay, next.end().plusDays(1));
        }
        return PayDaySettingsView.from(userRepository.saveAndFlush(user));
    }
}

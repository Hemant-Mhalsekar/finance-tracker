package com.financetracker.settings;

import java.time.LocalDate;

import com.financetracker.user.User;

// pendingPayDay and pendingPayDayFrom are null when no change is scheduled.
public record PayDaySettingsView(int payDay, Short pendingPayDay, LocalDate pendingPayDayFrom) {

    public static PayDaySettingsView from(User user) {
        return new PayDaySettingsView(user.getPayDay(), user.getPendingPayDay(), user.getPendingPayDayFrom());
    }
}

package com.financetracker.onboarding;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.category.DefaultCategories;
import com.financetracker.cycle.BudgetSplit;
import com.financetracker.cycle.Cycle;
import com.financetracker.cycle.CycleCalculator;
import com.financetracker.cycle.CyclePeriod;
import com.financetracker.cycle.CycleRepository;
import com.financetracker.cycle.CycleView;
import com.financetracker.user.UserRepository;

@Service
public class OnboardingService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CycleRepository cycleRepository;
    private final Clock clock;

    public OnboardingService(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            CycleRepository cycleRepository,
            Clock clock) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.cycleRepository = cycleRepository;
        this.clock = clock;
    }

    @Transactional
    public CycleView onboard(UUID userId, long salaryPaise, int payDay) {
        if (userRepository.claimPayDay(userId, (short) payDay) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Setup already completed");
        }

        for (DefaultCategories.DefaultCategory def : DefaultCategories.all()) {
            categoryRepository.save(new Category(userId, def.name(), def.type()));
        }

        LocalDate today = LocalDate.now(clock);
        CyclePeriod period = CycleCalculator.containing(today, payDay);
        BudgetSplit split = BudgetSplit.of(salaryPaise);

        Cycle cycle = cycleRepository.save(new Cycle(
                userId,
                period.start(),
                period.end(),
                salaryPaise,
                (short) 50,
                (short) 30,
                (short) 20,
                split.needsPaise(),
                split.wantsPaise(),
                split.savingsPaise()));

        return CycleView.from(cycle);
    }
}


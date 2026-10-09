package com.nexploy.campuslink.readiness.event;

import com.nexploy.campuslink.readiness.service.ReadinessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recomputes a student's readiness off the request thread whenever a readiness input changed.
 *
 * <p>Runs after the publishing work commits ({@link TransactionPhase#AFTER_COMMIT}, with
 * {@code fallbackExecution = true} so it still runs when the publisher had no ambient transaction —
 * the profile-edit controllers commit each repository write on its own), on the dedicated
 * {@code readinessRecomputeExecutor} pool so a slow ML call never blocks the user's save.
 *
 * <p>Best-effort by design: a scoring failure (e.g. the ML service is unavailable) leaves the prior
 * readiness score untouched and is logged, never propagated. It calls the system-scoped
 * {@link ReadinessService#recomputeInternal} because an async thread carries no security context;
 * the triggering change was already authorized.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadinessRecomputeListener {

    private final ReadinessService readinessService;

    @Async("readinessRecomputeExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(StudentReadinessInputChangedEvent event) {
        try {
            readinessService.recomputeInternal(event.studentId(), "current");
        } catch (RuntimeException ex) {
            log.warn("Readiness recompute failed for student {} (kept prior score): {}",
                event.studentId(), ex.getMessage());
        }
    }
}

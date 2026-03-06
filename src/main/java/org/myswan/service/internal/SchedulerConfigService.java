package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.SchedulerConfig;
import org.myswan.model.collection.SchedulerConfig.StepStatus;
import org.myswan.repository.SchedulerConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SchedulerConfigService {

    private static final String ID = "SCHEDULER_CONFIG";
    private final SchedulerConfigRepository repository;

    public SchedulerConfigService(SchedulerConfigRepository repository) {
        this.repository = repository;
    }

    public SchedulerConfig get() {
        return repository.findById(ID).orElseGet(() -> {
            SchedulerConfig def = new SchedulerConfig();
            return repository.save(def);
        });
    }

    public SchedulerConfig update(SchedulerConfig incoming) {
        SchedulerConfig existing = get();
        if (incoming.isEnabled() != existing.isEnabled()) {
            existing.setEnabled(incoming.isEnabled());
        }
        if (incoming.getIntervalMinutes() > 0) {
            existing.setIntervalMinutes(incoming.getIntervalMinutes());
        }
        existing.setWindowStartHour(incoming.getWindowStartHour());
        existing.setWindowEndHour(incoming.getWindowEndHour());
        if (incoming.getLastFailedStep() != null) {
            existing.setLastFailedStep(incoming.getLastFailedStep());
            existing.setLastFailedError(incoming.getLastFailedError());
            existing.setLastFailedAt(incoming.getLastFailedAt());
        }
        if (incoming.getPatternIntervalMinutes() > 0) {
            existing.setPatternIntervalMinutes(incoming.getPatternIntervalMinutes());
        }
        return repository.save(existing);
    }

    // ── Run-lifecycle methods ────────────────────────────────────────────────

    /** Called at the very start of a pipeline run – seeds all steps as PENDING */
    public void markRunStart(List<String> stepNames) {
        SchedulerConfig cfg = get();
        cfg.setRunning(true);
        cfg.setLastRunStartedAt(Instant.now().toString());
        List<StepStatus> steps = new ArrayList<>();
        for (String name : stepNames) {
            steps.add(new StepStatus(name, "PENDING", null, null, null));
        }
        cfg.setStepStatuses(steps);
        repository.save(cfg);
    }

    /** Called just before a step starts executing */
    public void markStepRunning(String stepName) {
        SchedulerConfig cfg = get();
        cfg.getStepStatuses().stream()
                .filter(s -> stepName.equals(s.getName()))
                .findFirst()
                .ifPresent(s -> {
                    s.setStatus("RUNNING");
                    s.setStartedAt(Instant.now().toString());
                });
        repository.save(cfg);
    }

    /** Called when a step completes successfully */
    public void markStepSuccess(String stepName) {
        SchedulerConfig cfg = get();
        cfg.getStepStatuses().stream()
                .filter(s -> stepName.equals(s.getName()))
                .findFirst()
                .ifPresent(s -> {
                    s.setStatus("SUCCESS");
                    s.setFinishedAt(Instant.now().toString());
                });
        repository.save(cfg);
    }

    /** Called when all steps succeed */
    public void markSuccess() {
        SchedulerConfig cfg = get();
        cfg.setRunning(false);
        cfg.setLastFailedStep(null);
        cfg.setLastFailedError(null);
        cfg.setLastFailedAt(null);
        cfg.setLastSuccessAt(Instant.now().toString());
        repository.save(cfg);
    }

    /** Called when a step fails – marks step FAILED, disables scheduler */
    public void markFailure(String stepName, String errorMessage) {
        SchedulerConfig cfg = get();
        cfg.setRunning(false);
        cfg.setEnabled(false);
        cfg.setLastFailedStep(stepName);
        cfg.setLastFailedError(errorMessage);
        cfg.setLastFailedAt(Instant.now().toString());
        cfg.getStepStatuses().stream()
                .filter(s -> stepName.equals(s.getName()))
                .findFirst()
                .ifPresent(s -> {
                    s.setStatus("FAILED");
                    s.setFinishedAt(Instant.now().toString());
                    s.setError(errorMessage);
                });
        repository.save(cfg);
        log.error("Scheduler auto-disabled. Failed step: {} | Error: {}", stepName, errorMessage);
    }

    // ── Pattern-scheduler lifecycle (never touches stepStatuses / enabled / running) ──

    /** Called just before a pattern fetch starts */
    public void markPatternRunning() {
        SchedulerConfig cfg = get();
        cfg.setPatternRunning(true);
        cfg.setPatternLastRunAt(Instant.now().toString());
        repository.save(cfg);
    }

    /** Called when a pattern fetch completes successfully */
    public void markPatternSuccess() {
        SchedulerConfig cfg = get();
        cfg.setPatternRunning(false);
        cfg.setPatternLastFailedError(null);
        cfg.setPatternLastFailedAt(null);
        repository.save(cfg);
    }

    /**
     * Called when a pattern fetch fails.
     * Records the error but does NOT disable the main scheduler.
     */
    public void markPatternFailure(String errorMessage) {
        SchedulerConfig cfg = get();
        cfg.setPatternRunning(false);
        cfg.setPatternLastFailedError(errorMessage);
        cfg.setPatternLastFailedAt(Instant.now().toString());
        repository.save(cfg);
        log.error("Pattern fetch failed (main scheduler unaffected): {}", errorMessage);
    }

    /** Re-enables the scheduler and clears failure state */
    public SchedulerConfig enable() {
        SchedulerConfig cfg = get();
        cfg.setEnabled(true);
        cfg.setLastFailedStep(null);
        cfg.setLastFailedError(null);
        cfg.setLastFailedAt(null);
        return repository.save(cfg);
    }

    public SchedulerConfig disable() {
        SchedulerConfig cfg = get();
        cfg.setEnabled(false);
        return repository.save(cfg);
    }
}


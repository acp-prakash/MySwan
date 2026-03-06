package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.SchedulerConfig;
import org.myswan.service.external.BarchartClient;
import org.myswan.service.external.RobinHoodClient;
import org.myswan.service.external.TradingViewClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * Dynamic scheduler that:
 *  - reads interval & window from MongoDB at each tick (no redeploy needed)
 *  - only fires between windowStartHour and windowEndHour
 *  - runs pipeline steps sequentially; stops & disables on first failure
 *  - records the failed step name + error in MongoDB so the UI badge can show it
 */
@Slf4j
@Service
public class SchedulerTask implements SchedulingConfigurer {

    // ── pipeline step names (used in failure badge) ──────────────────────────
    private static final String STEP_BARCHART     = "Barchart Quotes";
    private static final String STEP_TRADINGVIEW  = "TradingView Update";
    private static final String STEP_OPTIONS      = "Refresh Options";
    private static final String STEP_COMPUTE      = "Compute & Score";

    // ── fixed polling interval – checks every 60 s whether a real run is due ─
    private static final long POLL_MS = 60_000L;

    private final SchedulerConfigService schedulerConfigService;
    private final BarchartClient barchartClient;
    private final TradingViewClient tradingViewClient;
    private final RobinHoodClient robinHoodClient;
    private final ComputeService computeService;

    /** Tracks the last time the full pipeline was actually executed */
    private volatile long lastRunEpochMs = 0L;

    /** Holds the currently scheduled polling task so we can cancel/replace it */
    private ScheduledFuture<?> currentTask;
    private TaskScheduler taskScheduler;

    public SchedulerTask(
            SchedulerConfigService schedulerConfigService,
            BarchartClient barchartClient,
            TradingViewClient tradingViewClient,
            RobinHoodClient robinHoodClient,
            ComputeService computeService) {
        this.schedulerConfigService = schedulerConfigService;
        this.barchartClient = barchartClient;
        this.tradingViewClient = tradingViewClient;
        this.robinHoodClient = robinHoodClient;
        this.computeService = computeService;
    }

    // ── SchedulingConfigurer ─────────────────────────────────────────────────

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        this.taskScheduler = registrar.getScheduler();
        if (this.taskScheduler == null) {
            // Create a single-thread scheduler if Spring hasn't provided one yet
            org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler tpts =
                    new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
            tpts.setPoolSize(1);
            tpts.setThreadNamePrefix("myswan-scheduler-");
            tpts.initialize();
            registrar.setTaskScheduler(tpts);
            this.taskScheduler = tpts;
        }
        schedulePoll();
    }

    private synchronized void schedulePoll() {
        if (currentTask != null && !currentTask.isCancelled()) {
            currentTask.cancel(false);
        }
        currentTask = taskScheduler.scheduleWithFixedDelay(this::tick,
                Duration.ofMillis(POLL_MS));
    }

    // ── Core tick logic ──────────────────────────────────────────────────────

    private void tick() {
        try {
            SchedulerConfig cfg = schedulerConfigService.get();

            if (!cfg.isEnabled()) {
                return; // scheduler is OFF – nothing to do
            }

            // Window check
            int nowHour = LocalTime.now().getHour();
            if (nowHour < cfg.getWindowStartHour() || nowHour >= cfg.getWindowEndHour()) {
                log.debug("Scheduler: outside trading window ({} – {}), skipping",
                        cfg.getWindowStartHour(), cfg.getWindowEndHour());
                return;
            }

            // Interval check
            long intervalMs = (long) cfg.getIntervalMinutes() * 60_000L;
            long elapsed    = System.currentTimeMillis() - lastRunEpochMs;
            if (elapsed < intervalMs) {
                log.debug("Scheduler: {} ms until next run", intervalMs - elapsed);
                return;
            }

            log.info("=== Scheduler firing (interval={}m, window={}-{}) ===",
                    cfg.getIntervalMinutes(), cfg.getWindowStartHour(), cfg.getWindowEndHour());
            runPipeline();

        } catch (Exception e) {
            log.error("Scheduler tick error (unexpected)", e);
        }
    }

    // ── Pipeline ─────────────────────────────────────────────────────────────

    private void runPipeline() {
        lastRunEpochMs = System.currentTimeMillis();

        List<String> allSteps = List.of(STEP_BARCHART, STEP_TRADINGVIEW, STEP_OPTIONS, STEP_COMPUTE);
        schedulerConfigService.markRunStart(allSteps);

        // Step 1: Barchart daily quotes
        try {
            schedulerConfigService.markStepRunning(STEP_BARCHART);
            log.info("[Step 1/4] {}", STEP_BARCHART);
            barchartClient.getIntraDayQuotes();
            schedulerConfigService.markStepSuccess(STEP_BARCHART);
            log.info("[Step 1/4] {} ✓", STEP_BARCHART);
        } catch (Exception e) {
            fail(STEP_BARCHART, e);
            return;
        }

        // Step 2: TradingView
        try {
            schedulerConfigService.markStepRunning(STEP_TRADINGVIEW);
            log.info("[Step 2/4] {}", STEP_TRADINGVIEW);
            String result = tradingViewClient.updateTradingView();
            if (result != null && result.toLowerCase().contains("failed")) {
                throw new RuntimeException(result);
            }
            schedulerConfigService.markStepSuccess(STEP_TRADINGVIEW);
            log.info("[Step 2/4] {} ✓ → {}", STEP_TRADINGVIEW, result);
        } catch (Exception e) {
            fail(STEP_TRADINGVIEW, e);
            return;
        }

        // Step 3: Refresh Options
        try {
            schedulerConfigService.markStepRunning(STEP_OPTIONS);
            log.info("[Step 3/4] {}", STEP_OPTIONS);
            int saved = robinHoodClient.fetchAndSaveOptions(LocalDate.now());
            schedulerConfigService.markStepSuccess(STEP_OPTIONS);
            log.info("[Step 3/4] {} ✓ → {} options saved", STEP_OPTIONS, saved);
        } catch (Exception e) {
            fail(STEP_OPTIONS, e);
            return;
        }

        // Step 4: Compute & Score (internally runs syncAllHistory too)
        try {
            schedulerConfigService.markStepRunning(STEP_COMPUTE);
            log.info("[Step 4/4] {}", STEP_COMPUTE);
            String result = computeService.compute();
            if (result != null && result.toLowerCase().contains("error")) {
                throw new RuntimeException(result);
            }
            schedulerConfigService.markStepSuccess(STEP_COMPUTE);
            log.info("[Step 4/4] {} ✓ → {}", STEP_COMPUTE, result);
        } catch (Exception e) {
            fail(STEP_COMPUTE, e);
            return;
        }

        // All steps passed
        schedulerConfigService.markSuccess();
        log.info("=== Scheduler pipeline completed successfully ===");
    }

    private void fail(String stepName, Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        log.error("=== Scheduler pipeline FAILED at step '{}': {} ===", stepName, msg, e);
        schedulerConfigService.markFailure(stepName, msg);
    }
}

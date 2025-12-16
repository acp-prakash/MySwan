package org.myswan.controller.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.GuaranteedPick;
import org.myswan.model.compute.ExplosiveScoreDTO;
import org.myswan.model.compute.GuaranteedCandidateDTO;
import org.myswan.service.internal.GuaranteedExplosiveService;
import org.myswan.service.internal.PerformanceTrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Guaranteed Explosive Stock Detection
 */
@Slf4j
@RestController
@RequestMapping("/api/guaranteed")
@CrossOrigin(origins = "*")
public class GuaranteedExplosiveController {

    private final GuaranteedExplosiveService guaranteedExplosiveService;
    private final PerformanceTrackingService performanceTrackingService;

    public GuaranteedExplosiveController(GuaranteedExplosiveService guaranteedExplosiveService,
                                        PerformanceTrackingService performanceTrackingService) {
        this.guaranteedExplosiveService = guaranteedExplosiveService;
        this.performanceTrackingService = performanceTrackingService;
    }

    /**
     * Get top 3 guaranteed explosive stocks for today
     * GET /api/guaranteed/top3
     * NOTE: Only saves to DB once per day (first call) to prevent duplicates
     */
    @GetMapping("/top3")
    public ResponseEntity<List<GuaranteedCandidateDTO>> getTop3Guaranteed() {
        log.info("Finding top 3 guaranteed explosive stocks");

        try {
            List<GuaranteedCandidateDTO> candidates = guaranteedExplosiveService.findTop3Guaranteed();

            // Save picks to database for tracking (only if not already saved today)
            if (!candidates.isEmpty()) {
                guaranteedExplosiveService.saveTop3Picks(candidates);
            }

            log.info("Found {} guaranteed candidates", candidates.size());
            return ResponseEntity.ok(candidates);

        } catch (Exception e) {
            log.error("Error finding guaranteed explosive stocks", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Force refresh today's top 3 picks (overwrites existing)
     * POST /api/guaranteed/refresh-picks
     * Use when you want to update today's picks with latest data
     */
    @PostMapping("/refresh-picks")
    public ResponseEntity<String> refreshTodaysPicks() {
        log.info("Force refreshing today's picks");

        try {
            List<GuaranteedCandidateDTO> candidates = guaranteedExplosiveService.findTop3Guaranteed();

            if (candidates.isEmpty()) {
                return ResponseEntity.ok("No candidates found today");
            }

            guaranteedExplosiveService.forceSaveTop3Picks(candidates);

            return ResponseEntity.ok("Refreshed " + candidates.size() + " picks for today");

        } catch (Exception e) {
            log.error("Error refreshing picks", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get performance statistics
     * GET /api/guaranteed/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getPerformanceStats() {
        log.info("Getting guaranteed picks performance statistics");

        try {
            Map<String, Object> stats = guaranteedExplosiveService.getPerformanceStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting performance stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get picks for a specific date
     * GET /api/guaranteed/picks?date=2025-12-06
     */
    @GetMapping("/picks")
    public ResponseEntity<List<GuaranteedPick>> getPicksByDate(
            @RequestParam(required = false) String date) {

        if (date == null) {
            date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        log.info("Getting guaranteed picks for date: {}", date);

        try {
            List<GuaranteedPick> picks = guaranteedExplosiveService.getPicksByDate(date);
            return ResponseEntity.ok(picks);
        } catch (Exception e) {
            log.error("Error getting picks for date: {}", date, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get picks that need performance tracking
     * GET /api/guaranteed/needs-tracking
     */
    @GetMapping("/needs-tracking")
    public ResponseEntity<List<GuaranteedPick>> getPicksNeedingTracking() {
        log.info("Getting picks needing performance tracking");

        try {
            List<GuaranteedPick> picks = guaranteedExplosiveService.getPicksNeedingTracking();
            log.info("Found {} picks needing tracking", picks.size());
            return ResponseEntity.ok(picks);
        } catch (Exception e) {
            log.error("Error getting picks needing tracking", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get ALL stocks with explosive scores for grid display
     * GET /api/guaranteed/all-scores
     */
    @GetMapping("/all-scores")
    public ResponseEntity<List<ExplosiveScoreDTO>> getAllExplosiveScores() {
        log.info("Getting explosive scores for all stocks");

        try {
            List<ExplosiveScoreDTO> scores =
                guaranteedExplosiveService.getAllExplosiveScores();
            log.info("Analyzed {} stocks with explosive scores", scores.size());
            return ResponseEntity.ok(scores);
        } catch (Exception e) {
            log.error("Error getting all explosive scores", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Manually trigger performance tracking for all pending picks
     * POST /api/guaranteed/track-performance
     */
    @PostMapping("/track-performance")
    public ResponseEntity<String> trackPerformance() {
        log.info("Manual trigger: tracking performance for all pending picks");

        try {
            performanceTrackingService.trackAllPendingPicks();
            return ResponseEntity.ok("Performance tracking completed");
        } catch (Exception e) {
            log.error("Error tracking performance", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get detailed performance statistics
     * GET /api/guaranteed/performance-stats
     */
    @GetMapping("/performance-stats")
    public ResponseEntity<PerformanceTrackingService.PerformanceStats> getDetailedStats() {
        log.info("Getting detailed performance statistics");

        try {
            PerformanceTrackingService.PerformanceStats stats =
                performanceTrackingService.getPerformanceStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting performance stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all guaranteed picks (all dates) for performance history page
     * GET /api/guaranteed/picks/all
     */
    @GetMapping("/picks/all")
    public ResponseEntity<List<GuaranteedPick>> getAllPicks() {
        log.info("Getting all guaranteed picks");

        try {
            List<GuaranteedPick> allPicks = guaranteedExplosiveService.getGuaranteedPicks();

            // Sort by date descending (newest first)
            allPicks.sort((a, b) -> b.getDate().compareTo(a.getDate()));

            log.info("Found {} total picks", allPicks.size());
            return ResponseEntity.ok(allPicks);
        } catch (Exception e) {
            log.error("Error getting all picks", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Save a stock from explosive-scores-grid as a guaranteed pick
     * POST /api/guaranteed/save-pick
     * Body: { ticker, entryPrice, factorsPassed, convergenceScore, confidenceLevel, passedFactors, failedFactors }
     */
    @PostMapping("/save-pick")
    public ResponseEntity<String> savePickFromGrid(@RequestBody GuaranteedCandidateDTO candidate) {
        log.info("Saving pick from grid: {}", candidate.getStock().getTicker());

        try {
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);

            GuaranteedPick pick = new GuaranteedPick();
            pick.setDate(today);
            pick.setTicker(candidate.getStock().getTicker());
            pick.setRank(99); // Manual picks get rank 99 (not from top 3)
            pick.setEntryPrice(candidate.getStock().getPrice());
            pick.setFactorsPassed(candidate.getFactorsPassed());
            pick.setConvergenceScore(candidate.getConvergenceScore());
            pick.setConfidenceLevel(candidate.getConfidenceLevel());
            pick.setPassedFactors(candidate.getPassedFactors());
            pick.setFailedFactors(candidate.getFailedFactors());
            pick.setTrackingDate(java.time.LocalDate.now().plusDays(5));

            guaranteedExplosiveService.getGuaranteedPickRepository().save(pick);

            log.info("✓ Saved manual pick: {}", pick.getTicker());
            return ResponseEntity.ok("✓ Saved " + pick.getTicker() + " to guaranteed picks!");

        } catch (Exception e) {
            log.error("Error saving pick from grid", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}


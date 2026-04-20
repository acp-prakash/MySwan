package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.GuaranteedPick;
import org.myswan.model.collection.Stock;
import org.myswan.repository.GuaranteedPickRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service to track performance of guaranteed picks after 5 days
 * Automatically runs daily to validate if picks actually exploded
 */
@Slf4j
@Service
public class PerformanceTrackingService {

    private final GuaranteedPickRepository guaranteedPickRepository;
    private final StockService stockService;
    private final GuaranteedExplosiveService guaranteedExplosiveService;

    // Success thresholds
    private static final double MAX_SUCCESS_THRESHOLD = 15.0; // 15%+ peak gain = MAX_SUCCESS
    private static final double SUCCESS_THRESHOLD     = 10.0; // 10-14.9% peak gain = SUCCESS
    // Below 10% = FAIL  (old PARTIAL tier removed)

    public PerformanceTrackingService(GuaranteedPickRepository guaranteedPickRepository,
                                     StockService stockService,
                                     GuaranteedExplosiveService guaranteedExplosiveService) {
        this.guaranteedPickRepository = guaranteedPickRepository;
        this.stockService = stockService;
        this.guaranteedExplosiveService = guaranteedExplosiveService;
    }

    /**
     * Scheduled job runs daily at 6 PM (after market close + data updates)
     * Checks all picks that need tracking and measures their performance
     */
    @Scheduled(cron = "0 0 18 * * *") // Daily at 6 PM
    public void trackDailyPerformance() {
        log.info("=== Starting daily performance tracking ===");

        List<GuaranteedPick> picksToTrack = guaranteedExplosiveService.getPicksNeedingTracking();
        if (picksToTrack.isEmpty()) { log.info("No picks need tracking today"); return; }
        log.info("Found {} picks to track", picksToTrack.size());

        int maxSuccessCount = 0, successCount = 0, failCount = 0;
        for (GuaranteedPick pick : picksToTrack) {
            try {
                boolean updated = trackPickPerformance(pick);
                if (updated) {
                    switch (pick.getOutcome()) {
                        case "MAX_SUCCESS" -> maxSuccessCount++;
                        case "SUCCESS"     -> successCount++;
                        case "FAIL"        -> failCount++;
                    }
                }
            } catch (Exception e) {
                log.error("Error tracking pick for {}: {}", pick.getTicker(), e.getMessage());
            }
        }

        log.info("=== Performance tracking complete ===");
        log.info("MAX_SUCCESS: {} picks (≥{}% gain)", maxSuccessCount, (int) MAX_SUCCESS_THRESHOLD);
        log.info("SUCCESS:     {} picks (≥{}% gain)", successCount,    (int) SUCCESS_THRESHOLD);
        log.info("FAIL:        {} picks (<{}% gain)",  failCount,       (int) SUCCESS_THRESHOLD);
    }

    /**
     * Track performance for a single pick
     * Fetches current price and calculates if it exploded
     */
    public boolean trackPickPerformance(GuaranteedPick pick) {
        log.info("Tracking performance for {} (picked on {})", pick.getTicker(), pick.getDate());

        List<Stock> stocks = stockService.list();
        Optional<Stock> currentStockOpt = stocks.stream()
            .filter(s -> s.getTicker().equals(pick.getTicker()))
            .findFirst();

        if (currentStockOpt.isEmpty()) {
            log.warn("Stock {} not found in current data, skipping", pick.getTicker());
            return false;
        }

        Stock currentStock = currentStockOpt.get();
        double currentPrice = currentStock.getPrice();
        double entryPrice   = pick.getEntryPrice();

        double gainPct    = ((currentPrice - entryPrice) / entryPrice) * 100;
        double maxPrice   = getMaxPriceSinceEntry(pick.getTicker(), pick.getDate());
        double maxGainPct = maxPrice > 0 ? ((maxPrice - entryPrice) / entryPrice) * 100 : gainPct;

        // ── New three-tier outcome ──
        // MAX_SUCCESS : peak ≥ 15%
        // SUCCESS     : peak ≥ 10%  (< 15%)
        // FAIL        : peak < 10%
        String outcome;
        boolean moved15Percent;
        int daysToMove = calculateDaysToThreshold(pick.getTicker(), pick.getDate(), entryPrice);

        if (maxGainPct >= MAX_SUCCESS_THRESHOLD) {
            outcome        = "MAX_SUCCESS";
            moved15Percent = true;
        } else if (maxGainPct >= SUCCESS_THRESHOLD) {
            outcome        = "SUCCESS";
            moved15Percent = false;
        } else {
            outcome        = "FAIL";
            moved15Percent = false;
        }

        pick.setMaxPriceReached(maxPrice > 0 ? maxPrice : currentPrice);
        pick.setMaxGainPct(maxGainPct);
        pick.setFinalPrice(currentPrice);
        pick.setFinalGainPct(gainPct);
        pick.setMoved15Percent(moved15Percent);
        pick.setDaysToMove(daysToMove);
        pick.setOutcome(outcome);
        pick.setTracked(true);

        guaranteedPickRepository.save(pick);

        log.info("✓ {} — Entry:${} Max:${} (+{}%) Final:${} (+{}%) → {}",
            pick.getTicker(),
            String.format("%.2f", entryPrice),
            String.format("%.2f", maxPrice > 0 ? maxPrice : currentPrice),
            String.format("%.2f", maxGainPct),
            String.format("%.2f", currentPrice),
            String.format("%.2f", gainPct),
            outcome);

        return true;
    }

    /**
     * Get maximum price reached since entry date
     */
    private double getMaxPriceSinceEntry(String ticker, String entryDate) {
        try {
            LocalDate startDate = LocalDate.parse(entryDate);
            LocalDate endDate = LocalDate.now();

            List<Stock> history = stockService.getStockHistory(ticker, startDate, endDate);

            if (history == null || history.isEmpty()) {
                return 0;
            }

            return history.stream()
                .mapToDouble(Stock::getHigh)
                .max()
                .orElse(0);

        } catch (Exception e) {
            log.error("Error getting max price for {}: {}", ticker, e.getMessage());
            return 0;
        }
    }

    /**
     * Calculate how many days it took to reach 15% threshold
     * Returns -1 if never reached
     */
    private int calculateDaysToThreshold(String ticker, String entryDate, double entryPrice) {
        try {
            LocalDate startDate = LocalDate.parse(entryDate);
            LocalDate endDate   = LocalDate.now();
            List<Stock> history = stockService.getStockHistory(ticker, startDate, endDate);
            if (history == null || history.isEmpty()) return -1;

            history.sort((a, b) -> a.getHistDate().compareTo(b.getHistDate()));
            double targetPrice = entryPrice * (1 + MAX_SUCCESS_THRESHOLD / 100); // days to 15%

            for (int i = 0; i < history.size(); i++) {
                if (history.get(i).getHigh() >= targetPrice) return i + 1;
            }
            return -1;
        } catch (Exception e) {
            log.error("Error calculating days to threshold for {}: {}", ticker, e.getMessage());
            return -1;
        }
    }

    /**
     * Manual trigger to track specific pick
     */
    public GuaranteedPick trackSpecificPick(String ticker, String date) {
        List<GuaranteedPick> picks = guaranteedPickRepository.findByDate(date);

        Optional<GuaranteedPick> pickOpt = picks.stream()
            .filter(p -> p.getTicker().equals(ticker))
            .findFirst();

        if (pickOpt.isEmpty()) {
            log.warn("Pick not found: {} on {}", ticker, date);
            return null;
        }

        GuaranteedPick pick = pickOpt.get();
        trackPickPerformance(pick);

        return pick;
    }

    /**
     * Track all pending picks (manual trigger for testing)
     */
    public void trackAllPendingPicks() {
        log.info("=== Manual tracking of all pending picks ===");
        trackDailyPerformance();
    }

    /**
     * Get performance summary statistics
     */
    public PerformanceStats getPerformanceStats() {
        List<GuaranteedPick> allPicks = guaranteedPickRepository.findAll();

        long totalPicks   = allPicks.size();
        long trackedPicks = allPicks.stream().filter(GuaranteedPick::isTracked).count();
        long pendingPicks = totalPicks - trackedPicks;

        long maxSuccessCount = guaranteedPickRepository.countByOutcome("MAX_SUCCESS");
        long successCount    = guaranteedPickRepository.countByOutcome("SUCCESS");
        long failCount       = guaranteedPickRepository.countByOutcome("FAIL");
        // backward-compat: old "PARTIAL" entries counted as success (≥5% was partial, now ≥10% is success)
        long legacySuccess   = guaranteedPickRepository.countByOutcome("PARTIAL");

        // Combined success rate: MAX_SUCCESS + SUCCESS (+ legacy PARTIAL) / tracked
        long totalSuccess = maxSuccessCount + successCount + legacySuccess;
        double successRate    = trackedPicks > 0 ? (double) totalSuccess       / trackedPicks * 100 : 0;
        double maxSuccessRate = trackedPicks > 0 ? (double) maxSuccessCount    / trackedPicks * 100 : 0;
        double failRate       = trackedPicks > 0 ? (double) failCount          / trackedPicks * 100 : 0;

        double avgMaxGain = allPicks.stream()
            .filter(p -> p.isTracked() && p.getMaxGainPct() != null)
            .mapToDouble(GuaranteedPick::getMaxGainPct).average().orElse(0);

        double avgFinalGain = allPicks.stream()
            .filter(p -> p.isTracked() && p.getFinalGainPct() != null)
            .mapToDouble(GuaranteedPick::getFinalGainPct).average().orElse(0);

        // avgDaysToMove = days to hit 15% on MAX_SUCCESS picks
        double avgDaysToMove = allPicks.stream()
            .filter(p -> "MAX_SUCCESS".equals(p.getOutcome())
                && p.getDaysToMove() != null && p.getDaysToMove() > 0)
            .mapToInt(GuaranteedPick::getDaysToMove).average().orElse(0);

        return new PerformanceStats(
            totalPicks, trackedPicks, pendingPicks,
            maxSuccessCount, successCount, failCount,
            successRate, maxSuccessRate, failRate,
            avgMaxGain, avgFinalGain, avgDaysToMove
        );
    }

    /**
     * Performance statistics DTO
     * successRate     = (MAX_SUCCESS + SUCCESS) / tracked  — the headline number
     * maxSuccessRate  = MAX_SUCCESS only / tracked
     */
    public static class PerformanceStats {
        public long   totalPicks;
        public long   trackedPicks;
        public long   pendingPicks;
        public long   maxSuccessCount; // peak ≥ 15%
        public long   successCount;    // peak 10–14.9%
        public long   failCount;       // peak < 10%
        public double successRate;     // (maxSuccess + success) / tracked
        public double maxSuccessRate;  // maxSuccess only / tracked
        public double failRate;
        public double avgMaxGain;
        public double avgFinalGain;
        public double avgDaysToMove;   // days to reach 15% (MAX_SUCCESS picks only)

        public PerformanceStats(long totalPicks, long trackedPicks, long pendingPicks,
                               long maxSuccessCount, long successCount, long failCount,
                               double successRate, double maxSuccessRate, double failRate,
                               double avgMaxGain, double avgFinalGain, double avgDaysToMove) {
            this.totalPicks      = totalPicks;
            this.trackedPicks    = trackedPicks;
            this.pendingPicks    = pendingPicks;
            this.maxSuccessCount = maxSuccessCount;
            this.successCount    = successCount;
            this.failCount       = failCount;
            this.successRate     = successRate;
            this.maxSuccessRate  = maxSuccessRate;
            this.failRate        = failRate;
            this.avgMaxGain      = avgMaxGain;
            this.avgFinalGain    = avgFinalGain;
            this.avgDaysToMove   = avgDaysToMove;
        }
    }
}


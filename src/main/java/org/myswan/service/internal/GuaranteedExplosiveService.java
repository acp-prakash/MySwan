package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.GuaranteedPick;
import org.myswan.model.collection.Stock;
import org.myswan.model.compute.ExplosiveScoreDTO;
import org.myswan.model.compute.GuaranteedCandidateDTO;
import org.myswan.repository.GuaranteedPickRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for detecting guaranteed explosive stocks using multi-factor convergence
 */
@Slf4j
@Service
public class GuaranteedExplosiveService {

    private final StockService stockService;
    private final GuaranteedPickRepository guaranteedPickRepository;

    // Thresholds for "guaranteed" status
    private static final int MIN_CONVERGENCE_FACTORS = 7; // Out of 10
    private static final int GUARANTEED_THRESHOLD = 80; // Percentage score

    // Price range filters
    private static final double MIN_PRICE = 2.00;
    private static final double MAX_PRICE = 50.00;
    private static final long MIN_VOLUME = 500_000;

    public GuaranteedExplosiveService(StockService stockService,
                                     GuaranteedPickRepository guaranteedPickRepository) {
        this.stockService = stockService;
        this.guaranteedPickRepository = guaranteedPickRepository;
    }

    /**
     * Find top 3 GUARANTEED explosive stocks
     */
    public List<GuaranteedCandidateDTO> findTop3Guaranteed() {
        List<Stock> allStocks = stockService.list();

        log.info("Analyzing {} stocks for guaranteed explosive moves", allStocks.size());

        List<GuaranteedCandidateDTO> candidates = allStocks.parallelStream()
            .filter(this::passesBasicFilters)
            .map(this::analyzeConvergence)
            .filter(c -> c.getConvergenceScore() >= GUARANTEED_THRESHOLD)
            .filter(c -> c.getFactorsPassed() >= MIN_CONVERGENCE_FACTORS)
            .sorted(Comparator
                .comparingInt(GuaranteedCandidateDTO::getFactorsPassed)
                .thenComparingInt(GuaranteedCandidateDTO::getConvergenceScore)
                .reversed())
            .limit(3)
            .collect(Collectors.toList());

        log.info("Found {} guaranteed candidates meeting strict criteria", candidates.size());

        // If less than 3, lower threshold slightly
        if (candidates.size() < 3) {
            log.warn("Only {} candidates met strict criteria, lowering threshold", candidates.size());
            candidates = allStocks.parallelStream()
                .filter(this::passesBasicFilters)
                .map(this::analyzeConvergence)
                .filter(c -> c.getConvergenceScore() >= 70) // Lower threshold
                .sorted(Comparator
                    .comparingInt(GuaranteedCandidateDTO::getConvergenceScore)
                    .reversed())
                .limit(3)
                .collect(Collectors.toList());
        }

        return candidates;
    }

    private boolean passesBasicFilters(Stock stock) {
        double price = stock.getPrice();
        double volume = stock.getVolume();
        double change = stock.getChange();

        // Basic quality filters
        return price >= MIN_PRICE
            && price <= MAX_PRICE
            && volume >= MIN_VOLUME
            && price > 0 && volume > 0;
    }

    /**
     * Analyze convergence of multiple factors
     */
    private GuaranteedCandidateDTO analyzeConvergence(Stock stock) {
        GuaranteedCandidateDTO candidate = new GuaranteedCandidateDTO(stock);
        int factorsPassed = 0;
        int totalPoints = 0;
        List<String> passedFactors = new ArrayList<>();
        List<String> failedFactors = new ArrayList<>();

        // Get history for analysis
        List<Stock> history = stockService.getStockHistory(
            stock.getTicker(),
            LocalDate.now().minusDays(10),
            LocalDate.now()
        );

        // Factor 1: PRICE ACTION (3 checks, 30 points)
        int priceActionResult = analyzePriceAction(stock, history, passedFactors, failedFactors);
        totalPoints += priceActionResult;
        if (priceActionResult >= 20) factorsPassed++;

        // Factor 2: VOLUME (2 checks, 20 points)
        int volumeResult = analyzeVolume(stock, history, passedFactors, failedFactors);
        totalPoints += volumeResult;
        if (volumeResult >= 10) factorsPassed++;

        // Factor 3: PATTERNS (2 checks, 15 points)
        int patternsResult = analyzePatterns(stock, passedFactors, failedFactors);
        totalPoints += patternsResult;
        if (patternsResult >= 8) factorsPassed++;

        // Factor 4: TECHNICAL INDICATORS (2 checks, 15 points)
        int technicalResult = analyzeTechnicals(stock, passedFactors, failedFactors);
        totalPoints += technicalResult;
        if (technicalResult >= 8) factorsPassed++;

        // Factor 5: MARKET STRUCTURE (2 checks, 15 points)
        int structureResult = analyzeMarketStructure(stock, passedFactors, failedFactors);
        totalPoints += structureResult;
        if (structureResult >= 8) factorsPassed++;

        // Factor 6: SCORING (2 checks, 15 points)
        int scoringResult = analyzeScoring(stock, passedFactors, failedFactors);
        totalPoints += scoringResult;
        if (scoringResult >= 8) factorsPassed++;

        // Factor 7: SIGNALS (2 checks, 15 points)
        int signalsResult = analyzeSignals(stock, passedFactors, failedFactors);
        totalPoints += signalsResult;
        if (signalsResult >= 8) factorsPassed++;

        // Factor 8: FLOAT & LIQUIDITY (1 check, 5 points)
        int liquidityResult = analyzeLiquidity(stock, passedFactors, failedFactors);
        totalPoints += liquidityResult;
        if (liquidityResult >= 5) factorsPassed++;

        // Factor 9: TODAY'S MOMENTUM (bonus check, 10 points)
        int momentumResult = analyzeTodaysMomentum(stock, passedFactors, failedFactors);
        totalPoints += momentumResult;
        if (momentumResult >= 5) factorsPassed++;

        // Factor 10: CONFIRMATION (bonus - multiple factors align)
        if (factorsPassed >= 6) {
            totalPoints += 10;
            passedFactors.add("✓ STRONG CONVERGENCE - Multiple factors aligned!");
        }

        // Calculate final score (out of 100)
        int convergenceScore = Math.min(totalPoints, 100);

        candidate.setFactorsPassed(factorsPassed);
        candidate.setConvergenceScore(convergenceScore);
        candidate.setPassedFactors(passedFactors);
        candidate.setFailedFactors(failedFactors);
        candidate.calculateConfidence();

        return candidate;
    }

    private int analyzePriceAction(Stock stock, List<Stock> history,
                                   List<String> passed, List<String> failed) {
        int points = 0;
        double changePct = (stock.getChange() / stock.getPrice()) * 100;

        // Check 1: Already moving up in last 2 days
        if (history != null && history.size() >= 2) {
            history.sort(Comparator.comparing(Stock::getHistDate));
            double price2DaysAgo = history.get(history.size() - 2).getPrice();
            double change2Day = ((stock.getPrice() - price2DaysAgo) / price2DaysAgo) * 100;

            if (change2Day >= 5.0) {
                passed.add(String.format("✓ Up %.1f%% in 2 days (momentum confirmed)", change2Day));
                points += 10;
            } else {
                failed.add("✗ Not up 5%+ in 2 days");
            }
        }

        // Check 2: Breaking resistance
        if (history != null && history.size() >= 5) {
            double recentHigh = history.stream()
                .limit(5)
                .mapToDouble(Stock::getHigh)
                .max()
                .orElse(0);

            if (stock.getPrice() > recentHigh * 1.02) {
                double breakoutPct = ((stock.getPrice() - recentHigh) / recentHigh) * 100;
                passed.add(String.format("✓ Breaking resistance (+%.1f%%)", breakoutPct));
                points += 10;
            } else {
                failed.add("✗ Not breaking resistance");
            }
        }

        // Check 3: Uptrend structure
        Integer upDays = stock.getUpDays();
        if (upDays != null && upDays >= 2) {
            passed.add("✓ " + upDays + " consecutive up days");
            points += 10;
        } else {
            failed.add("✗ No uptrend structure");
        }

        return points;
    }

    private int analyzeVolume(Stock stock, List<Stock> history,
                             List<String> passed, List<String> failed) {
        int points = 0;

        if (history != null && history.size() >= 3) {
            double avgVolume = history.stream()
                .limit(5)
                .mapToDouble(Stock::getVolume)
                .average()
                .orElse(1.0);

            double volumeRatio = stock.getVolume() / avgVolume;

            if (volumeRatio >= 3.0) {
                passed.add(String.format("✓ %.1fX volume surge (institutional interest)", volumeRatio));
                points += 10;
            } else if (volumeRatio >= 2.0) {
                passed.add(String.format("✓ Above avg volume (%.1fX)", volumeRatio));
                points += 5;
            } else {
                failed.add("✗ Volume not 3X average");
            }

            // Check volume is increasing
            if (history.size() >= 3) {
                history.sort(Comparator.comparing(Stock::getHistDate));
                double vol1 = history.get(history.size() - 1).getVolume();
                double vol2 = history.get(history.size() - 2).getVolume();

                if (stock.getVolume() > vol1 && vol1 > vol2) {
                    passed.add("✓ Volume increasing daily");
                    points += 10;
                } else {
                    failed.add("✗ Volume not increasing");
                }
            }
        }

        return points;
    }

    private int analyzePatterns(Stock stock, List<String> passed, List<String> failed) {
        int points = 0;

        Integer longPatterns = stock.getNoOfLongPatterns();
        Integer shortPatterns = stock.getNoOfShortPatterns();

        if (longPatterns != null && longPatterns >= 2) {
            passed.add("✓ " + longPatterns + " bullish patterns");
            points += 10;
        } else {
            failed.add("✗ Less than 2 bullish patterns");
        }

        if (shortPatterns != null && shortPatterns.intValue() == 0) {
            passed.add("✓ No bearish patterns");
            points += 5;
        } else {
            failed.add("✗ Bearish patterns present");
        }

        return points;
    }

    private int analyzeTechnicals(Stock stock, List<String> passed, List<String> failed) {
        int points = 0;
        double changePct = (stock.getChange() / stock.getPrice()) * 100;

        if (stock.getScore() != null) {
            int overallScore = stock.getScore().getOverallScore();
            if (overallScore >= 70) {
                passed.add("✓ High overall score: " + overallScore);
                points += 10;
            } else {
                failed.add("✗ Overall score < 70");
            }
        }

        // Check if not overbought (healthy momentum)
        if (changePct > 0 && changePct < 15) {
            passed.add("✓ Healthy momentum (not overbought)");
            points += 5;
        } else if (changePct >= 15) {
            failed.add(String.format("✗ Overbought (up %.1f%% today)", changePct));
        }

        return points;
    }

    private int analyzeMarketStructure(Stock stock, List<String> passed, List<String> failed) {
        int points = 0;

        if (stock.getBottom() != null) {
            Integer bottomConditions = stock.getBottom().getConditionsMet();
            if (bottomConditions != null && bottomConditions >= 5) {
                passed.add("✓ Bottom formed (" + bottomConditions + " conditions)");
                points += 10;
            } else {
                failed.add("✗ No strong bottom");
            }
        }

        Integer upDays = stock.getUpDays();
        if (upDays != null && upDays >= 2) {
            points += 5;
        }

        return points;
    }

    private int analyzeScoring(Stock stock, List<String> passed, List<String> failed) {
        int points = 0;

        if (stock.getSpike() != null) {
            int spikeScore = stock.getSpike().getSpikeScore();
            if (spikeScore >= 60) {
                passed.add("✓ Spike score: " + spikeScore);
                points += 10;
            } else {
                failed.add("✗ Spike score < 60");
            }
        }

        if (stock.getScore() != null) {
            int overallScore = stock.getScore().getOverallScore();
            if (overallScore >= 70) {
                points += 5;
            }
        }

        return points;
    }

    private int analyzeSignals(Stock stock, List<String> passed, List<String> failed) {
        int points = 0;

        if (stock.getScore() != null && stock.getScore().getSignal() != null) {
            String signal = stock.getScore().getSignal();
            if ("BUY".equals(signal)) {
                passed.add("✓ BUY signal active");
                points += 10;
            } else if (!"SELL".equals(signal)) {
                passed.add("✓ HOLD signal (not SELL)");
                points += 5;
            } else {
                failed.add("✗ SELL signal");
            }
        }

        return points;
    }

    private int analyzeLiquidity(Stock stock, List<String> passed, List<String> failed) {
        double price = stock.getPrice();
        double volume = stock.getVolume();

        if (price >= MIN_PRICE && price <= MAX_PRICE && volume >= 1_000_000.0) {
            passed.add("✓ Optimal price/volume range");
            return 5;
        } else {
            failed.add("✗ Not in optimal range");
            return 0;
        }
    }

    private int analyzeTodaysMomentum(Stock stock, List<String> passed, List<String> failed) {
        double changePct = (stock.getChange() / stock.getPrice()) * 100;

        if (changePct >= 3.0 && changePct <= 12.0) {
            passed.add(String.format("✓ Strong today: +%.1f%%", changePct));
            return 10;
        } else if (changePct > 0) {
            passed.add(String.format("✓ Up today: +%.1f%%", changePct));
            return 5;
        } else {
            failed.add("✗ Down today");
            return 0;
        }
    }

    /**
     * Save top 3 picks to database for tracking (ONLY if not already saved today)
     */
    public void saveTop3Picks(List<GuaranteedCandidateDTO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            log.warn("No candidates to save");
            return;
        }

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Check if picks already exist for today
        List<GuaranteedPick> existingPicks = guaranteedPickRepository.findByDate(today);
        if (!existingPicks.isEmpty()) {
            log.info("Picks already saved for {}. Skipping to prevent duplicates.", today);
            return; // Don't save again - prevents duplicates on every page refresh
        }

        // Only save if no picks exist for today
        log.info("Saving {} picks for {} (first time today)", candidates.size(), today);

        int rank = 1;
        for (GuaranteedCandidateDTO candidate : candidates) {
            GuaranteedPick pick = new GuaranteedPick();
            pick.setDate(today);
            pick.setTicker(candidate.getStock().getTicker());
            pick.setRank(rank++);
            pick.setEntryPrice(candidate.getStock().getPrice());
            pick.setFactorsPassed(candidate.getFactorsPassed());
            pick.setConvergenceScore(candidate.getConvergenceScore());
            pick.setConfidenceLevel(candidate.getConfidenceLevel());
            pick.setPassedFactors(candidate.getPassedFactors());
            pick.setFailedFactors(candidate.getFailedFactors());
            pick.setTrackingDate(LocalDate.now().plusDays(5)); // Check in 5 days

            guaranteedPickRepository.save(pick);
            log.info("✓ Saved guaranteed pick: {} (rank {}) with {} factors passed, entry price: ${}",
                     pick.getTicker(), pick.getRank(), pick.getFactorsPassed(),
                     String.format("%.2f", pick.getEntryPrice()));
        }
    }

    /**
     * Force save new picks for today (overwrites existing)
     * Use this if you want to manually refresh today's picks
     */
    public void forceSaveTop3Picks(List<GuaranteedCandidateDTO> candidates) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Delete existing picks for today
        List<GuaranteedPick> existingPicks = guaranteedPickRepository.findByDate(today);
        if (!existingPicks.isEmpty()) {
            guaranteedPickRepository.deleteAll(existingPicks);
            log.info("Deleted {} existing picks for {} (force refresh)", existingPicks.size(), today);
        }

        // Save new picks
        int rank = 1;
        for (GuaranteedCandidateDTO candidate : candidates) {
            GuaranteedPick pick = new GuaranteedPick();
            pick.setDate(today);
            pick.setTicker(candidate.getStock().getTicker());
            pick.setRank(rank++);
            pick.setEntryPrice(candidate.getStock().getPrice());
            pick.setFactorsPassed(candidate.getFactorsPassed());
            pick.setConvergenceScore(candidate.getConvergenceScore());
            pick.setConfidenceLevel(candidate.getConfidenceLevel());
            pick.setPassedFactors(candidate.getPassedFactors());
            pick.setFailedFactors(candidate.getFailedFactors());
            pick.setTrackingDate(LocalDate.now().plusDays(5));

            guaranteedPickRepository.save(pick);
            log.info("✓ Force saved pick: {} (rank {})", pick.getTicker(), pick.getRank());
        }
    }

    /**
     * Get historical performance statistics
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalPicks = guaranteedPickRepository.count();
        long successCount = guaranteedPickRepository.countByOutcome("SUCCESS");
        long partialCount = guaranteedPickRepository.countByOutcome("PARTIAL");
        long failCount = guaranteedPickRepository.countByOutcome("FAIL");
        long pendingCount = guaranteedPickRepository.countByOutcome("PENDING");

        stats.put("totalPicks", totalPicks);
        stats.put("successCount", successCount);
        stats.put("partialCount", partialCount);
        stats.put("failCount", failCount);
        stats.put("pendingCount", pendingCount);

        if (totalPicks > 0) {
            double successRate = (double) successCount / (totalPicks - pendingCount) * 100;
            stats.put("successRate", Math.round(successRate * 10) / 10.0);
        } else {
            stats.put("successRate", 0.0);
        }

        return stats;
    }

    /**
     * Get all picks for a specific date
     */
    public List<GuaranteedPick> getPicksByDate(String date) {
        return guaranteedPickRepository.findByDateOrderByRankAsc(date);
    }

    /**
     * Get picks that need tracking (past tracking date and not yet tracked)
     */
    public List<GuaranteedPick> getPicksNeedingTracking() {
        return guaranteedPickRepository.findByTrackedFalse().stream()
            .filter(pick -> pick.getTrackingDate() != null &&
                          !pick.getTrackingDate().isAfter(LocalDate.now()))
            .collect(Collectors.toList());
    }

    /**
     * Get repository (for performance tracking service)
     */
    public GuaranteedPickRepository getGuaranteedPickRepository() {
        return guaranteedPickRepository;
    }

    /**
     * Get stock service (for performance tracking service)
     */
    public StockService getStockService() {
        return stockService;
    }

    /**
     * Get ALL stocks with explosive scores for grid display
     */
    public List<ExplosiveScoreDTO> getAllExplosiveScores() {
        List<Stock> allStocks = stockService.list();

        log.info("Calculating explosive scores for {} stocks", allStocks.size());

        List<ExplosiveScoreDTO> scores = allStocks.parallelStream()
            .filter(this::passesBasicFilters)
            .map(this::calculateExplosiveScore)
            .sorted(Comparator
                .comparingInt(ExplosiveScoreDTO::getConvergenceScore)
                .reversed())
            .collect(Collectors.toList());

        log.info("Calculated explosive scores for {} qualifying stocks", scores.size());
        return scores;
    }

    /**
     * Calculate explosive score for a single stock (lighter version for grid)
     */
    private ExplosiveScoreDTO calculateExplosiveScore(Stock stock) {
        ExplosiveScoreDTO dto = new ExplosiveScoreDTO();

        // Basic info
        dto.setTicker(stock.getTicker());
        dto.setPrice(stock.getPrice());
        dto.setChange(stock.getChange());
        dto.setChangePct((stock.getChange() / stock.getPrice()) * 100);
        dto.setVolume(stock.getVolume());
        dto.setVolumeM(stock.getVolume() / 1_000_000.0);

        // Metrics
        dto.setUpDays(stock.getUpDays());
        dto.setNoOfLongPatterns(stock.getNoOfLongPatterns());
        dto.setNoOfShortPatterns(stock.getNoOfShortPatterns());

        if (stock.getSpike() != null) {
            dto.setSpikeScore(stock.getSpike().getSpikeScore());
        }

        if (stock.getScore() != null) {
            dto.setOverallScore(stock.getScore().getOverallScore());
            dto.setSignal(stock.getScore().getSignal());
        }

        // Calculate convergence
        GuaranteedCandidateDTO candidate = analyzeConvergence(stock);
        dto.setFactorsPassed(candidate.getFactorsPassed());
        dto.setConvergenceScore(candidate.getConvergenceScore());
        dto.setConfidenceLevel(candidate.getConfidenceLevel());
        dto.setConfidenceText(candidate.getConfidenceText());

        // Top 3 reasons (comma separated)
        if (candidate.getPassedFactors().size() > 0) {
            String topReasons = candidate.getPassedFactors().stream()
                .limit(3)
                .collect(Collectors.joining("; "));
            dto.setTopReasons(topReasons);
        }

        // Store full factor lists for detailed modal view
        dto.setPassedFactors(candidate.getPassedFactors());
        dto.setFailedFactors(candidate.getFailedFactors());

        return dto;
    }
}


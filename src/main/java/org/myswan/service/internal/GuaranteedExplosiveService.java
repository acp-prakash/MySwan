package org.myswan.service.internal;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.GuaranteedPick;
import org.myswan.model.collection.Stock;
import org.myswan.model.compute.ExplosiveScoreDTO;
import org.myswan.model.compute.GuaranteedCandidateDTO;
import org.myswan.repository.GuaranteedPickRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for detecting guaranteed explosive stocks using two-path
 * convergence scoring: Path A (Oversold Bounce) and Path B (Momentum Pop).
 */
@Getter
@Slf4j
@Service
public class GuaranteedExplosiveService {

    private final StockService stockService;
    private final GuaranteedPickRepository guaranteedPickRepository;

    // -------------------------------------------------------
    // Configurable thresholds — tuned via application.properties
    // -------------------------------------------------------
    @Value("${guaranteed.min.price:3.00}")
    private double minPrice;

    @Value("${guaranteed.max.price:30.00}")
    private double maxPrice;

    @Value("${guaranteed.min.volume:1000000}")
    private long minVolume;

    /** Winning-path score (0-100) required to qualify */
    @Value("${guaranteed.min.path.score:80}")
    private int minPathScore;

    /** Path A (Oversold Bounce): RSI must be <= this value */
    @Value("${guaranteed.path.a.rsi.max:35}")
    private double pathARsiMax;

    /** Path B (Momentum Pop): RSI must be >= this value */
    @Value("${guaranteed.path.b.rsi.min:45}")
    private double pathBRsiMin;

    /** Path B (Momentum Pop): RSI must be <= this value */
    @Value("${guaranteed.path.b.rsi.max:65}")
    private double pathBRsiMax;

    /** Minimum factors passed in the winning path (max 5) */
    @Value("${guaranteed.min.factors:4}")
    private int minFactors;

    /** Days after pick date before performance is checked */
    @Value("${guaranteed.tracking.days:10}")
    private int trackingDays;

    public GuaranteedExplosiveService(StockService stockService,
                                     GuaranteedPickRepository guaranteedPickRepository) {
        this.stockService = stockService;
        this.guaranteedPickRepository = guaranteedPickRepository;
    }

    // -------------------------------------------------------
    // Core: Find Top 3 Guaranteed Explosive Stocks
    // -------------------------------------------------------

    /**
     * Find top 3 GUARANTEED explosive stocks using two-path convergence.
     * No fallback threshold — quality over forced top-3.
     */
    public List<GuaranteedCandidateDTO> findTop3Guaranteed() {
        List<Stock> allStocks = stockService.list();
        log.info("=== Guaranteed Explosive Analysis: {} total stocks ===", allStocks.size());

        // Stage 1: Hard pre-filters
        List<Stock> preFiltered = allStocks.stream()
            .filter(this::passesBasicFilters)
            .collect(Collectors.toList());
        log.info("Pre-filter passed: {}/{}", preFiltered.size(), allStocks.size());

        // Stage 2: Two-path convergence scoring
        List<GuaranteedCandidateDTO> analyzed = preFiltered.parallelStream()
            .map(this::analyzeConvergence)
            .collect(Collectors.toList());

        long pathACount = analyzed.stream()
            .filter(c -> "OVERSOLD_BOUNCE".equals(c.getStrategyPath())
                && c.getConvergenceScore() >= minPathScore).count();
        long pathBCount = analyzed.stream()
            .filter(c -> "MOMENTUM_POP".equals(c.getStrategyPath())
                && c.getConvergenceScore() >= minPathScore).count();
        log.info("Path A (Oversold Bounce) qualifiers: {}", pathACount);
        log.info("Path B (Momentum Pop) qualifiers:    {}", pathBCount);

        // Stage 3: Apply score + factor thresholds, take best 3
        List<GuaranteedCandidateDTO> candidates = analyzed.stream()
            .filter(c -> c.getConvergenceScore() >= minPathScore)
            .filter(c -> c.getFactorsPassed() >= minFactors)
            .sorted(Comparator
                .comparingInt(GuaranteedCandidateDTO::getConvergenceScore)
                .reversed())
            .limit(3)
            .collect(Collectors.toList());

        log.info("Final guaranteed candidates: {}", candidates.size());
        return candidates;
    }

    // -------------------------------------------------------
    // Filter: Hard exclusions before convergence scoring
    // -------------------------------------------------------

    private boolean passesBasicFilters(Stock stock) {
        double price  = stock.getPrice();
        double volume = stock.getVolume();
        double avgVol = stock.getAvgVolume10D();

        // Data validity + avgVolume10D null-safety (prevents divide-by-zero downstream)
        if (price <= 0 || volume <= 0 || avgVol <= 0) return false;

        // Price range
        if (price < minPrice || price > maxPrice) return false;

        // Minimum volume
        if (volume < minVolume) return false;

        // Hard exclusion: chasing — stock already moved > 7% today
        double changePct = (stock.getChange() / price) * 100.0;
        if (changePct > 7.0) return false;

        // Hard exclusion: overbought RSI — no room to run
        if (stock.getRsi14() > 65.0) return false;

        // Hard exclusion: active bearish patterns
        if (stock.getNoOfShortPatterns() > 0) return false;

        // Hard exclusion: earnings within 10 days (gap-down risk)
        if (stock.getEarningDays() > 0 && stock.getEarningDays() < 10) return false;

        return true;
    }

    // -------------------------------------------------------
    // Convergence: Two-path scoring
    // -------------------------------------------------------

    /**
     * Score stock on two independent paths. The winning path (higher score)
     * determines the strategy and final convergenceScore.
     *
     * Tiered scoring on A1/B1 (spike) and A2/B2 (bounce/pop):
     *   HIGH  (≥60) → full points, counts as a factor
     *   MED   (≥40) → half points, counts as a factor
     *   LOW   (<40) → 0 points, does NOT count as a factor
     */
    private GuaranteedCandidateDTO analyzeConvergence(Stock stock) {
        GuaranteedCandidateDTO candidate = new GuaranteedCandidateDTO(stock);

        double rsi        = stock.getRsi14();
        double volRatio   = stock.getAvgVolume10D() > 0
            ? stock.getVolume() / stock.getAvgVolume10D() : 0;
        int bottomConds   = stock.getBottom() != null
            ? stock.getBottom().getConditionsMet() : 0;
        int spikeScore    = stock.getSpike()   != null ? stock.getSpike().getSpikeScore()    : 0;
        int bounceScore   = stock.getOversold() != null ? stock.getOversold().getBounceScore() : 0;
        int popScore      = stock.getMomPop()  != null ? stock.getMomPop().getPopScore()     : 0;

        // ---- Path A: Oversold Bounce ----
        int pathAScore   = 0;
        int pathAFactors = 0;
        List<String> pathAPassed = new ArrayList<>();
        List<String> pathAFailed = new ArrayList<>();

        // A1 — Spike score tiered: HIGH≥60→25pts | MED≥40→12pts | LOW→0
        if (spikeScore >= 60) {
            pathAScore += 25; pathAFactors++;
            pathAPassed.add(String.format("✓ [A] Spike HIGH (score=%d)", spikeScore));
        } else if (spikeScore >= 40) {
            pathAScore += 12; pathAFactors++;
            pathAPassed.add(String.format("~ [A] Spike MED (score=%d, partial)", spikeScore));
        } else {
            pathAFailed.add(String.format("✗ [A] Spike LOW (score=%d, need ≥40)", spikeScore));
        }

        // A2 — Oversold bounce tiered: HIGH≥60→25pts | MED≥40→12pts | LOW→0
        if (bounceScore >= 60) {
            pathAScore += 25; pathAFactors++;
            pathAPassed.add(String.format("✓ [A] Oversold Bounce HIGH (score=%d)", bounceScore));
        } else if (bounceScore >= 40) {
            pathAScore += 12; pathAFactors++;
            pathAPassed.add(String.format("~ [A] Oversold Bounce MED (score=%d, partial)", bounceScore));
        } else {
            pathAFailed.add(String.format("✗ [A] Oversold Bounce LOW (score=%d, need ≥40)", bounceScore));
        }

        // A3 — Strong bottom reversal: conditionsMet >= 5 → 20pts | >=3 → 10pts
        if (bottomConds >= 5) {
            pathAScore += 20; pathAFactors++;
            pathAPassed.add("✓ [A] Strong bottom: " + bottomConds + " conditions");
        } else if (bottomConds >= 3) {
            pathAScore += 10; pathAFactors++;
            pathAPassed.add("~ [A] Moderate bottom: " + bottomConds + " conditions (partial)");
        } else {
            pathAFailed.add("✗ [A] Bottom weak (" + bottomConds + "/3 conditions)");
        }

        // A4 — RSI deeply oversold (15 pts)
        if (rsi <= pathARsiMax) {
            pathAScore += 15; pathAFactors++;
            pathAPassed.add(String.format("✓ [A] RSI oversold: %.1f (≤%.0f)", rsi, pathARsiMax));
        } else if (rsi <= pathARsiMax + 10) {
            // Within 10 points of the threshold: partial (8 pts, counts as factor)
            pathAScore += 8; pathAFactors++;
            pathAPassed.add(String.format("~ [A] RSI near-oversold: %.1f (partial)", rsi));
        } else {
            pathAFailed.add(String.format("✗ [A] RSI not oversold: %.1f (need ≤%.0f)", rsi, pathARsiMax));
        }

        // A5 — Long patterns + volume surge (15 pts)
        boolean a5full    = stock.getNoOfLongPatterns() >= 1 && volRatio >= 3.0;
        boolean a5partial = stock.getNoOfLongPatterns() >= 1 && volRatio >= 1.5;
        if (a5full) {
            pathAScore += 15; pathAFactors++;
            pathAPassed.add(String.format("✓ [A] Patterns(%d) + Volume %.1fX",
                stock.getNoOfLongPatterns(), volRatio));
        } else if (a5partial) {
            pathAScore += 7; pathAFactors++;
            pathAPassed.add(String.format("~ [A] Patterns(%d) + Volume %.1fX (partial)",
                stock.getNoOfLongPatterns(), volRatio));
        } else {
            pathAFailed.add(String.format("✗ [A] Patterns(%d) / Volume %.1fX (need ≥1 pat + 1.5X vol)",
                stock.getNoOfLongPatterns(), volRatio));
        }

        // ---- Path B: Momentum Pop ----
        int pathBScore   = 0;
        int pathBFactors = 0;
        List<String> pathBPassed = new ArrayList<>();
        List<String> pathBFailed = new ArrayList<>();

        // B1 — Spike score tiered: HIGH≥60→25pts | MED≥40→12pts | LOW→0
        if (spikeScore >= 60) {
            pathBScore += 25; pathBFactors++;
            pathBPassed.add(String.format("✓ [B] Spike HIGH (score=%d)", spikeScore));
        } else if (spikeScore >= 40) {
            pathBScore += 12; pathBFactors++;
            pathBPassed.add(String.format("~ [B] Spike MED (score=%d, partial)", spikeScore));
        } else {
            pathBFailed.add(String.format("✗ [B] Spike LOW (score=%d, need ≥40)", spikeScore));
        }

        // B2 — Momentum pop tiered: HIGH≥60→25pts | MED≥40→12pts | LOW→0
        if (popScore >= 60) {
            pathBScore += 25; pathBFactors++;
            pathBPassed.add(String.format("✓ [B] Momentum Pop HIGH (popScore=%d)", popScore));
        } else if (popScore >= 40) {
            pathBScore += 12; pathBFactors++;
            pathBPassed.add(String.format("~ [B] Momentum Pop MED (popScore=%d, partial)", popScore));
        } else {
            pathBFailed.add(String.format("✗ [B] Momentum Pop LOW (popScore=%d, need ≥40)", popScore));
        }

        // B3 — >= 2 long patterns + BUY signal (20 pts) | 1 pattern + BUY (10 pts)
        boolean b3full    = stock.getNoOfLongPatterns() >= 2
            && stock.getScore() != null && "BUY".equals(stock.getScore().getSignal());
        boolean b3partial = stock.getNoOfLongPatterns() >= 1
            && stock.getScore() != null && "BUY".equals(stock.getScore().getSignal());
        if (b3full) {
            pathBScore += 20; pathBFactors++;
            pathBPassed.add("✓ [B] " + stock.getNoOfLongPatterns() + " long patterns + BUY signal");
        } else if (b3partial) {
            pathBScore += 10; pathBFactors++;
            pathBPassed.add("~ [B] 1 long pattern + BUY signal (partial)");
        } else {
            pathBFailed.add(String.format("✗ [B] Need ≥1 pattern + BUY (have %d, signal=%s)",
                stock.getNoOfLongPatterns(),
                stock.getScore() != null ? stock.getScore().getSignal() : "none"));
        }

        // B4 — RSI in momentum zone (15 pts)
        boolean b4 = rsi >= pathBRsiMin && rsi <= pathBRsiMax;
        if (b4) {
            pathBScore += 15; pathBFactors++;
            pathBPassed.add(String.format("✓ [B] RSI momentum zone: %.1f (%.0f–%.0f)",
                rsi, pathBRsiMin, pathBRsiMax));
        } else if (rsi >= pathBRsiMin - 5 && rsi <= pathBRsiMax + 5) {
            // Within 5 points of zone edges: partial
            pathBScore += 7; pathBFactors++;
            pathBPassed.add(String.format("~ [B] RSI near-momentum zone: %.1f (partial)", rsi));
        } else {
            pathBFailed.add(String.format("✗ [B] RSI outside momentum zone: %.1f (need %.0f–%.0f)",
                rsi, pathBRsiMin, pathBRsiMax));
        }

        // B5 — Moderate bottom + gate pass (15 pts) | bottom only (7 pts)
        boolean gatePass = stock.getGateSignal() != null && stock.getGateSignal().isGatePass();
        boolean b5full    = bottomConds >= 3 && gatePass;
        boolean b5partial = bottomConds >= 2;
        if (b5full) {
            pathBScore += 15; pathBFactors++;
            pathBPassed.add("✓ [B] Gate pass + bottom: " + bottomConds + " conditions");
        } else if (b5partial) {
            pathBScore += 7; pathBFactors++;
            pathBPassed.add(String.format("~ [B] Bottom %d conditions (gate=%s, partial)",
                bottomConds, gatePass ? "pass" : "fail"));
        } else {
            pathBFailed.add(String.format("✗ [B] Bottom too weak (%d conds, gate=%s)",
                bottomConds, gatePass ? "pass" : "fail"));
        }

        // ---- Pick the winning path ----
        String strategyPath;
        int convergenceScore;
        int factorsPassed;
        List<String> passedFactors;
        List<String> failedFactors;

        if (pathAScore >= pathBScore) {
            strategyPath   = "OVERSOLD_BOUNCE";
            convergenceScore = pathAScore;
            factorsPassed  = pathAFactors;
            passedFactors  = pathAPassed;
            failedFactors  = pathAFailed;
        } else {
            strategyPath   = "MOMENTUM_POP";
            convergenceScore = pathBScore;
            factorsPassed  = pathBFactors;
            passedFactors  = pathBPassed;
            failedFactors  = pathBFailed;
        }

        candidate.setStrategyPath(strategyPath);
        candidate.setConvergenceScore(convergenceScore);
        candidate.setFactorsPassed(factorsPassed);
        candidate.setPassedFactors(passedFactors);
        candidate.setFailedFactors(failedFactors);
        candidate.calculateConfidence();

        return candidate;
    }

    // -------------------------------------------------------
    // Persistence: Save picks to DB
    // -------------------------------------------------------

    /**
     * Save top 3 picks to database (ONLY if not already saved today).
     */
    public void saveTop3Picks(List<GuaranteedCandidateDTO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            log.warn("No candidates to save");
            return;
        }

        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<GuaranteedPick> existingPicks = guaranteedPickRepository.findByDate(today);
        if (!existingPicks.isEmpty()) {
            log.info("Picks already saved for {}. Skipping to prevent duplicates.", today);
            return;
        }

        log.info("Saving {} picks for {} (first time today)", candidates.size(), today);
        int rank = 1;
        for (GuaranteedCandidateDTO candidate : candidates) {
            guaranteedPickRepository.save(buildPick(candidate, today, rank++));
        }
    }

    /**
     * Force-save new picks for today (overwrites existing).
     */
    public void forceSaveTop3Picks(List<GuaranteedCandidateDTO> candidates) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<GuaranteedPick> existingPicks = guaranteedPickRepository.findByDate(today);
        if (!existingPicks.isEmpty()) {
            guaranteedPickRepository.deleteAll(existingPicks);
            log.info("Deleted {} existing picks for {} (force refresh)", existingPicks.size(), today);
        }

        int rank = 1;
        for (GuaranteedCandidateDTO candidate : candidates) {
            GuaranteedPick pick = buildPick(candidate, today, rank++);
            guaranteedPickRepository.save(pick);
            log.info("✓ Force saved: {} (rank {}, strategy={})", pick.getTicker(), pick.getRank(), pick.getStrategyPath());
        }
    }

    private GuaranteedPick buildPick(GuaranteedCandidateDTO candidate, String date, int rank) {
        GuaranteedPick pick = new GuaranteedPick();
        pick.setDate(date);
        pick.setTicker(candidate.getStock().getTicker());
        pick.setRank(rank);
        pick.setEntryPrice(candidate.getStock().getPrice());
        pick.setFactorsPassed(candidate.getFactorsPassed());
        pick.setConvergenceScore(candidate.getConvergenceScore());
        pick.setConfidenceLevel(candidate.getConfidenceLevel());
        pick.setStrategyPath(candidate.getStrategyPath());
        pick.setPassedFactors(candidate.getPassedFactors());
        pick.setFailedFactors(candidate.getFailedFactors());
        pick.setTrackingDate(LocalDate.now().plusDays(trackingDays));
        log.info("✓ Saved: {} (rank {}, strategy={}, score={}, factors={}, entry=${})",
            pick.getTicker(), pick.getRank(), pick.getStrategyPath(),
            pick.getConvergenceScore(), pick.getFactorsPassed(),
            String.format("%.2f", pick.getEntryPrice()));
        return pick;
    }

    // -------------------------------------------------------
    // Statistics & Queries (unchanged from original)
    // -------------------------------------------------------

    /**
     * Get historical performance statistics
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        long totalPicks      = guaranteedPickRepository.count();
        long maxSuccessCount = guaranteedPickRepository.countByOutcome("MAX_SUCCESS");
        long successCount    = guaranteedPickRepository.countByOutcome("SUCCESS");
        long failCount       = guaranteedPickRepository.countByOutcome("FAIL");
        long pendingCount    = guaranteedPickRepository.countByOutcome("PENDING");
        // backward-compat: old PARTIAL (≥5%) counts as success in headline rate
        long legacyPartial   = guaranteedPickRepository.countByOutcome("PARTIAL");

        stats.put("totalPicks",      totalPicks);
        stats.put("maxSuccessCount", maxSuccessCount);
        stats.put("successCount",    successCount);
        stats.put("failCount",       failCount);
        stats.put("pendingCount",    pendingCount);

        long tracked = totalPicks - pendingCount;
        long totalSuccess = maxSuccessCount + successCount + legacyPartial;
        double successRate = tracked > 0 ? (double) totalSuccess / tracked * 100 : 0.0;
        stats.put("successRate", Math.round(successRate * 10) / 10.0);
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
            .filter(pick -> pick.getTrackingDate() != null
                && !pick.getTrackingDate().isAfter(LocalDate.now()))
            .collect(Collectors.toList());
    }

    /**
     * Get repository (for performance tracking service)
     */
    public List<GuaranteedPick> getGuaranteedPicks() {
        List<Stock> stocks = stockService.list();
        List<GuaranteedPick> list = guaranteedPickRepository.findAll();
        for (GuaranteedPick pick : list) {
            for (Stock stock : stocks) {
                if (pick.getTicker().equals(stock.getTicker())) {
                    pick.setStock(stock);
                    break;
                }
            }
        }
        return list;
    }

    // -------------------------------------------------------
    // Explosive Scores Grid (all stocks)
    // -------------------------------------------------------

    /**
     * Get ALL stocks with explosive scores for grid display
     */
    public List<ExplosiveScoreDTO> getAllExplosiveScores() {
        List<Stock> allStocks = stockService.list();
        log.info("Calculating explosive scores for {} stocks", allStocks.size());

        List<ExplosiveScoreDTO> scores = allStocks.parallelStream()
            .filter(this::passesBasicFilters)
            .map(this::calculateExplosiveScore)
            .sorted(Comparator.comparingInt(ExplosiveScoreDTO::getConvergenceScore).reversed())
            .collect(Collectors.toList());

        log.info("Calculated explosive scores for {} qualifying stocks", scores.size());
        return scores;
    }

    /**
     * Calculate explosive score for a single stock (lighter version for grid)
     */
    private ExplosiveScoreDTO calculateExplosiveScore(Stock stock) {
        ExplosiveScoreDTO dto = new ExplosiveScoreDTO();
        dto.setTicker(stock.getTicker());
        dto.setPrice(stock.getPrice());
        dto.setChange(stock.getChange());
        dto.setChangePct((stock.getChange() / stock.getPrice()) * 100);
        dto.setVolume(stock.getVolume());
        dto.setVolumeM(stock.getVolume() / 1_000_000.0);
        dto.setUpDays(stock.getUpDays());
        dto.setNoOfLongPatterns(stock.getNoOfLongPatterns());
        dto.setNoOfShortPatterns(stock.getNoOfShortPatterns());

        if (stock.getSpike() != null)  dto.setSpikeScore(stock.getSpike().getSpikeScore());
        if (stock.getScore() != null) {
            dto.setOverallScore(stock.getScore().getOverallScore());
            dto.setSignal(stock.getScore().getSignal());
        }

        GuaranteedCandidateDTO candidate = analyzeConvergence(stock);
        dto.setFactorsPassed(candidate.getFactorsPassed());
        dto.setConvergenceScore(candidate.getConvergenceScore());
        dto.setConfidenceLevel(candidate.getConfidenceLevel());
        dto.setConfidenceText(candidate.getConfidenceText());
        dto.setStrategyPath(candidate.getStrategyPath());

        if (!candidate.getPassedFactors().isEmpty()) {
            dto.setTopReasons(candidate.getPassedFactors().stream()
                .limit(3).collect(Collectors.joining("; ")));
        }
        dto.setPassedFactors(candidate.getPassedFactors());
        dto.setFailedFactors(candidate.getFailedFactors());

        return dto;
    }
}


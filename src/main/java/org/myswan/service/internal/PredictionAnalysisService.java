package org.myswan.service.internal;

import org.myswan.model.collection.Stock;
import org.myswan.model.compute.MetricsInfo;
import org.myswan.model.dto.ComboAnalysis;
import org.myswan.model.dto.PredictionAnalysisResponse;
import org.myswan.model.dto.TimeHorizonAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to analyze stock history metrics and find best factor combinations
 * for each time horizon (D1-D10)
 */
@Service
public class PredictionAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(PredictionAnalysisService.class);
    private final MongoTemplate mongoTemplate;

    public PredictionAnalysisService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Performs comprehensive analysis to find best combinations
     */
    public PredictionAnalysisResponse analyzeMetrics(double threshold) {
        log.info("========================================");
        log.info("Starting prediction analysis... CODE VERSION: 2024-12-28-V16");
        log.info("FILTERS: Scores >= 60, Requires signals OR score>=70, Success rate >= 15%");
        log.info("BUG FIX: Corrected MOMENTUM signal detection (was checking wrong field)");
        log.info("THRESHOLD: Analyzing with {}% price change threshold", threshold);
        log.info("========================================");

        PredictionAnalysisResponse response = new PredictionAnalysisResponse();
        response.setAnalysisDate(LocalDate.now().toString());

        // Fetch all stock history with metricsInfo
        Query query = Query.query(Criteria.where("metricsInfo").exists(true));
        List<Stock> stocks = mongoTemplate.find(query, Stock.class, "stockHistory");

        if (stocks.isEmpty()) {
            log.warn("No stock history found with metricsInfo. Run DayChange computation first.");
            return response;
        }

        response.setTotalRecordsAnalyzed(stocks.size());
        log.info("Analyzing {} stock records with metrics", stocks.size());

        // Analyze each time horizon (D1-D10)
        double bestOverallWinRate = 0;
        double bestOverallAvgReturn = 0;
        String bestOverallHorizon = "";
        String bestOverallCombo = "";

        for (int day = 1; day <= 10; day++) {
            log.info("Analyzing Day +{}", day);

            TimeHorizonAnalysis horizonAnalysis = analyzeTimeHorizon(stocks, day);
            response.getTimeHorizonResults().add(horizonAnalysis);

            // Find top combos for this horizon
            PredictionAnalysisResponse.DetailedComboAnalysis detailedAnalysis =
                analyzeDetailedCombos(stocks, day);
            response.getDetailedResults().add(detailedAnalysis);

            // Track best overall
            if (horizonAnalysis.getWinPercentage() > bestOverallWinRate ||
                (horizonAnalysis.getWinPercentage() == bestOverallWinRate &&
                 horizonAnalysis.getAvgReturn() > bestOverallAvgReturn)) {
                bestOverallWinRate = horizonAnalysis.getWinPercentage();
                bestOverallAvgReturn = horizonAnalysis.getAvgReturn();
                bestOverallHorizon = horizonAnalysis.getTimeHorizon();
                bestOverallCombo = horizonAnalysis.getBestCombo();
            }
        }

        response.setBestOverallTimeHorizon(bestOverallHorizon);
        response.setBestOverallCombo(bestOverallCombo);
        response.setBestWinRate(bestOverallWinRate);
        response.setBestAvgReturn(bestOverallAvgReturn);

        log.info("Analysis complete. Best: {} with {}% win rate, {}% avg return",
                 bestOverallHorizon, bestOverallWinRate, bestOverallAvgReturn);

        // NEW: Analyze peak returns (highest return achieved on ANY day D1-D10)
        log.info("Analyzing peak returns (max return on any day D1-D10)...");
        List<PredictionAnalysisResponse.PeakAnalysis> peakAnalysis = analyzePeakReturns(stocks, threshold);
        response.setPeakAnalysisResults(peakAnalysis);

        if (!peakAnalysis.isEmpty()) {
            PredictionAnalysisResponse.PeakAnalysis best = peakAnalysis.getFirst();
            response.setBestPeakCombo(best.getCombo());
            response.setBestPeakReturn(best.getPeakReturn());
            response.setBestPeakDay(best.getPeakDay());
            log.info("Best peak: {} achieved {}% on {}", best.getCombo(), best.getPeakReturn(), best.getPeakDay());
        }

        return response;
    }

    /**
     * Analyzes which tickers consistently achieve threshold%+ gains
     */
    public List<Map<String, Object>> analyzeTickerPerformance(double threshold) {
        log.info("========================================");
        log.info("Starting ticker performance analysis with threshold: {}%...", threshold);
        log.info("========================================");

        // Fetch all stocks with metrics
        Query query = new Query();
        query.addCriteria(Criteria.where("metricsInfo").exists(true));
        List<Stock> stocks = mongoTemplate.find(query, Stock.class, "stockHistory");

        log.info("Total stocks with metrics: {}", stocks.size());

        // Map: ticker -> [total occurrences, 20%+ wins]
        Map<String, int[]> tickerStats = new HashMap<>();

        for (Stock stock : stocks) {
            MetricsInfo metrics = stock.getMetricsInfo();
            if (metrics == null || stock.getTicker() == null) continue;

            String ticker = stock.getTicker();

            // Check if this stock achieved threshold%+ on any day (D1-D10)
            boolean achievedPlus = false;
            for (int day = 1; day <= 10; day++) {
                Double dayReturn = getDayReturn(metrics, day);
                if (dayReturn != null && dayReturn >= threshold) {
                    achievedPlus = true;
                    break;
                }
            }

            // Track stats: [0]=total, [1]=wins
            tickerStats.putIfAbsent(ticker, new int[]{0, 0});
            tickerStats.get(ticker)[0]++; // Increment total
            if (achievedPlus) {
                tickerStats.get(ticker)[1]++; // Increment wins
            }
        }

        // Build results list
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : tickerStats.entrySet()) {
            String ticker = entry.getKey();
            int total = entry.getValue()[0];
            int wins = entry.getValue()[1];

            if (total < 5) continue; // Minimum 5 occurrences

            // Calculate reliability score (0-100 scale)
            // Formula: Success Rate (0-100) * log10(sample size factor)
            double successRate = (wins * 100.0) / total;
            double sampleSizeFactor = Math.log10(Math.max(total, 10));
            double reliabilityScore = successRate * sampleSizeFactor;

            Map<String, Object> tickerResult = new HashMap<>();
            tickerResult.put("ticker", ticker);
            tickerResult.put("totalAppeared", total);
            tickerResult.put("achievedPlus", wins);
            tickerResult.put("successRate", Math.round(successRate * 100.0) / 100.0);
            tickerResult.put("reliabilityScore", Math.min(100.0, Math.round(reliabilityScore * 100.0) / 100.0));

            results.add(tickerResult);
        }

        // Sort by wins descending
        results.sort((a, b) -> Integer.compare(
            (Integer)b.get("achievedPlus"),
            (Integer)a.get("achievedPlus")
        ));

        log.info("Ticker analysis complete. Found {} tickers with 5+ occurrences", results.size());

        // Return top 50
        return results.stream().limit(50).collect(Collectors.toList());
    }

    /**
     * Get detailed stats for a specific ticker with configurable threshold
     */
    public Map<String, Object> getTickerStats(String ticker, double threshold) {
        log.info("Fetching stats for ticker: {} with threshold: {}%", ticker, threshold);

        // Fetch all history for this ticker
        Query query = new Query();
        query.addCriteria(Criteria.where("ticker").is(ticker));
        query.addCriteria(Criteria.where("metricsInfo").exists(true));
        List<Stock> stocks = mongoTemplate.find(query, Stock.class, "stockHistory");

        if (stocks == null || stocks.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("ticker", ticker);
            result.put("error", "No data found for ticker: " + ticker);
            return result;
        }

        log.info("Found {} records for ticker: {}", stocks.size(), ticker);

        // Calculate overall ticker stats
        int totalAppeared = stocks.size();
        int achievedThreshold = 0;

        // Track combo performance for this ticker
        Map<String, int[]> comboStats = new HashMap<>(); // [total, wins]

        for (Stock stock : stocks) {
            MetricsInfo metrics = stock.getMetricsInfo();
            if (metrics == null) continue;

            // Check if achieved threshold%+
            boolean hitThreshold = false;
            for (int day = 1; day <= 10; day++) {
                Double dayReturn = getDayReturn(metrics, day);
                if (dayReturn != null && dayReturn >= threshold) {
                    hitThreshold = true;
                    achievedThreshold++;
                    break;
                }
            }

            // Track combo performance
            List<String> comboKeys = createComboVariations(metrics.getDay0Factors());
            for (String combo : comboKeys) {
                comboStats.putIfAbsent(combo, new int[]{0, 0});
                comboStats.get(combo)[0]++; // Total
                if (hitThreshold) {
                    comboStats.get(combo)[1]++; // Wins
                }
            }
        }

        // Build combo performance list
        List<Map<String, Object>> combos = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : comboStats.entrySet()) {
            if (entry.getValue()[0] < 3) continue; // Minimum 3 occurrences

            Map<String, Object> combo = new HashMap<>();
            int total = entry.getValue()[0];
            int wins = entry.getValue()[1];
            double successRate = (wins * 100.0) / total;

            // Calculate reliability score (0-100 scale)
            double sampleSizeFactor = Math.log10(Math.max(total, 10));
            double reliabilityScore = successRate * sampleSizeFactor;

            combo.put("combo", entry.getKey());
            combo.put("totalAppeared", total);
            combo.put("achievedThreshold", wins);
            combo.put("successRate", Math.round(successRate * 100.0) / 100.0);
            combo.put("reliabilityScore", Math.min(100.0, Math.round(reliabilityScore * 100.0) / 100.0));

            combos.add(combo);
        }

        // Sort by wins descending
        combos.sort((a, b) -> Integer.compare((Integer)b.get("achievedThreshold"), (Integer)a.get("achievedThreshold")));

        // Build result
        double overallSuccessRate = (achievedThreshold * 100.0 / totalAppeared);
        double overallSampleFactor = Math.log10(Math.max(totalAppeared, 10));
        double overallReliabilityScore = overallSuccessRate * overallSampleFactor;

        Map<String, Object> result = new HashMap<>();
        result.put("ticker", ticker);
        result.put("threshold", threshold);
        result.put("totalAppeared", totalAppeared);
        result.put("achievedThreshold", achievedThreshold);
        result.put("successRate", Math.round(overallSuccessRate * 100.0) / 100.0);
        result.put("reliabilityScore", Math.min(100.0, Math.round(overallReliabilityScore * 100.0) / 100.0));
        result.put("bestCombos", combos.stream().limit(20).collect(Collectors.toList()));

        // Calculate rank among all tickers (using 20% for global ranking)
        List<Map<String, Object>> allTickers = analyzeTickerPerformance(20.0);
        int rank = 1;
        for (Map<String, Object> t : allTickers) {
            if (t.get("ticker").equals(ticker)) {
                break;
            }
            rank++;
        }
        result.put("rank", rank <= allTickers.size() ? rank : "Not in Top 50");

        log.info("Ticker {} stats with {}% threshold: Total={}, Wins={}, Success Rate={}%, Best Combos={}",
                 ticker, threshold, totalAppeared, achievedThreshold,
                 result.get("successRate"), combos.size());

        return result;
    }

    /**
     * Analyzes a specific time horizon (D1, D2, etc.)
     */
    private TimeHorizonAnalysis analyzeTimeHorizon(List<Stock> stocks, int dayNumber) {
        TimeHorizonAnalysis analysis = new TimeHorizonAnalysis();
        analysis.setTimeHorizon("Day +" + dayNumber);

        // Group by factor combinations - use variations for diversity
        Map<String, List<Double>> comboReturns = new HashMap<>();

        // Debug: Track what combos are being created
        Set<String> uniqueCombos = new HashSet<>();

        for (Stock stock : stocks) {
            MetricsInfo metrics = stock.getMetricsInfo();
            if (metrics == null || metrics.getDay0Factors() == null) continue;

            // Get return for this day
            Double dayReturn = getDayReturn(metrics, dayNumber);
            if (dayReturn == null) continue;

            // Create MULTIPLE combo variations for each stock (same as detailed analysis)
            List<String> comboKeys = createComboVariations(metrics.getDay0Factors());

            // Debug: Log first stock's variations with FULL details
            if (uniqueCombos.isEmpty()) {
                log.info("========== Day +{} DEBUG ==========", dayNumber);
                log.info("Sample stock ALL factors ({}): {}",
                         metrics.getDay0Factors().size(),
                         metrics.getDay0Factors());
                log.info("Variations created: {}", comboKeys);

                // Show what was detected
                boolean hasSpike = metrics.getDay0Factors().stream().anyMatch(f ->
                    f.contains("SPIKE-ISSPIKE=true") || f.contains("SPIKE-ISSPIKE-true"));
                boolean hasBottom = metrics.getDay0Factors().stream().anyMatch(f ->
                    f.contains("BOTTOM-ISBOTTOM=true") || f.contains("BOTTOM-ISBOTTOM-true"));
                boolean hasPattern = metrics.getDay0Factors().stream().anyMatch(f ->
                    (f.contains("PATTERN-LONG=") || f.contains("PATTERN-LONG-")) &&
                    !f.contains("=0") && !f.contains("-0"));

                log.info("Detected: SPIKE={}, BOTTOM={}, PATTERN={}", hasSpike, hasBottom, hasPattern);
                log.info("==================================");
            }

            uniqueCombos.addAll(comboKeys);

            for (String comboKey : comboKeys) {
                comboReturns.computeIfAbsent(comboKey, k -> new ArrayList<>()).add(dayReturn);
            }
        }

        log.info("Day +{} - Total unique combos found: {} - Combos: {}",
                 dayNumber, uniqueCombos.size(),
                 uniqueCombos.stream().limit(10).collect(Collectors.toList()));

        // Find best combo - prefer specific signals over generic patterns
        String bestCombo = "";
        double bestWinRate = 0;
        double bestAvgReturn = 0;
        int bestTotalTrades = 0;

        for (Map.Entry<String, List<Double>> entry : comboReturns.entrySet()) {
            List<Double> returns = entry.getValue();
            if (returns.size() < 10) continue; // Minimum sample size

            long wins = returns.stream().filter(r -> r > 0).count();
            double winRate = (wins * 100.0) / returns.size();
            double avgReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);

            // Skip if it's ONLY "LONG_PATTERN" and we have other candidates
            boolean isOnlyPattern = entry.getKey().equals("LONG_PATTERN");
            boolean hasOtherCandidates = !bestCombo.isEmpty() && !bestCombo.equals("LONG_PATTERN");

            if (isOnlyPattern && hasOtherCandidates) {
                // Only use LONG_PATTERN if no better alternatives exist
                continue;
            }

            // Best combo has highest win rate, then highest avg return
            // For ties, prefer smaller sample sizes (more specific signals)
            boolean isBetter = false;
            if (winRate > bestWinRate) {
                isBetter = true;
            } else if (Math.abs(winRate - bestWinRate) < 0.5) { // Win rates within 0.5% are considered similar
                if (avgReturn > bestAvgReturn) {
                    isBetter = true;
                } else if (Math.abs(avgReturn - bestAvgReturn) < 0.1) { // Returns within 0.1% are similar
                    // Prefer more specific combos (smaller sample size) if performance is similar
                    if (returns.size() < bestTotalTrades * 0.5 && !entry.getKey().equals("LONG_PATTERN")) {
                        isBetter = true;
                    }
                }
            }

            if (isBetter) {
                bestCombo = entry.getKey();
                bestWinRate = winRate;
                bestAvgReturn = avgReturn;
                bestTotalTrades = returns.size();
            }
        }

        analysis.setBestCombo(bestCombo);
        analysis.setWinPercentage(Math.round(bestWinRate * 100.0) / 100.0);
        analysis.setAvgReturn(Math.round(bestAvgReturn * 100.0) / 100.0);
        analysis.setTotalTrades(bestTotalTrades);

        return analysis;
    }

    /**
     * Analyzes detailed combos for a time horizon and returns top 10
     */
    private PredictionAnalysisResponse.DetailedComboAnalysis analyzeDetailedCombos(
            List<Stock> stocks, int dayNumber) {

        PredictionAnalysisResponse.DetailedComboAnalysis detailedAnalysis =
            new PredictionAnalysisResponse.DetailedComboAnalysis();
        detailedAnalysis.setTimeHorizon("Day +" + dayNumber);

        // Group by combo - now we'll create multiple combo variations per stock
        Map<String, List<Double>> comboReturns = new HashMap<>();

        for (Stock stock : stocks) {
            MetricsInfo metrics = stock.getMetricsInfo();
            if (metrics == null || metrics.getDay0Factors() == null) continue;

            Double dayReturn = getDayReturn(metrics, dayNumber);
            if (dayReturn == null) continue;

            // Create MULTIPLE combo variations for each stock
            List<String> comboKeys = createComboVariations(metrics.getDay0Factors());
            for (String comboKey : comboKeys) {
                comboReturns.computeIfAbsent(comboKey, k -> new ArrayList<>()).add(dayReturn);
            }
        }

        // Calculate stats for each combo
        List<ComboAnalysis> comboAnalyses = new ArrayList<>();

        for (Map.Entry<String, List<Double>> entry : comboReturns.entrySet()) {
            List<Double> returns = entry.getValue();
            if (returns.size() < 10) continue;

            ComboAnalysis combo = new ComboAnalysis();
            combo.setCombo(entry.getKey());
            combo.setTrades(returns.size());

            long wins = returns.stream().filter(r -> r > 0).count();
            combo.setWinningTrades((int) wins);
            combo.setLosingTrades(returns.size() - (int) wins);
            combo.setWinPercentage(Math.round((wins * 100.0 / returns.size()) * 100.0) / 100.0);

            double avgReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            combo.setExpectedReturn(Math.round(avgReturn * 100.0) / 100.0);

            combo.setMaxReturn(returns.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            combo.setMinReturn(returns.stream().mapToDouble(Double::doubleValue).min().orElse(0));

            // Standard deviation
            double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - avgReturn, 2))
                .average().orElse(0);
            combo.setStdDeviation(Math.round(Math.sqrt(variance) * 100.0) / 100.0);

            // Profit factor
            double totalWins = returns.stream().filter(r -> r > 0).mapToDouble(Double::doubleValue).sum();
            double totalLosses = Math.abs(returns.stream().filter(r -> r < 0).mapToDouble(Double::doubleValue).sum());
            combo.setProfitFactor(totalLosses > 0 ? Math.round((totalWins / totalLosses) * 100.0) / 100.0 : 0);

            combo.setFactors(entry.getKey());

            comboAnalyses.add(combo);
        }

        // Sort by win rate desc, then by expected return desc
        comboAnalyses.sort((a, b) -> {
            int winCompare = Double.compare(b.getWinPercentage(), a.getWinPercentage());
            if (winCompare != 0) return winCompare;
            return Double.compare(b.getExpectedReturn(), a.getExpectedReturn());
        });

        // Take top 10
        List<ComboAnalysis> top10 = comboAnalyses.stream().limit(10).collect(Collectors.toList());
        detailedAnalysis.setTopCombos(top10);

        return detailedAnalysis;
    }

    /**
     * Creates multiple combo variations from Day 0 factors
     * This ensures we analyze different combinations separately
     * NOTE: Excludes LONG_PATTERN-only combos as they're too common and not actionable
     */
    private List<String> createComboVariations(List<String> factors) {
        List<String> variations = new ArrayList<>();

        if (factors == null || factors.isEmpty()) {
            variations.add("NO_FACTORS");
            return variations;
        }

        // Extract individual scores instead of overall score
        int dayTradingScore = 0;
        int swingTradingScore = 0;
        int breakoutScore = 0;
        int patternScore = 0;
        int reversalScore = 0;

        for (String factor : factors) {
            if (factor.contains("SCORE-DAYTRADE")) {
                dayTradingScore = extractScore(factor);
            } else if (factor.contains("SCORE-SWINGTRADE")) {
                swingTradingScore = extractScore(factor);
            } else if (factor.contains("SCORE-BREAKOUT")) {
                breakoutScore = extractScore(factor);
            } else if (factor.contains("SCORE-PATTERN")) {
                patternScore = extractScore(factor);
            } else if (factor.contains("SCORE-REVERSAL")) {
                reversalScore = extractScore(factor);
            }
        }

        // Extract signals - FIX: Use = not - in pattern matching
        boolean hasSpike = factors.stream().anyMatch(f ->
            f.contains("SPIKE-ISSPIKE=true") || f.contains("SPIKE-ISSPIKE-true"));
        boolean hasBottom = factors.stream().anyMatch(f ->
            f.contains("BOTTOM-ISBOTTOM=true") || f.contains("BOTTOM-ISBOTTOM-true"));
        boolean hasMomentum = factors.stream().anyMatch(f ->
            f.contains("MOMENTUMPOP-ISMOMENTUMPOP=true") || f.contains("MOMENTUMPOP-ISMOMENTUMPOP-true"));
        boolean hasOversold = factors.stream().anyMatch(f ->
            f.contains("OVERSOLDBOUNCE-ISOVERSOLD=true") || f.contains("OVERSOLDBOUNCE-ISOVERSOLD-true"));
        boolean hasLongPattern = factors.stream().anyMatch(f ->
            (f.contains("PATTERN-LONG=") || f.contains("PATTERN-LONG-")) &&
            !f.contains("PATTERN-LONG=0") && !f.contains("PATTERN-LONG-0"));

        // Generate variations focusing on different aspects
        List<String> signals = new ArrayList<>();
        if (hasSpike) signals.add("SPIKE");
        if (hasBottom) signals.add("BOTTOM");
        if (hasMomentum) signals.add("MOMENTUM");
        if (hasOversold) signals.add("OVERSOLD");

        // Create score variations for each score type (only for scores >= 60)
        List<String> scoreVariations = new ArrayList<>();
        if (dayTradingScore >= 60) {
            scoreVariations.add(getScoreRangeWithType("DAYTRADE", dayTradingScore));
        }
        if (swingTradingScore >= 60) {
            scoreVariations.add(getScoreRangeWithType("SWING", swingTradingScore));
        }
        if (breakoutScore >= 60) {
            scoreVariations.add(getScoreRangeWithType("BREAKOUT", breakoutScore));
        }
        if (patternScore >= 60) {
            scoreVariations.add(getScoreRangeWithType("PATTERN", patternScore));
        }
        if (reversalScore >= 60) {
            scoreVariations.add(getScoreRangeWithType("REVERSAL", reversalScore));
        }

        // Remove any empty strings from variations
        scoreVariations.removeIf(String::isEmpty);

        boolean hasAnyScore = !scoreVariations.isEmpty();

        // Require at least one signal OR a good score (>=70) to proceed
        // This filters out generic/weak setups with no meaningful triggers
        boolean hasGoodScore = scoreVariations.stream()
            .anyMatch(s -> s.contains(">90") || s.contains(":80-89") || s.contains(":70-79"));

        if (!hasAnyScore && signals.isEmpty()) {
            // No scores and no signals - skip this stock
            return variations;
        }

        if (hasAnyScore && signals.isEmpty() && !hasGoodScore) {
            // Only has score 60-69 with no signals - too weak, skip
            return variations;
        }

        // Only create variations if we have meaningful signals or good scores
        if (hasAnyScore || !signals.isEmpty()) {

            // Variation 1: Individual score types only
            variations.addAll(scoreVariations);

            // Variation 2: Score + each signal individually (for highest score type)
            if (!scoreVariations.isEmpty() && !signals.isEmpty()) {
                // Use the highest score type for combinations
                String primaryScore = scoreVariations.getFirst();
                for (String signal : signals) {
                    variations.add(primaryScore + " + " + signal);
                }
            }

            // Variation 3: Signals only (for stocks without good scores)
            if (!signals.isEmpty()) {
                variations.addAll(signals);
            }

            // Variation 4: Multiple signals combined
            if (signals.size() >= 2) {
                String multiSignal = String.join(" + ", signals);
                if (!scoreVariations.isEmpty()) {
                    variations.add(scoreVariations.getFirst() + " + " + multiSignal);
                } else {
                    variations.add(multiSignal);
                }
            }


            // Variation 5: Pattern-based combinations (ONLY with other signals)
            if (hasLongPattern && !signals.isEmpty()) {
                // Only add pattern combos if there are other meaningful signals
                if (!scoreVariations.isEmpty()) {
                    variations.add(scoreVariations.getFirst() + " + LONG_PATTERN");
                }
                variations.add(String.join(" + ", signals) + " + LONG_PATTERN");
            }
        } else if (hasLongPattern && !hasAnyScore) {
            // ONLY use LONG_PATTERN if there's NO score AND NO signals (absolute last resort)
            variations.add("LONG_PATTERN");
        } else if (hasAnyScore) {
            // If we have a score but no signals, at least use the score
            variations.addAll(scoreVariations);
        }

        // If no variations created, add default
        if (variations.isEmpty()) {
            // DO NOT create "NO_STRONG_SIGNALS" - these are low-quality setups
            // Skip this stock entirely from analysis
            return variations; // Return empty list
        }

        return variations;
    }

    /**
     * Get score range string
     */
    private String getScoreRange(int score) {
        if (score >= 90) return "SCORE>90";
        if (score >= 80) return "SCORE:80-89";
        if (score >= 70) return "SCORE:70-79";
        if (score >= 60) return "SCORE:60-69";
        if (score > 0) return "SCORE<60";
        return "";
    }

    /**
     * Get score range string with type prefix (DAYTRADE, SWING, BREAKOUT, PATTERN, REVERSAL)
     * Only creates ranges for scores >= 60 (reliable signals)
     */
    private String getScoreRangeWithType(String scoreType, int score) {
        if (score >= 90) return scoreType + ">90";
        if (score >= 80) return scoreType + ":80-89";
        if (score >= 70) return scoreType + ":70-79";
        if (score >= 60) return scoreType + ":60-69";
        // DO NOT create ranges for scores < 60 - they are unreliable
        return "";
    }


    /**
     * Extracts return for specific day (D1-D10)
     */
    private Double getDayReturn(MetricsInfo metrics, int dayNumber) {
        try {
            return switch (dayNumber) {
                case 1 -> metrics.getD1() != null ? metrics.getD1().getPriceChgPct() : null;
                case 2 -> metrics.getD2() != null ? metrics.getD2().getPriceChgPct() : null;
                case 3 -> metrics.getD3() != null ? metrics.getD3().getPriceChgPct() : null;
                case 4 -> metrics.getD4() != null ? metrics.getD4().getPriceChgPct() : null;
                case 5 -> metrics.getD5() != null ? metrics.getD5().getPriceChgPct() : null;
                case 6 -> metrics.getD6() != null ? metrics.getD6().getPriceChgPct() : null;
                case 7 -> metrics.getD7() != null ? metrics.getD7().getPriceChgPct() : null;
                case 8 -> metrics.getD8() != null ? metrics.getD8().getPriceChgPct() : null;
                case 9 -> metrics.getD9() != null ? metrics.getD9().getPriceChgPct() : null;
                case 10 -> metrics.getD10() != null ? metrics.getD10().getPriceChgPct() : null;
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a combo key from Day 0 factors
     * Groups similar patterns together with more granular scoring
     */
    private String createComboKey(List<String> factors) {
        if (factors == null || factors.isEmpty()) return "NO_FACTORS";

        // Extract overall score for more granular grouping
        int overallScore = 0;
        for (String factor : factors) {
            if (factor.contains("SCORE-OVERALL")) {
                overallScore = extractScore(factor);
                break;
            }
        }

        // Extract key signals
        boolean hasSpike = factors.stream().anyMatch(f ->
            f.contains("SPIKE-ISSPIKE-true"));
        boolean hasBottom = factors.stream().anyMatch(f ->
            f.contains("BOTTOM-ISBOTTOM-true"));
        boolean hasMomentum = factors.stream().anyMatch(f ->
            f.contains("MOMENTUMPOP-ISMOMENTUMPOP-true"));
        boolean hasOversold = factors.stream().anyMatch(f ->
            f.contains("OVERSOLDBOUNCE-ISOVERSOLD-true"));
        boolean hasLongPattern = factors.stream().anyMatch(f ->
            f.contains("PATTERN-LONG") && !f.contains("PATTERN-LONG-0"));

        // Create list to store combo parts
        List<String> comboParts = new ArrayList<>();

        // Add score range (more granular)
        if (overallScore >= 90) {
            comboParts.add("SCORE>90");
        } else if (overallScore >= 80) {
            comboParts.add("SCORE:80-89");
        } else if (overallScore >= 70) {
            comboParts.add("SCORE:70-79");
        } else if (overallScore >= 60) {
            comboParts.add("SCORE:60-69");
        } else if (overallScore > 0) {
            comboParts.add("SCORE<60");
        }

        // Add signals in priority order
        if (hasSpike) comboParts.add("SPIKE");
        if (hasBottom) comboParts.add("BOTTOM");
        if (hasMomentum) comboParts.add("MOMENTUM");
        if (hasOversold) comboParts.add("OVERSOLD");
        if (hasLongPattern) comboParts.add("LONG_PATTERN");

        // Build combo string
        if (comboParts.isEmpty()) {
            return "NO_STRONG_SIGNALS";
        }

        // Join with " + " separator
        return String.join(" + ", comboParts);
    }

    /**
     * Extracts numeric score from factor string
     * Handles both formats: SCORE-OVERALL=9 and SCORE-OVERALL-9
     */
    private int extractScore(String factor) {
        try {
            // Try format: SCORE-OVERALL=9
            if (factor.contains("=")) {
                String[] parts = factor.split("=");
                if (parts.length == 2) {
                    String scoreStr = parts[1].replace(";", "").trim();
                    return Integer.parseInt(scoreStr);
                }
            }

            // Fallback: Try format: SCORE-OVERALL-9
            String[] parts = factor.split("-");
            if (parts.length >= 3) {
                String scoreStr = parts[2].replace(";", "").trim();
                return Integer.parseInt(scoreStr);
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        return 0;
    }

    /**
     * Count-based analysis with both total occurrences and win count
     * Shows: How many times combo appeared vs how many times it achieved threshold%+
     */
    private List<PredictionAnalysisResponse.PeakAnalysis> analyzePeakReturns(List<Stock> stocks, double threshold) {
        // Map: combo -> total occurrences
        Map<String, Integer> comboTotalCount = new HashMap<>();
        // Map: combo -> count of times it achieved threshold%+
        Map<String, Integer> comboWinCount = new HashMap<>();

        int totalProcessed = 0;
        int outliersFiltered = 0;

        for (Stock stock : stocks) {
            MetricsInfo metrics = stock.getMetricsInfo();
            if (metrics == null || metrics.getDay0Factors() == null) continue;

            totalProcessed++;

            // Check if ANY day (D1-D10) achieved threshold%+ gain
            boolean achievedPercent = false;

            for (int day = 1; day <= 10; day++) {
                Double dayReturn = getDayReturn(metrics, day);
                if (dayReturn != null) {
                    // Filter outliers first
                    if (dayReturn > 200.0 || dayReturn < -90.0) {
                        outliersFiltered++;
                        continue; // Skip this day's data
                    }

                    // If any day hits threshold%+, mark as success and move on
                    if (dayReturn >= threshold) {
                        achievedPercent = true;
                        break; // Found it! No need to check remaining days
                    }
                }
            }

            // Create combo variations for this stock
            List<String> comboKeys = createComboVariations(metrics.getDay0Factors());

            // Count total occurrences for each combo
            for (String comboKey : comboKeys) {
                comboTotalCount.merge(comboKey, 1, Integer::sum);

                // If it achieved 20%+, also increment win count
                if (achievedPercent) {
                    comboWinCount.merge(comboKey, 1, Integer::sum);
                }
            }
        }

        // Build results list
        List<PredictionAnalysisResponse.PeakAnalysis> results = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : comboTotalCount.entrySet()) {
            String combo = entry.getKey();
            int totalCount = entry.getValue();
            int winCount = comboWinCount.getOrDefault(combo, 0);

            if (totalCount < 10) continue; // Minimum 10 occurrences required

            double successRate = (winCount * 100.0) / totalCount;

            // Skip combos with success rate below 15% - not worth trading
            if (successRate < 15.0) {
                continue;
            }

            PredictionAnalysisResponse.PeakAnalysis analysis =
                new PredictionAnalysisResponse.PeakAnalysis();
            analysis.setCombo(combo);
            analysis.setPeakReturn(threshold); // Use configurable threshold
            analysis.setPeakDay(totalCount + "/" + winCount); // Store as "total/wins"
            analysis.setAvgPeakReturn(winCount); // Win count
            analysis.setOccurrences(totalCount); // Total occurrences
            analysis.setWinRate((winCount * 100.0) / totalCount); // Success rate %

            // Calculate reliability score (0-100 scale)
            // Formula: Success Rate (0-100) * log10(sample size factor)
            // This balances success rate with statistical significance
            double sampleSizeFactor = Math.log10(Math.max(totalCount, 10)); // log10(10)=1, log10(100)=2, log10(1000)=3
            double reliabilityScore = successRate * sampleSizeFactor;

            // Cap at 100 for display purposes
            analysis.setReliabilityScore(Math.min(100.0, Math.round(reliabilityScore * 100.0) / 100.0));

            results.add(analysis);
        }

        // Sort by win count descending, but penalize low success rates
        results.sort((a, b) -> {
            // Get actual win counts (stored in avgPeakReturn field)
            int winA = (int)a.getAvgPeakReturn();
            int winB = (int)b.getAvgPeakReturn();

            // Compare win counts
            int winCompare = Integer.compare(winB, winA);

            // If win counts are close (within 20%), prefer higher success rate
            if (Math.abs(winA - winB) <= Math.max(winA, winB) * 0.2) {
                return Double.compare(b.getWinRate(), a.getWinRate());
            }

            return winCompare;
        });

        // Log statistics
        log.info("Peak analysis: Processed {} stocks, filtered {} outliers",
                 totalProcessed, outliersFiltered);
        log.info("Peak analysis: Found {} combos (threshold: {}%), ranked by {}%+ win count",
                 results.size(), threshold, threshold);

        // Return top 20
        return results.stream().limit(20).collect(Collectors.toList());
    }
}


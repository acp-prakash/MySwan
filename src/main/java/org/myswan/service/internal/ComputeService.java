package org.myswan.service.internal;

import org.myswan.helpers.scoring.*;
import org.myswan.helpers.scoring.ConsecutiveDaysCalculator;
import org.myswan.model.compute.Score;
import org.myswan.model.collection.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ComputeService {

    private static final Logger log = LoggerFactory.getLogger(ComputeService.class);
    private final StockService stockService;
    private final PatternService patternService;
    private final DayTrading dayTrading;
    private final SwingTrading swingTrading;
    private final Reversal reversal;
    private final Breakout breakout;
    private final Pattern pattern;
    private final BottomDetect bottomDetect;
    private final SpikeDetect spikeDetect;
    private final OversoldBounceDetect oversoldBounceDetect;
    private final MomentumPopDetect momentumPopDetect;
    private final FilterCategoryDetect filterCategoryDetect;
    private final ConsecutiveDaysCalculator consecutiveDaysCalculator;
    private final DailyRanking dailyRanking;
    private final SyncService syncService;
    private final PicksService picksService;

    public ComputeService(StockService stockService, PatternService patternService,
                          DayTrading dayTrading, SwingTrading swingTrading, Reversal reversal,
                          Breakout breakout, Pattern pattern, BottomDetect bottomDetect,
                          SpikeDetect spikeDetect, OversoldBounceDetect oversoldBounceDetect,
                          MomentumPopDetect momentumPopDetect, FilterCategoryDetect filterCategoryDetect,
                          ConsecutiveDaysCalculator consecutiveDaysCalculator, DailyRanking dailyRanking,
                          SyncService syncService, PicksService picksService) {
        this.stockService = stockService;
        this.patternService = patternService;
        this.dayTrading = dayTrading;
        this.swingTrading = swingTrading;
        this.reversal = reversal;
        this.breakout = breakout;
        this.pattern = pattern;
        this.bottomDetect = bottomDetect;
        this.spikeDetect = spikeDetect;
        this.oversoldBounceDetect = oversoldBounceDetect;
        this.momentumPopDetect = momentumPopDetect;
        this.filterCategoryDetect = filterCategoryDetect;
        this.consecutiveDaysCalculator = consecutiveDaysCalculator;
        this.dailyRanking = dailyRanking;
        this.syncService = syncService;
        this.picksService = picksService;
    }

    public String compute() {

        try {
            List<Stock> allList = stockService.list();
            List<org.myswan.model.collection.Pattern> allPatterns = updatePatternAndStockCounts(allList);
            calculateScore(allList, allPatterns);
            return "Compute and Scoring calculation complete: " + allList.size() + " stocks processed";
        } catch (Exception e) {
            return "Error during Compute and Scoring calculation: " + e.getMessage();
        }
    }

    private List<org.myswan.model.collection.Pattern> updatePatternAndStockCounts(List<Stock> allStocks) {

        // 1. Fetch all patterns
        List<org.myswan.model.collection.Pattern> allPatterns = patternService.list();

        // 2. Group patterns by ticker
        Map<String, List<org.myswan.model.collection.Pattern>> patternsByTicker = allPatterns.stream()
                .collect(Collectors.groupingBy(p ->
                        p.getTicker() != null ? p.getTicker().toUpperCase() : "")
                );

        log.info("Processing {} patterns grouped into {} tickers",
                allPatterns.size(), patternsByTicker.size());

        // 3. Precompute long/short counts for each ticker (avoid recomputing)
        Map<String, int[]> countsByTicker = new HashMap<>();

        patternsByTicker.forEach((ticker, patterns) -> {
            if (ticker.isEmpty()) return;

            int longCount = 0;
            int shortCount = 0;

            for (org.myswan.model.collection.Pattern p : patterns) {
                if ("long".equalsIgnoreCase(p.getTrend())) longCount++;
                else if ("short".equalsIgnoreCase(p.getTrend())) shortCount++;
            }
            countsByTicker.put(ticker, new int[] {longCount, shortCount});

            // Update each pattern object
            for (org.myswan.model.collection.Pattern p : patterns) {
                p.setNoOfLongPatterns(longCount);
                p.setNoOfShortPatterns(shortCount);
            }
        });

        // 4. Update stock objects using the SAME precomputed map
        for (Stock stock : allStocks) {
            String ticker = stock.getTicker() != null ? stock.getTicker().toUpperCase() : "";
            int[] counts = countsByTicker.get(ticker);

            if (counts != null) {
                stock.setNoOfLongPatterns(counts[0]);
                stock.setNoOfShortPatterns(counts[1]);
            } else {
                stock.setNoOfLongPatterns(0);
                stock.setNoOfShortPatterns(0);
            }
        }
        return allPatterns;
    }

    public String calculateScore(List<Stock> allList, List<org.myswan.model.collection.Pattern> allPatterns) {
        try {

            List<Stock> historyList = stockService.getHistoryByDate(LocalDate.now().minusDays(1));
            if(historyList == null || historyList.isEmpty())
            {
                historyList = stockService.getHistoryByDate(LocalDate.now().minusDays(2));
                if(historyList == null || historyList.isEmpty())
                {
                    historyList = stockService.getHistoryByDate(LocalDate.now().minusDays(3));
                }
            }

            log.info("Loaded {} history records for previous business day: {}", historyList.size(),
                    historyList.getFirst().getHistDate());

            // Create a map of previous day history by ticker for fast lookup
            var historyMap = new java.util.HashMap<String, Stock>();
            for (Stock h : historyList) {
                if (h.getTicker() != null && !h.getTicker().isBlank()) {
                    historyMap.put(h.getTicker(), h);
                }
            }

            // Calculate consecutive up/down days first (before scoring)
            allList.parallelStream().forEach(consecutiveDaysCalculator::calculateConsecutiveDays);
            log.info("calculateConsecutiveDays completed");
            allList.parallelStream().forEach(dayTrading::calculateScore);//DayTrading Setup
            log.info("dayTrading::calculateScore completed");
            allList.parallelStream().forEach(swingTrading::calculateScore);//SwingTrading Setup
            log.info("swingTrading::calculateScore completed");
            allList.parallelStream().forEach(reversal::calculateScore);// Reversal Setup
            log.info("reversal::calculateScore completed");
            allList.parallelStream().forEach(breakout::calculateScore);//Breakout Setup
            log.info("breakout::calculateScore completed");
            allList.parallelStream().forEach(pattern::calculateScore);//Pattern Setup
            log.info("pattern::calculateScore completed");
            allList.parallelStream().forEach(this::calculateOverAllScore);//Overall Score
            log.info("calculateOverAllScore completed");
            allList.parallelStream().forEach(this::calculateSignal);//Overall Signal
            log.info("calculateSignal completed");

            //Detecting Bottomed stocks
            allList.parallelStream().forEach(stock -> {
                Stock previousDayStock = historyMap.get(stock.getTicker());
                bottomDetect.detectBottomSignal(stock, previousDayStock);
            });
            log.info("detectBottomSignal completed");

            //Detecting Spike probables
            allList.parallelStream().forEach(stock -> {
                Stock previousDayStock = historyMap.get(stock.getTicker());
                spikeDetect.detectSpikeSignal(stock, previousDayStock);
            });
            log.info("detectSpikeSignal completed");

            //Detecting OverSold Bounces
            allList.parallelStream().forEach(stock -> {
                Stock previousDayStock = historyMap.get(stock.getTicker());
                oversoldBounceDetect.detectOversoldBounce(stock, previousDayStock);
            });
            log.info("detectOversoldBounce completed");

            //Detecting Momentum Pops
            allList.parallelStream().forEach(stock -> {
                Stock previousDayStock = historyMap.get(stock.getTicker());
                momentumPopDetect.detectMomentumPop(stock, previousDayStock);
            });
            log.info("detectMomentumPop completed");

            //Categorize and assign filter category each stock daily
            allList.parallelStream().forEach(stock -> {
                Stock previousDayStock = historyMap.get(stock.getTicker());
                filterCategoryDetect.filterCategory(stock, previousDayStock);
            });
            log.info("filterCategory completed");

            allList.parallelStream().forEach(dailyRanking::dailyRanking);//DailyRanking Setup
            log.info("dailyRanking completed");
            stockService.replaceStocks(allList);
            log.info("replaceStocks completed");

            allPatterns.parallelStream().forEach(pattern -> {
                String ticker = pattern.getTicker() != null ? pattern.getTicker().toUpperCase() : "";
                Stock stock = allList.stream()
                        .filter(s -> ticker.equalsIgnoreCase(s.getTicker()))
                        .findFirst()
                        .orElse(null);
                if (stock != null) {
                    pattern.setStock(stock);
                }
            });
            patternService.deleteAll();
            patternService.saveAll(allPatterns);

            picksService.syncWithStockData(allList);
            log.info("syncWithStockData completed");
            syncService.syncAllHistory();
            log.info("syncAllHistory completed");
            return "Scoring calculation complete: " + allList.size() + " stocks processed";
        } catch (Exception e) {
            return "Error during scoring calculation: " + e.getMessage();
        }
    }

    private void calculateOverAllScore(Stock stock) {

        if(stock.getScore() == null)
            stock.setScore(new Score());

        double day = Math.min(stock.getScore().getDayTradingScore(), 100.0);
        double swing = Math.min(stock.getScore().getSwingTradingScore(), 100.0);
        double reversal = Math.min(stock.getScore().getReversalScore(), 100.0);
        double breakout = Math.min(stock.getScore().getBreakoutScore(), 100.0);
        double pattern = Math.min(stock.getScore().getPatternScore(), 100.0);

        // 1. Normalize 0–100 overall score
        double overall = (day + swing + reversal + breakout + pattern) / 5.0;

        // 2. Build explanation summary

        String sb = "Day: " + String.format("%.1f", (double) stock.getScore().getDayTradingScore()) + " (" +
                stock.getScore().getDayTradingReason() + ") | " +
                "Swing: " + String.format("%.1f", (double) stock.getScore().getSwingTradingScore()) + " (" +
                stock.getScore().getSwingTradingReason() + ") | " +
                "Reversal: " + String.format("%.1f", (double) stock.getScore().getReversalScore()) + " (" +
                stock.getScore().getReversalReason() + ") | " +
                "Breakout: " + String.format("%.1f", (double) stock.getScore().getBreakoutScore()) + " (" +
                stock.getScore().getBreakoutReason() + ") | " +
                "Pattern: " + String.format("%.1f", (double) stock.getScore().getPatternScore()) + " (" +
                stock.getScore().getPatternReason() + ")";

        stock.getScore().setOverallScore((int) overall);
        stock.getScore().setOverallReason(sb);
    }

    private void calculateSignal(Stock stock) {

        if(stock.getScore() == null)
            stock.setScore(new Score());
        String signal;
        StringBuilder reason = new StringBuilder();

        if (stock.getScore().getOverallScore() >= 60) {
            signal = "BUY";
            reason.append("Strong technical alignment; Overall score high (")
                    .append(String.format("%.1f", (double)stock.getScore().getOverallScore()))
                    .append("). Indicators point to bullish continuation. ");
        }
        else if (stock.getScore().getOverallScore() <= 40) {
            signal = "SELL";
            reason.append("Weak technical setup; Overall score low (")
                    .append(String.format("%.1f", (double)stock.getScore().getOverallScore()))
                    .append("). Momentum and trend do not support upside. ");
        }
        else {
            signal = "HOLD";
            reason.append("Mixed signals; Overall score neutral (")
                    .append(String.format("%.1f", (double)stock.getScore().getOverallScore()))
                    .append("). Wait for clearer setup. ");
        }

        // Add more detailed summary
        reason.append("Summary: ").append(stock.getScore().getOverallReason());

        stock.getScore().setSignal(signal);
        stock.getScore().setSignalReason(reason.toString());

        // Calculate consecutive signal days including today
        try {
            // Start with today = 1 (current signal)
            int signalDays = 1;

            String ticker = stock.getTicker();
            if (ticker != null && !ticker.isBlank()) {
                // Determine previous business-day window to inspect (last 30 calendar days)
                java.time.LocalDate endDate = java.time.LocalDate.now().minusDays(1);
                // Walk back to skip weekends for endDate if needed
                while (endDate.getDayOfWeek().getValue() >= 6) { // 6=Sat,7=Sun
                    endDate = endDate.minusDays(1);
                }
                java.time.LocalDate fromDate = endDate.minusDays(30);

                // Fetch history for this ticker for the date range [fromDate, endDate]
                List<Stock> history = stockService.getStockHistory(ticker, fromDate, endDate);

                // history is sorted DESC by histDate in getStockHistory
                for (Stock h : history) {
                    if (h == null) continue;
                    if (h.getScore() == null) break;
                    String prevSignal = h.getScore().getSignal();
                    if (prevSignal == null) break;
                    if (prevSignal.equalsIgnoreCase(signal)) {
                        signalDays++;
                    } else {
                        break; // signal changed in history
                    }
                }
            }

            stock.getScore().setSignalDays(signalDays);
        } catch (Exception e) {
            log.warn("Failed to compute signalDays for {}: {}", stock.getTicker(), e.getMessage());
            // leave signalDays as default (0) if failure
        }
    }
}
package org.myswan.service.internal;

import org.myswan.helpers.scoring.*;
import org.myswan.helpers.scoring.ConsecutiveDaysCalculator;
import org.myswan.model.compute.FilterCategory;
import org.myswan.model.compute.Score;
import org.myswan.model.Stock;
import org.myswan.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);
    private final StockService stockService;
    private final StockRepository repository;
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

    public ScoringService(StockService stockService, StockRepository repository,
                          DayTrading dayTrading,SwingTrading swingTrading,Reversal reversal,
                          Breakout breakout,Pattern pattern, BottomDetect bottomDetect,
                          SpikeDetect spikeDetect, OversoldBounceDetect oversoldBounceDetect,
                          MomentumPopDetect momentumPopDetect,
                          FilterCategoryDetect filterCategoryDetect,
                          ConsecutiveDaysCalculator consecutiveDaysCalculator) {
        this.stockService = stockService;
        this.repository = repository;
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
    }

    public String calculateScore() {
        try {
            List<Stock> allList = stockService.list();

            // Calculate previous business day (skip weekends)
            LocalDate previousDate = LocalDate.now().minusDays(1);
            while (previousDate.getDayOfWeek().getValue() >= 6) { // 6=Saturday, 7=Sunday
                previousDate = previousDate.minusDays(1);
            }

            List<Stock> historyList = stockService.getHistoryByDate(previousDate);
            log.info("Loaded {} history records for previous business day: {}", historyList.size(), previousDate);

            // Create a map of previous day history by ticker for fast lookup
            var historyMap = new java.util.HashMap<String, Stock>();
            for (Stock h : historyList) {
                if (h.getTicker() != null && !h.getTicker().isBlank()) {
                    historyMap.put(h.getTicker(), h);
                }
            }

            // Calculate consecutive up/down days first (before scoring)
            allList.parallelStream().forEach(consecutiveDaysCalculator::calculateConsecutiveDays);
            allList.parallelStream().forEach(dayTrading::calculateScore);//DayTrading Setup
            allList.parallelStream().forEach(swingTrading::calculateScore);//SwingTrading Setup
            allList.parallelStream().forEach(reversal::calculateScore);// Reversal Setup
            allList.parallelStream().forEach(breakout::calculateScore);//Breakout Setup
            allList.parallelStream().forEach(pattern::calculateScore);//Pattern Setup
            allList.parallelStream().forEach(this::calculateOverAllScore);//Overall Score
            allList.parallelStream().forEach(this::calculateSignal);//Overall Signal
            //Detecting Bottomed stocks
            allList.parallelStream().forEach(stock -> {
                Stock previousDayStock = historyMap.get(stock.getTicker());
                bottomDetect.detectBottomSignal(stock, previousDayStock);
            });
            //Detecting Spike probables
            allList.parallelStream().forEach(stock -> {
                Stock previousDayStock = historyMap.get(stock.getTicker());
                spikeDetect.detectSpikeSignal(stock, previousDayStock);
            });
            //Detecting OverSOld Bounces
            allList.parallelStream().forEach(stock -> {
                Stock previousDayStock = historyMap.get(stock.getTicker());
                oversoldBounceDetect.detectOversoldBounce(stock, previousDayStock);
            });

            //Detecting Momentum Pops
            allList.parallelStream().forEach(stock -> {
                Stock previousDayStock = historyMap.get(stock.getTicker());
                momentumPopDetect.detectMomentumPop(stock, previousDayStock);
            });

            //Categorize and assign filter category each stock daily
            allList.parallelStream().forEach(stock -> {
                Stock previousDayStock = historyMap.get(stock.getTicker());
                filterCategoryDetect.filterCategory(stock, previousDayStock);
            });
            repository.saveAll(allList);
            stockService.syncStockHistory();
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
    }
}
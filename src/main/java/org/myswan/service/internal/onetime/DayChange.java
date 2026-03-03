package org.myswan.service.internal.onetime;

import org.myswan.model.collection.Stock;
import org.myswan.model.compute.MetricsDay;
import org.myswan.model.compute.MetricsInfo;
import org.myswan.service.internal.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DayChange {

    private static final Logger log = LoggerFactory.getLogger(DayChange.class);

    private final StockService stockService;
    private final MongoTemplate mongoTemplate;

    public DayChange(StockService stockService, MongoTemplate mongoTemplate) {
        this.stockService = stockService;
        this.mongoTemplate = mongoTemplate;
    }

    public void computeDayChangeForAllHistory() {
        try {
            List<Stock> allStocks = stockService.list();
            if (allStocks == null || allStocks.isEmpty()) {
                log.warn("No stocks found in database");
                return;
            }

            log.info("Starting metrics computation for {} tickers", allStocks.size());
            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (Stock stock : allStocks) {
                try {
                    if (stock == null || stock.getTicker() == null) {
                        log.warn("Skipping null stock or ticker");
                        continue;
                    }

                    processTickerHistory(stock.getTicker());
                    processedCount.incrementAndGet();

                    if (processedCount.get() % 10 == 0) {
                        log.info("Processed {} of {} tickers", processedCount.get(), allStocks.size());
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    String tickerName = (stock != null && stock.getTicker() != null) ? stock.getTicker() : "unknown";
                    log.error("Error processing ticker: {}", tickerName, e);
                }
            }

            log.info("Metrics computation completed. Success: {}, Errors: {}",
                     processedCount.get(), errorCount.get());

        } catch (Exception e) {
            log.error("Fatal error in computeDayChangeForAllHistory", e);
            throw new RuntimeException("Failed to compute day changes", e);
        }
    }

    private void processTickerHistory(String ticker) {
        // Fetch all history for this ticker
        List<Stock> history = stockService.getStockHistory(ticker, null, null);

        if (history == null || history.isEmpty()) {
            log.debug("No history found for ticker: {}", ticker);
            return;
        }

        // Reverse to get chronological order (oldest first)
        Collections.reverse(history);

        List<Stock> updatedStocks = new ArrayList<>();

        // Process ALL records - populate D1-D10 with whatever future data is available
        // Recent records may have partial data (e.g., only D1-D5 if only 5 days of future data exist)
        for (int i = 0; i < history.size(); i++) {
            Stock day0Stock = history.get(i);  // This is "Day 0" for this iteration
            if (day0Stock == null) {
                continue;
            }

            try {
                // Build metrics: Day 0 = current record, D1-D10 = next days (if available)
                MetricsInfo metricsInfo = buildMetricsInfo(day0Stock, history, i);

                // Log first few records to verify
                if (i < 3) {
                    log.debug("Ticker: {} | Index: {} | Day0 Date: {} | Day0 Price: {} | Day0 Factors: {}",
                             ticker, i, day0Stock.getHistDate(), day0Stock.getPrice(),
                             metricsInfo.getDay0Factors().size());
                }

                // Set metricsInfo on the Day 0 record
                day0Stock.setMetricsInfo(metricsInfo);
                updatedStocks.add(day0Stock);
            } catch (Exception e) {
                log.error("Error building metrics for ticker: {} at index: {}", ticker, i, e);
            }
        }

        log.debug("Ticker: {} | Processed: {} records (includes partial future data for recent dates)",
                 ticker, updatedStocks.size());

        // Save all updated records back to stockHistory collection in one bulk operation
        if (!updatedStocks.isEmpty()) {
            // Use bulk operations for better performance - single DB call
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, "stockHistory");

            for (Stock stock : updatedStocks) {
                Query query = Query.query(Criteria.where("_id").is(stock.getId()));
                Update update = new Update().set("metricsInfo", stock.getMetricsInfo());
                bulkOps.updateOne(query, update);
            }

            bulkOps.execute();
            log.debug("Bulk updated metrics for {} records of ticker: {}", updatedStocks.size(), ticker);
        }
    }

    private MetricsInfo buildMetricsInfo(Stock day0Stock, List<Stock> history, int day0Index) {
        MetricsInfo metricsInfo = new MetricsInfo();
        metricsInfo.setTicker(day0Stock.getTicker());
        metricsInfo.setDay0Factors(new ArrayList<>());

        // Compute forward-looking metrics from Day 0 to D1-D10
        // day0Stock is the baseline, we compare future days against it
        metricsInfo.setD1(computeMetricsDaySafe(day0Stock, history, day0Index + 1));   // Day 0 → Day 1
        metricsInfo.setD2(computeMetricsDaySafe(day0Stock, history, day0Index + 2));   // Day 0 → Day 2
        metricsInfo.setD3(computeMetricsDaySafe(day0Stock, history, day0Index + 3));   // Day 0 → Day 3
        metricsInfo.setD4(computeMetricsDaySafe(day0Stock, history, day0Index + 4));   // Day 0 → Day 4
        metricsInfo.setD5(computeMetricsDaySafe(day0Stock, history, day0Index + 5));   // Day 0 → Day 5
        metricsInfo.setD6(computeMetricsDaySafe(day0Stock, history, day0Index + 6));   // Day 0 → Day 6
        metricsInfo.setD7(computeMetricsDaySafe(day0Stock, history, day0Index + 7));   // Day 0 → Day 7
        metricsInfo.setD8(computeMetricsDaySafe(day0Stock, history, day0Index + 8));   // Day 0 → Day 8
        metricsInfo.setD9(computeMetricsDaySafe(day0Stock, history, day0Index + 9));   // Day 0 → Day 9
        metricsInfo.setD10(computeMetricsDaySafe(day0Stock, history, day0Index + 10)); // Day 0 → Day 10

        // Capture Day 0 factors (signals/scores from the baseline day)
        addDay0Factors(metricsInfo, day0Stock);

        return metricsInfo;
    }

    private MetricsDay computeMetricsDaySafe(Stock day0Stock, List<Stock> history, int futureIndex) {
        if (futureIndex >= history.size()) {
            return new MetricsDay(); // Return empty metrics if out of bounds
        }

        Stock futureStock = history.get(futureIndex);
        if (futureStock == null) {
            return new MetricsDay();
        }

        return computeMetricsDay(day0Stock, futureStock);
    }

    private MetricsDay computeMetricsDay(Stock day0Stock, Stock futureStock) {
        MetricsDay day = new MetricsDay();

        double day0Price = day0Stock.getPrice();
        double futurePrice = futureStock.getPrice();

        if (day0Price == 0) {
            log.warn("Day 0 price is 0 for ticker: {} on {}", day0Stock.getTicker(), day0Stock.getHistDate());
            return day;
        }

        // Calculate price change FROM Day 0 TO future day
        double priceDiff = futurePrice - day0Price;
        double priceChgPct = ((futurePrice - day0Price) / day0Price) * 100;

        // Round to 2 decimal places to avoid floating point precision issues
        day.setPriceDiff(Math.round(priceDiff * 100.0) / 100.0);
        day.setPriceChgPct(Math.round(priceChgPct * 100.0) / 100.0);

        return day;
    }

    private void addDay0Factors(MetricsInfo metricsInfo, Stock day0Stock) {
        List<String> factors = metricsInfo.getDay0Factors();

        // Capture Day 0 factors from the baseline stock (the stock we're analyzing)
        if (log.isTraceEnabled()) {
            log.trace("Capturing Day 0 factors from: Ticker={} Date={} Price={}",
                     day0Stock.getTicker(), day0Stock.getHistDate(), day0Stock.getPrice());
        }

        // Score factors with null checks - Reading from Day 0 stock object
        if (day0Stock.getScore() != null) {
            factors.add("SCORE-SIGNAL=" + day0Stock.getScore().getSignal());
            factors.add("SCORE-OVERALL=" + day0Stock.getScore().getOverallScore());
            factors.add("SCORE-DAYTRADE=" + day0Stock.getScore().getDayTradingScore());
            factors.add("SCORE-SWINGTRADE=" + day0Stock.getScore().getSwingTradingScore());
            factors.add("SCORE-BREAKOUT=" + day0Stock.getScore().getBreakoutScore());
            factors.add("SCORE-PATTERN=" + day0Stock.getScore().getPatternScore());
            factors.add("SCORE-REVERSAL=" + day0Stock.getScore().getReversalScore());
        }

        // Bottom factors with null checks
        if (day0Stock.getBottom() != null) {
            factors.add("BOTTOM-ISBOTTOM=" + day0Stock.getBottom().isBottom());
            factors.add("BOTTOM-STRENGTH=" + day0Stock.getBottom().getStrength());
            factors.add("BOTTOM-CONDITIONMET=" + day0Stock.getBottom().getConditionsMet());
        }

        // Spike factors with null checks
        if (day0Stock.getSpike() != null) {
            factors.add("SPIKE-ISSPIKE=" + day0Stock.getSpike().isSpikeLikely());
            factors.add("SPIKE-SCORE=" + day0Stock.getSpike().getSpikeScore());
            factors.add("SPIKE-TYPE=" + Objects.toString(day0Stock.getSpike().getSpikeType(), "NONE"));
        }

        // Oversold factors with null checks
        if (day0Stock.getOversold() != null) {
            factors.add("OVERSOLDBOUNCE-ISOVERSOLD=" + day0Stock.getOversold().isOversoldBounce());
            factors.add("OVERSOLDBOUNCE-SCORE=" + day0Stock.getOversold().getBounceScore());
            factors.add("OVERSOLDBOUNCE-TYPE=" + Objects.toString(day0Stock.getOversold().getBounceType(), "NONE"));
        }

        // Momentum factors with null checks
        if (day0Stock.getMomPop() != null) {
            factors.add("MOMENTUMPOP-ISMOMENTUMPOP=" + day0Stock.getMomPop().isMomentumPop());
            factors.add("MOMENTUMPOP-SCORE=" + day0Stock.getMomPop().getPopScore());
            factors.add("MOMENTUMPOP-TYPE=" + Objects.toString(day0Stock.getMomPop().getPopType(), "NONE"));
        }

        // Daily rank factors with null checks
        if (day0Stock.getDailyRank() != null) {
            factors.add("DAILYRANK-PICKSCORE=" + day0Stock.getDailyRank().getPickScore());
            factors.add("DAILYRANK-SAFESCORE=" + day0Stock.getDailyRank().getSafetyRank());
            factors.add("DAILYRANK-RANK=" + day0Stock.getDailyRank().getFinalRank());
        }

        // Pattern counts
        factors.add("PATTERN-LONG=" + day0Stock.getNoOfLongPatterns());
        factors.add("PATTERN-SHORT=" + day0Stock.getNoOfShortPatterns());
    }
}

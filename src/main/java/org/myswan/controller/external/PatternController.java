package org.myswan.controller.external;

import lombok.extern.slf4j.Slf4j;
import org.myswan.common.UtilHelper;
import org.myswan.model.collection.Master;
import org.myswan.model.collection.Pattern;
import org.myswan.model.collection.Stock;
import org.myswan.service.external.EtradeClient;
import org.myswan.service.internal.MasterService;
import org.myswan.service.internal.PatternService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
public class PatternController {

    private final PatternService patternService;
    private final MasterService masterService;
    private final EtradeClient etradePatternClient;
    private final MongoTemplate mongoTemplate;

    public PatternController(PatternService patternService,
                           MasterService masterService,
                           EtradeClient etradePatternClient,
                           MongoTemplate mongoTemplate) {
        this.patternService = patternService;
        this.masterService = masterService;
        this.etradePatternClient = etradePatternClient;
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/pattern/list")
    public ResponseEntity<List<Pattern>> getAllPatterns() {
        return ResponseEntity.ok(patternService.list());
    }

    @GetMapping("/pattern/{ticker}")
    public ResponseEntity<List<Pattern>> getPatternsByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(patternService.listByTicker(ticker));
    }

    @GetMapping("/pattern/history/{ticker}")
    public ResponseEntity<List<Pattern>> getPatternHistoryByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(patternService.listHistoryByTicker(ticker));
    }

    @PostMapping("/pattern/fetch-etrade")
    public ResponseEntity<String> fetchEtradePatterns() {
        try {
            log.info("Starting eTrade pattern fetch with parallel processing...");

            // Get all masters with etradePatternLookup = true
            List<Master> masters = masterService.list();
            log.info("Total masters in database: {}", masters.size());

            List<Master> enabledMasters = masters.stream()
                .filter(m -> {
                    boolean enabled = m.isEtradePatternLookup();
                    if (enabled) {
                        log.debug("Ticker {} - etradePatternLookup: true", m.getTicker());
                    }
                    return enabled;
                })
                .toList();

            log.info("Found {} masters with eTrade pattern enabled (etradePatternLookup = true)", enabledMasters.size());

            if (enabledMasters.size() > 0 && enabledMasters.size() <= 10) {
                log.info("Enabled tickers: {}", enabledMasters.stream()
                    .map(Master::getTicker)
                    .collect(Collectors.joining(", ")));
            }

            if (enabledMasters.isEmpty()) {
                return ResponseEntity.ok("No masters enabled for eTrade pattern lookup");
            }

            List<Pattern> allPatterns = Collections.synchronizedList(new ArrayList<>());
            List<String> failedTickers = Collections.synchronizedList(new ArrayList<>());
            List<String> notFoundTickers = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            AtomicInteger notFoundCount = new AtomicInteger(0);
			String today = UtilHelper.formatLocalDateToString(LocalDate.now());
            //String today = UtilHelper.formatLocalDateToString(LocalDate.now().minusDays(1));

            log.info("Starting pattern fetch for {} enabled tickers", enabledMasters.size());

            // Use Java 21 Virtual Threads for parallel processing
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                // Process in batches of 50
                int batchSize = 50;

                for (int i = 0; i < enabledMasters.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, enabledMasters.size());
                    List<Master> batch = enabledMasters.subList(i, end);

                    log.info("Processing batch: {} to {} of {}", i + 1, end, enabledMasters.size());

                    // Create futures for this batch
                    List<CompletableFuture<Void>> futures = new ArrayList<>();

                    for (Master master : batch) {
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            try {
                                log.debug("Fetching patterns for ticker: {}", master.getTicker());
                                List<Pattern> patterns = etradePatternClient.fetchPatterns(master.getTicker());

                                if (patterns != null && !patterns.isEmpty()) {
                                    allPatterns.addAll(patterns);
                                    successCount.incrementAndGet();
                                    log.info("✓ {} - Fetched {} patterns", master.getTicker(), patterns.size());
                                } else {
                                    successCount.incrementAndGet();
                                    log.info("✓ {} - No patterns found (valid ticker, no patterns)", master.getTicker());
                                }

                                // Small delay to avoid overwhelming the API
                                Thread.sleep(100);

                            } catch (org.myswan.service.external.PatternNotFoundException e) {
                                // 404 - Ticker not found at eTrade, disable it
                                notFoundCount.incrementAndGet();
                                notFoundTickers.add(master.getTicker());
                                log.warn("✗ {} - NOT FOUND (404) - Will disable in master", master.getTicker());

                                // Disable ticker in master collection
                                try {
                                    disableTickerInMaster(master.getTicker());
                                } catch (Exception ex) {
                                    log.error("Failed to disable ticker in master: {}", master.getTicker(), ex);
                                }

                            } catch (Exception e) {
                                failureCount.incrementAndGet();
                                failedTickers.add(master.getTicker() + ": " + e.getMessage());
                                log.error("✗ {} - ERROR: {}", master.getTicker(), e.getMessage());
                            }
                        }, executor);

                        futures.add(future);
                    }

                    // Wait for batch to complete before starting next batch
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                    log.info("Batch complete. Total progress - Success: {}, Not Found: {}, Failed: {}",
                             successCount.get(), notFoundCount.get(), failureCount.get());
                }
            }

            log.info("Fetched total {} patterns from eTrade. Success: {}, Failed: {}",
                     allPatterns.size(), successCount.get(), failureCount.get());

            // Enrich patterns with stock price data
            enrichPatternsWithStockData(allPatterns);
            log.info("Enriched patterns with stock price data");

            // Delete existing patterns
            patternService.deleteAll();
            log.info("Deleted existing patterns");

            // Save new patterns
            patternService.saveAll(allPatterns);
            log.info("Saved {} new patterns", allPatterns.size());

            // Delete history for today
            patternService.deleteHistoryByDate(today);
            log.info("Deleted pattern history for today: {}", today);

            // Save to history
            patternService.saveToHistory(allPatterns);
            log.info("Saved patterns to history");

            // Update stock pattern counts
            updateStockPatternCounts();
            log.info("Updated stock pattern counts");

            StringBuilder result = new StringBuilder();
            result.append(String.format(
                "Pattern fetch completed for %d enabled tickers\n\n",
                enabledMasters.size()));
            result.append(String.format("✓ Success: %d (Total patterns: %d)\n", successCount.get(), allPatterns.size()));
            result.append(String.format("✗ Not Found (404): %d (disabled in master)\n", notFoundCount.get()));
            result.append(String.format("✗ Failed: %d\n", failureCount.get()));

            if (!notFoundTickers.isEmpty()) {
                result.append("\n🚫 Tickers not found (404) - DISABLED in master:\n");
                notFoundTickers.forEach(ticker -> result.append("  - ").append(ticker).append("\n"));
            }

            if (!failedTickers.isEmpty()) {
                result.append("\n❌ Failed tickers (errors):\n");
                failedTickers.forEach(ticker -> result.append("  - ").append(ticker).append("\n"));
            }

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            log.error("Error in fetchEtradePatterns", e);
            return ResponseEntity.internalServerError()
                .body("Error fetching eTrade patterns: " + e.getMessage());
        }
    }

    private void updateStockPatternCounts() {
        try {
			//String today = UtilHelper.formatLocalDateToString(LocalDate.now());
            String today = UtilHelper.formatLocalDateToString(LocalDate.now().minusDays(1));

            // First, reset stock pattern counts to 0 (current stock collection)
            Update resetUpdate = new Update()
                .set("noOfLongPatterns", 0)
                .set("noOfShortPatterns", 0);
            mongoTemplate.updateMulti(new Query(), resetUpdate, Stock.class);
            log.info("Reset stock pattern counts to 0 in stock collection");

            // Reset ONLY today's stockHistory records to 0
            Query todayHistoryQuery = new Query(Criteria.where("histDate").is(LocalDate.parse(today)));
            mongoTemplate.updateMulti(todayHistoryQuery, resetUpdate, "stockHistory");
            log.info("Reset stock pattern counts to 0 in stockHistory for date: {}", today);

            // Get all patterns grouped by ticker (case-insensitive grouping)
            List<Pattern> allPatterns = patternService.list();
            Map<String, List<Pattern>> patternsByTicker = allPatterns.stream()
                .collect(Collectors.groupingBy(p -> p.getTicker() != null ? p.getTicker().toUpperCase() : ""));

            log.info("Processing pattern counts for {} unique tickers", patternsByTicker.size());

            // Update each stock
            patternsByTicker.forEach((upperTicker, patterns) -> {
                if (upperTicker.isEmpty()) return;

                long longCount = patterns.stream()
                    .filter(p -> "long".equalsIgnoreCase(p.getTrend()))
                    .count();
                long shortCount = patterns.stream()
                    .filter(p -> "short".equalsIgnoreCase(p.getTrend()))
                    .count();

                // Update stock collection (current stock)
                Query stockQuery = new Query(Criteria.where("ticker").regex("^" + upperTicker + "$", "i"));
                Update update = new Update()
                    .set("noOfLongPatterns", (int) longCount)
                    .set("noOfShortPatterns", (int) shortCount);

                long stocksUpdated = mongoTemplate.updateFirst(stockQuery, update, Stock.class).getModifiedCount();

                // Update ONLY today's stockHistory record for this ticker
                Query historyQuery = new Query(
                    Criteria.where("ticker").regex("^" + upperTicker + "$", "i")
                        .and("histDate").is(LocalDate.parse(today))
                );
                long historyUpdated = mongoTemplate.updateFirst(historyQuery, update, "stockHistory").getModifiedCount();

                if (stocksUpdated > 0) {
                    log.debug("Updated pattern counts for {}: Long={}, Short={} (stock: {}, history: {})",
                             upperTicker, longCount, shortCount, stocksUpdated, historyUpdated);
                } else {
                    log.warn("No stock found for ticker: {} (from patterns)", patterns.get(0).getTicker());
                }
            });

            log.info("Updated pattern counts for {} stocks", patternsByTicker.size());
        } catch (Exception e) {
            log.error("Error updating stock pattern counts", e);
        }
    }

    private void enrichPatternsWithStockData(List<Pattern> patterns) {
        try {
            // Group patterns by ticker for efficient lookup
            Map<String, List<Pattern>> patternsByTicker = patterns.stream()
                .collect(Collectors.groupingBy(Pattern::getTicker));

            // Fetch stock data for all tickers at once
            List<String> tickers = new ArrayList<>(patternsByTicker.keySet());
            Query query = new Query(Criteria.where("ticker").in(tickers));
            List<Stock> stocks = mongoTemplate.find(query, Stock.class);

            // Create a map of ticker -> stock for quick lookup
            Map<String, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getTicker, stock -> stock));

            // Enrich each pattern with stock data
            patterns.forEach(pattern -> {
                Stock stock = stockMap.get(pattern.getTicker());
                if (stock != null) {
                    pattern.setPrice(stock.getPrice());
                    pattern.setChange(stock.getChange());
                    pattern.setHigh(stock.getHigh());
                    pattern.setLow(stock.getLow());
                    log.debug("Enriched pattern for {} with price: {}, change: {}, high: {}, low: {}",
                            pattern.getTicker(), stock.getPrice(), stock.getChange(), stock.getHigh(), stock.getLow());
                } else {
                    log.warn("No stock data found for ticker: {}", pattern.getTicker());
                    // Set default values
                    pattern.setPrice(0.0);
                    pattern.setChange(0.0);
                    pattern.setHigh(0.0);
                    pattern.setLow(0.0);
                }
            });

            log.info("Enriched {} patterns with stock data from {} stocks", patterns.size(), stocks.size());
        } catch (Exception e) {
            log.error("Error enriching patterns with stock data", e);
        }
    }

    private void disableTickerInMaster(String ticker) {
        try {
            // Use case-insensitive query to find the ticker
            Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            Update update = new Update().set("etradePatternLookup", false);

            long updated = mongoTemplate.updateFirst(query, update, "master").getModifiedCount();

            if (updated > 0) {
                log.info("Disabled etradePatternLookup for ticker: {} (404 not found)", ticker);
            } else {
                log.warn("Could not find ticker in master collection to disable: {}", ticker);
            }
        } catch (Exception e) {
            log.error("Error disabling ticker {} in master collection", ticker, e);
            throw e;
        }
    }
}

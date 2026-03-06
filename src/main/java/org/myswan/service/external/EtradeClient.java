package org.myswan.service.external;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.myswan.common.UtilHelper;
import org.myswan.model.collection.AppCache;
import org.myswan.model.collection.Master;
import org.myswan.model.collection.Pattern;
import org.myswan.model.collection.Stock;
import org.myswan.service.internal.AppCacheService;
import org.myswan.service.internal.MasterService;
import org.myswan.service.internal.PatternService;
import org.myswan.service.internal.StockService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EtradeClient {

    private final RestTemplate restTemplate;
    private final AppCacheService appCacheService;
    private final MasterService masterService;
    private final PatternService patternService;
    private final StockService stockService;
    private final MongoTemplate mongoTemplate;

    @Value("${etrade.pattern.url}")
    private String etradePatternUrl;

    public EtradeClient(RestTemplate restTemplate,
                        AppCacheService appCacheService,
                        MasterService masterService,
                        PatternService patternService,
                        StockService stockService,
                        MongoTemplate mongoTemplate) {
        this.restTemplate = restTemplate;
        this.appCacheService = appCacheService;
        this.masterService = masterService;
        this.patternService = patternService;
        this.stockService = stockService;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Fetches patterns for a single ticker and enriches them with stock data.
     */
    public List<Pattern> fetchPatternsForTicker(String ticker) throws PatternNotFoundException {
        List<Pattern> list = fetchPatterns(ticker.toUpperCase());
        Optional<Stock> stock = stockService.getByTicker(ticker.toUpperCase());
        if (stock.isPresent()) {
            for (Pattern pattern : list) {
                pattern.setStock(stock.get());
            }
        }
        return list;
    }

    /**
     * Fetches patterns for all enabled masters (optionally filtered by myFavorite),
     * enriches them with stock data, replaces the pattern collection, and returns a summary string.
     */
    public String fetchAndSaveAllPatterns(String onlyMyFav) throws InterruptedException {
        log.info("Starting eTrade pattern fetch with parallel processing (onlyMyFav: {})...", onlyMyFav);

        List<Master> masters = masterService.list();
        log.info("Total masters in database: {}", masters.size());

        List<Master> enabledMasters = masters.stream()
            .filter(m -> {
                boolean enabled = m.isEtradePatternLookup();
                if (enabled) {
                    log.debug("Ticker {} - etradePatternLookup: true", m.getTicker());
                }
                if ("Y".equalsIgnoreCase(onlyMyFav)) {
                    boolean isFavorite = "Y".equals(m.getMyFavorite());
                    log.debug("Ticker {} - myFavorite: {}", m.getTicker(), m.getMyFavorite());
                    return enabled && isFavorite;
                }
                return enabled;
            })
            .toList();

        log.info("Found {} masters with eTrade pattern enabled (etradePatternLookup = true{})",
            enabledMasters.size(),
            "Y".equalsIgnoreCase(onlyMyFav) ? " and myFavorite = Y" : "");

        if (!enabledMasters.isEmpty() && enabledMasters.size() <= 10) {
            log.info("Enabled tickers: {}", enabledMasters.stream()
                .map(Master::getTicker)
                .collect(Collectors.joining(", ")));
        }

        if (enabledMasters.isEmpty()) {
            return "No masters enabled for eTrade pattern lookup";
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

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            int batchSize = 50;

            for (int i = 0; i < enabledMasters.size(); i += batchSize) {
                int end = Math.min(i + batchSize, enabledMasters.size());
                List<Master> batch = enabledMasters.subList(i, end);

                log.info("Processing batch: {} to {} of {}", i + 1, end, enabledMasters.size());

                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (Master master : batch) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            log.debug("Fetching patterns for ticker: {}", master.getTicker());
                            List<Pattern> patterns = fetchPatterns(master.getTicker());

                            if (patterns != null && !patterns.isEmpty()) {
                                allPatterns.addAll(patterns);
                                successCount.incrementAndGet();
                                log.info("✓ {} - Fetched {} patterns", master.getTicker(), patterns.size());
                            } else {
                                successCount.incrementAndGet();
                                log.info("✓ {} - No patterns found (valid ticker, no patterns)", master.getTicker());
                            }

                            Thread.sleep(400);

                        } catch (PatternNotFoundException e) {
                            notFoundCount.incrementAndGet();
                            notFoundTickers.add(master.getTicker());
                            log.warn("✗ {} - NOT FOUND (404) - Will disable in master", master.getTicker());

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

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                Thread.sleep(400);  // 2 second pause between batches

                log.info("Batch complete. Total progress - Success: {}, Not Found: {}, Failed: {}",
                         successCount.get(), notFoundCount.get(), failureCount.get());
            }
        }

        log.info("Fetched total {} patterns from eTrade. Success: {}, Failed: {}",
                 allPatterns.size(), successCount.get(), failureCount.get());

        enrichPatternsWithStockData(allPatterns);
        log.info("Enriched patterns with stock price data");

        patternService.deleteAll();
        log.info("Deleted existing patterns");

        patternService.saveAll(allPatterns);
        log.info("Saved {} new patterns", allPatterns.size());

        // Delete history for today
        //patternService.deleteHistoryByDate(today);
        //log.info("Deleted pattern history for today: {}", today);

        // Save to history
        //patternService.saveToHistory(allPatterns);
        //log.info("Saved patterns to history");

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

        return result.toString();
    }

    public void enrichPatternsWithStockData(List<Pattern> patterns) {
        try {
            Map<String, List<Pattern>> patternsByTicker = patterns.stream()
                .collect(Collectors.groupingBy(Pattern::getTicker));

            List<String> tickers = new ArrayList<>(patternsByTicker.keySet());
            Query query = new Query(Criteria.where("ticker").in(tickers));
            List<Stock> stocks = mongoTemplate.find(query, Stock.class);

            Map<String, Stock> stockMap = stocks.stream()
                .collect(Collectors.toMap(Stock::getTicker, stock -> stock));

            patterns.forEach(pattern -> {
                Stock stock = stockMap.get(pattern.getTicker());
                if (stock != null) {
                    pattern.setStock(stock);
                    log.debug("Enriched pattern for {} with stock data: Price={}, Change={}, High={}, Low={}",
                            pattern.getTicker(), stock.getPrice(), stock.getChange(), stock.getHigh(), stock.getLow());
                } else {
                    log.warn("No stock data found for ticker: {}", pattern.getTicker());
                }
            });

            log.info("Enriched {} patterns with stock data from {} stocks", patterns.size(), stocks.size());
        } catch (Exception e) {
            log.error("Error enriching patterns with stock data", e);
        }
    }

    public void disableTickerInMaster(String ticker) {
        try {
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

    public List<Pattern> fetchPatterns(String ticker) throws PatternNotFoundException {
        List<Pattern> patterns = new ArrayList<>();

        try {
            // Get eTrade token from AppCache
            AppCache appCache = appCacheService.getAppCache();
            String etradeToken = appCache != null ? appCache.getEtradeToken() : null;

            if (etradeToken == null || etradeToken.isEmpty()) {
                log.warn("eTrade token not found in AppCache for ticker: {}", ticker);
                return patterns;
            }

            // Build URL with ticker and token
            String url = etradePatternUrl.replace("TICKER", ticker) + etradeToken;

            log.debug("Fetching eTrade patterns for ticker: {} from URL: {}", ticker, url.substring(0, Math.min(url.length(), 100)) + "...");

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Make API call
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );

            // Parse response
            String responseBody = response.getBody();
            if (StringUtils.hasText(responseBody)) {
                patterns = parsePatternResponse(ticker, responseBody);
                log.info("Fetched {} patterns for ticker: {}", patterns.size(), ticker);
            } else {
                log.warn("Empty response from eTrade API for ticker: {}", ticker);
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("Ticker {} not found (404) at eTrade API - will be disabled", ticker);
                throw new PatternNotFoundException("Ticker not found: " + ticker);
            } else {
                log.error("HTTP error {} fetching patterns for ticker: {}", e.getStatusCode(), ticker, e);
                throw new RuntimeException("HTTP error for ticker: " + ticker, e);
            }
        } catch (Exception e) {
            log.error("Error fetching eTrade patterns for ticker: {}", ticker, e);
            throw new RuntimeException("Error fetching patterns for ticker: " + ticker, e);
        }

        return patterns;
    }

    private List<Pattern> parsePatternResponse(String ticker, String response) {
        List<Pattern> patterns = new ArrayList<>();
        int longCount = 0;
        int shortCount = 0;
        final LocalDate today = LocalDate.now();

        try {
            JSONObject json = new JSONObject(response);
            if (json.has("events") && !json.isNull("events")) {
                JSONArray array = json.getJSONArray("events");

                // First pass: count long and short patterns with entry price
                for (int i = 0; i < array.length(); i++) {
                    JSONObject event = array.getJSONObject(i);
                    if ("true".equals(UtilHelper.checkForPresence(event, "active"))) {
                        // Check if has entry price
                        boolean hasEntry = false;
                        if (event.has("endPrices") && !event.isNull("endPrices")) {
                            JSONObject endPrices = event.getJSONObject("endPrices");
                            String entryPrice = UtilHelper.stripStringToTwoDecimals(
                                UtilHelper.checkForPresence(endPrices, "breakout"), false);
                            hasEntry = StringUtils.hasText(entryPrice);
                        }

                        if (hasEntry) {
                            // Count by trend type
                            if (event.has("eventType") && !event.isNull("eventType")) {
                                JSONObject eventType = event.getJSONObject("eventType");
                                String tradeType = UtilHelper.checkForPresence(eventType, "tradeType");
                                if ("long".equalsIgnoreCase(tradeType)) {
                                    longCount++;
                                } else if ("short".equalsIgnoreCase(tradeType)) {
                                    shortCount++;
                                }
                            }
                        }
                    }
                }

                // Second pass: create pattern objects
                for (int i = 0; i < array.length(); i++) {
                    JSONObject event = array.getJSONObject(i);

                    // Only process active events
                    if ("true".equals(UtilHelper.checkForPresence(event, "active"))) {
                        Pattern pattern = new Pattern();
                        pattern.setTicker(ticker);
                        pattern.setHistDate(UtilHelper.formatLocalDateToString(LocalDate.now()));
                        //pattern.setHistDate(UtilHelper.formatLocalDateToString(LocalDate.now().minusDays(1)));
                        pattern.setStop(UtilHelper.stripStringToTwoDecimals(
                            UtilHelper.checkForPresence(event, "deactivationPrice"), false));
                        pattern.setId(UtilHelper.checkForPresence(event, "eventId"));
                        pattern.setEventId(UtilHelper.checkForPresence(event, "eventId"));
                        pattern.setName(UtilHelper.checkForPresence(event, "eventLabel"));

                        // Parse dates from the event
                        JSONObject dates = event.optJSONObject("dates");
                        String eventBeginDate = null;
                        String eventEndDate = null;
                        if (dates != null) {
                            String eventBegin = UtilHelper.checkForPresence(dates, "eventBegin");
                            String eventEnd = UtilHelper.checkForPresence(dates, "eventEnd");

                            // Extract YYYY-MM-DD from ISO timestamp
                            if (StringUtils.hasText(eventBegin) && eventBegin.length() >= 10) {
                                eventBeginDate = eventBegin.substring(0, 10);
                            }
                            if (StringUtils.hasText(eventEnd) && eventEnd.length() >= 10) {
                                eventEndDate = eventEnd.substring(0, 10);
                            }
                        }
                        pattern.setEventBeginDate(eventBeginDate);
                        pattern.setEventEndDate(eventEndDate);
                        // Pattern emergence date is when it was confirmed (eventEnd)
                        pattern.setPatternEmergenceDate(eventEndDate);

                        String targetDate = UtilHelper.checkForPresence(event, "lastPossibleActive");
                        if (StringUtils.hasText(targetDate) && targetDate.length() > 10) {
                            targetDate = targetDate.substring(0, 10);
                        }
                        LocalDate parsedTargetDate = null;
                        if (StringUtils.hasText(targetDate)) {
                            try {
                                parsedTargetDate = LocalDate.parse(targetDate);
                            } catch (DateTimeParseException ex) {
                                log.debug("Unable to parse target date '{}' for ticker {}", targetDate, ticker, ex);
                            }
                        }
                        if (parsedTargetDate != null && parsedTargetDate.isBefore(today)) {
                            continue;
                        }
                        pattern.setTargetDate(targetDate);
                        pattern.setStatus("Y");

                        // Parse endPrices and get entry price
                        String entryPrice = "";
                        if (event.has("endPrices") && !event.isNull("endPrices")) {
                            JSONObject endPrices = event.getJSONObject("endPrices");
                            entryPrice = UtilHelper.stripStringToTwoDecimals(
                                UtilHelper.checkForPresence(endPrices, "breakout"), false);
                            pattern.setEntry(entryPrice);
                        }

                        // Parse eventType
                        if (event.has("eventType") && !event.isNull("eventType")) {
                            JSONObject eventType = event.getJSONObject("eventType");
                            pattern.setTrend(UtilHelper.checkForPresence(eventType, "tradeType"));
                        }

                        // Parse targetPrice
                        if (event.has("targetPrice") && !event.isNull("targetPrice")) {
                            JSONObject targetPrice = event.getJSONObject("targetPrice");
                            pattern.setMinPT(UtilHelper.stripStringToTwoDecimals(
                                UtilHelper.checkForPresence(targetPrice, "lower"), false));
                            pattern.setMaxPT(UtilHelper.stripStringToTwoDecimals(
                                UtilHelper.checkForPresence(targetPrice, "upper"), false));
                        }

                        // Only add pattern if it has a valid entry price
                        if (StringUtils.hasText(entryPrice)) {
                            // Set pattern counts
                            pattern.setNoOfLongPatterns(longCount);
                            pattern.setNoOfShortPatterns(shortCount);

                            patterns.add(pattern);
                            log.debug("Added pattern for ticker: {} with entry: {}", ticker, entryPrice);
                        } else {
                            log.debug("Skipped pattern for ticker: {} - no entry price", ticker);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing eTrade pattern response for ticker: {}", ticker, e);
        }

        log.info("Parsed {} patterns for ticker: {} (Long: {}, Short: {})",
                 patterns.size(), ticker, longCount, shortCount);
        return patterns;
    }
}

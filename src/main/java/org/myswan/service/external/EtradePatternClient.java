package org.myswan.service.external;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.myswan.common.UtilHelper;
import org.myswan.model.AppCache;
import org.myswan.model.Pattern;
import org.myswan.service.internal.AppCacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class EtradePatternClient {

    private final RestTemplate restTemplate;
    private final AppCacheService appCacheService;

    @Value("${etrade.pattern.url}")
    private String etradePatternUrl;

    public EtradePatternClient(RestTemplate restTemplate, AppCacheService appCacheService) {
        this.restTemplate = restTemplate;
        this.appCacheService = appCacheService;
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
                        pattern.setName(UtilHelper.checkForPresence(event, "eventLabel"));

                        String targetDate = UtilHelper.checkForPresence(event, "lastPossibleActive");
                        if (StringUtils.hasText(targetDate) && targetDate.length() > 10) {
                            targetDate = targetDate.substring(0, 10);
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


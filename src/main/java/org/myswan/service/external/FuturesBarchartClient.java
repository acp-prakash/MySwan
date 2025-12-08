package org.myswan.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.myswan.model.collection.Futures;
import org.myswan.model.collection.Rating;
import org.myswan.service.internal.AppCacheService;
import org.myswan.service.internal.FuturesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class FuturesBarchartClient {
    private static final Logger log = LoggerFactory.getLogger(FuturesBarchartClient.class);

    private final HttpClient httpClient;
    private final String gcUrl;
    private final String esUrl;
    private final String emdUrl;
    private final String vxUrl;
    private final FuturesService futuresService;
    private final AppCacheService appCacheService;
    private final ObjectMapper objectMapper;

    public FuturesBarchartClient(
            @Value("${barchart.api.url.gc}") String gcUrl,
            @Value("${barchart.api.url.es}") String esUrl,
            @Value("${barchart.api.url.emd}") String emdUrl,
            @Value("${barchart.api.url.vx}") String vxUrl,
            FuturesService futuresService,
            AppCacheService appCacheService,
            ObjectMapper objectMapper
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.gcUrl = gcUrl != null ? gcUrl : "";
        this.esUrl = esUrl != null ? esUrl : "";
        this.emdUrl = emdUrl != null ? emdUrl : "";
        this.vxUrl = vxUrl != null ? vxUrl : "";
        this.futuresService = futuresService;
        this.appCacheService = appCacheService;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetch all futures quotes (GC, ES, EMD, VX) - returns ALL contract months for each
     */
    public List<Futures> fetchAllFutures() throws IOException, InterruptedException {
        List<Futures> allFutures = new ArrayList<>();

        log.info("Fetching GC futures contracts...");
        List<Futures> gcContracts = fetchMultipleFutures(gcUrl, "GC");
        allFutures.addAll(gcContracts);

        log.info("Fetching ES futures contracts...");
        List<Futures> esContracts = fetchMultipleFutures(esUrl, "ES");
        allFutures.addAll(esContracts);

        log.info("Fetching EMD futures contracts...");
        List<Futures> emdContracts = fetchMultipleFutures(emdUrl, "EMD");
        allFutures.addAll(emdContracts);

        log.info("Fetching VX futures contracts...");
        List<Futures> vxContracts = fetchMultipleFutures(vxUrl, "VX");
        allFutures.addAll(vxContracts);

        log.info("Total futures contracts fetched: {}", allFutures.size());
        updateFuturesQuotes(allFutures);
        return allFutures;
    }

    private List<Futures> fetchMultipleFutures(String url, String baseTicker) throws IOException, InterruptedException {
        log.debug("Requesting Barchart Futures URL for {}: {}", baseTicker, url);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(""));

        // Attach cached headers (token/cookie) if available
        if (appCacheService != null && appCacheService.getAppCache() != null) {
            String token = appCacheService.getAppCache().getBarchartToken();
            String cookie = appCacheService.getAppCache().getBarchartCookie();
            if (token != null && !token.isBlank()) reqBuilder.header("x-xsrf-token", token);
            if (cookie != null && !cookie.isBlank()) reqBuilder.header("Cookie", cookie);
        }

        HttpRequest request = reqBuilder.build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String body = response.body();
            log.error("Barchart futures request failed for {}: status={}, body={}", baseTicker, status, body);
            return List.of();
        }

        String respBody = response.body();
        List<Futures> futuresList = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode dataNode = root.has("data") ? root.get("data") : root;
            if (dataNode == null || dataNode.isNull()) {
                log.warn("No data node found for futures {}", baseTicker);
                return List.of();
            }

            // Handle array of futures contracts
            if (dataNode.isArray()) {
                for (JsonNode itemNode : dataNode) {
                    JsonNode rawNode = itemNode.has("raw") && itemNode.get("raw") != null && !itemNode.get("raw").isNull()
                            ? itemNode.get("raw")
                            : itemNode;
                    Futures futures = mapToFutures(rawNode, itemNode, baseTicker);
                    if (futures != null) {
                        futuresList.add(futures);
                    }
                }
            } else {
                // Single object response
                JsonNode rawNode = dataNode.has("raw") && dataNode.get("raw") != null && !dataNode.get("raw").isNull()
                        ? dataNode.get("raw")
                        : dataNode;
                Futures futures = mapToFutures(rawNode, dataNode, baseTicker);
                if (futures != null) {
                    futuresList.add(futures);
                }
            }

            log.info("Fetched {} contracts for {}", futuresList.size(), baseTicker);
            return futuresList;
        } catch (Exception e) {
            log.error("Failed to parse/convert Barchart futures response for {}", baseTicker, e);
            return List.of();
        }
    }

    private Futures mapToFutures(JsonNode raw, JsonNode item, String baseTicker) {
        Futures futures = new Futures();

        // Get the full symbol from Barchart (e.g., "GS*0", "ES*0")
        String fullSymbol = getTextValue(raw, "symbol");
        if (fullSymbol == null || fullSymbol.isBlank()) {
            log.warn("No symbol found in response for base ticker {}", baseTicker);
            return null;
        }

        // Set both ticker AND id to the full symbol (like Stock does)
        futures.setTicker(fullSymbol);
        futures.setId(fullSymbol);
        futures.setType("FUTURE");
        futures.setHistDate(LocalDate.now());
        //futures.setHistDate(LocalDate.now().minusDays(1));

        // Price fields
        futures.setPrice(getDoubleValue(raw, "dailyLastPrice", "lastPrice"));
        futures.setOpen(getDoubleValue(raw, "dailyOpenPrice", "openPrice"));
        futures.setHigh(getDoubleValue(raw, "dailyHighPrice", "highPrice"));
        futures.setLow(getDoubleValue(raw, "dailyLowPrice", "lowPrice"));
        futures.setChange(getDoubleValue(raw, "dailyPriceChange", "priceChange"));
        futures.setVolume(getDoubleValue(raw, "dailyVolume", "volume"));
        futures.setPrevClose(getDoubleValue(raw, "dailyPreviousPrice", "previousPrice"));

        // Futures-specific fields
        futures.setOpenInterest(getDoubleValue(raw, "openInterest"));
        futures.setExpiryDate(getTextValue(raw, "contractExpirationDate"));
        futures.setExpiryDays(getIntValue(raw, "daysToContractExpiration"));

        // Calculate up/down days from 5-day data (simplified)
        double high5d = getDoubleValue(raw, "dailyHighPrice5d", "highPrice5d");
        double low5d = getDoubleValue(raw, "dailyLowPrice5d", "lowPrice5d");
        futures.setUpHigh(high5d);
        futures.setDownLow(low5d);

        // Rating fields from Barchart opinions
        Rating rating = new Rating();
        String shortOpinion = getFirstNonNull(item, "dailyOpinionShortTerm", "opinionShortTerm");
        String longOpinion = getFirstNonNull(item, "dailyOpinionLongTerm", "opinionLongTerm");
        String overallOpinion = getFirstNonNull(item, "dailyOpinion", "opinion");
        String trendSignal = getFirstNonNull(item, "dailyTrendSpotterSignal", "trendSpotterSignal");

        rating.setBtShortRating(shortOpinion);
        rating.setBtLongRating(longOpinion);
        rating.setBtRating(overallOpinion);
        rating.setBtTrend(trendSignal);
        futures.setRating(rating);

        log.debug("Mapped futures: ticker={}, price={}, open={}, high={}, low={}, expiry={}, expiryDays={}",
                futures.getTicker(), futures.getPrice(), futures.getOpen(),
                futures.getHigh(), futures.getLow(), futures.getExpiryDate(), futures.getExpiryDays());

        return futures;
    }

    private String getTextValue(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asText();
            }
        }
        return null;
    }

    private double getDoubleValue(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asDouble(0.0);
            }
        }
        return 0.0;
    }

    private int getIntValue(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.get(field).isNull()) {
                return node.get(field).asInt(0);
            }
        }
        return 0;
    }

    private String getFirstNonNull(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.get(field).isNull()) {
                JsonNode val = node.get(field);
                if (val.isTextual()) return val.asText();
                if (val.isNumber()) return String.valueOf(val.asInt());
            }
        }
        return null;
    }

    private void updateFuturesQuotes(List<Futures> futures) {
        if (futures == null || futures.isEmpty()) return;

        futuresService.bulkPatch(futures,
                Set.of("id", "ticker", "type", "histDate", "price", "change", "open", "high", "low",
                        "volume", "prevClose", "openInterest", "expiryDate", "expiryDays",
                        "upHigh", "downLow", "rating"));
        log.info("Updated {} futures records in MongoDB", futures.size());
    }
}


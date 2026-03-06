package org.myswan.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.myswan.model.collection.Options;
import org.myswan.repository.OptionsRepository;
import org.myswan.service.internal.AppCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RobinHoodClient {

    private static final Logger log = LoggerFactory.getLogger(RobinHoodClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppCacheService appCacheService;
    private final OptionsRepository optionsRepository;
    private final MongoTemplate mongoTemplate;

    public RobinHoodClient(
            ObjectMapper objectMapper,
            AppCacheService appCacheService,
            OptionsRepository optionsRepository,
            MongoTemplate mongoTemplate
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.appCacheService = appCacheService;
        this.optionsRepository = optionsRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Options> getOptions() {
        return new ArrayList<>();
    }

    /**
     * Calls the Robinhood options URL stored in AppCache, parses the response,
     * upserts each option into the options collection, and returns the count saved.
     */
    public int fetchAndSaveOptions(LocalDate histDate) {
        var cache = appCacheService.getAppCache();
        if (cache == null) {
            log.warn("fetchAndSaveOptions: AppCache not found");
            return 0;
        }
        String robinhoodUrl = cache.getRobinhoodUrl();
        String robinhoodToken = cache.getRobinhoodToken();

        if (robinhoodUrl == null || robinhoodUrl.isBlank()) {
            log.warn("fetchAndSaveOptions: robinhoodUrl not configured in Settings");
            return 0;
        }
        if (robinhoodToken == null || robinhoodToken.isBlank()) {
            log.warn("fetchAndSaveOptions: robinhoodToken not configured in Settings");
            return 0;
        }

        LocalDate effectiveDate = histDate != null ? histDate : LocalDate.now();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(robinhoodUrl))
                    .header("Authorization", "Bearer " + robinhoodToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("fetchAndSaveOptions: Robinhood returned HTTP {}: {}", response.statusCode(), response.body());
                return 0;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.get("results");
            if (results == null || !results.isArray()) {
                log.warn("fetchAndSaveOptions: no 'results' array in Robinhood response");
                return 0;
            }

            int saved = 0;
            for (JsonNode node : results) {
                Options opt = mapNodeToOptions(node, effectiveDate);
                if (opt == null) continue;
                // Upsert by optionId
                if (opt.getOptionId() != null && !opt.getOptionId().isBlank()) {
                    Query q = Query.query(Criteria.where("optionId").is(opt.getOptionId()));
                    List<Options> existing = mongoTemplate.find(q, Options.class, "options");
                    if (!existing.isEmpty()) {
                        Options prev = existing.getFirst();
                        opt.setId(prev.getId());
                        // preserve priceOnAdd from the first time it was saved
                        double priceOnAdd = prev.getPriceOnAdd() > 0 ? prev.getPriceOnAdd() : prev.getPrice();
                        opt.setPriceOnAdd(priceOnAdd);
                        // changeSinceAdded = current price - priceOnAdd
                        double changeSinceAdded = priceOnAdd > 0
                                ? Math.round((opt.getPrice() - priceOnAdd) * 10000.0) / 10000.0
                                : 0.0;
                        opt.setChangeSinceAdded(changeSinceAdded);
                    } else {
                        // first time saving — priceOnAdd = current price
                        opt.setPriceOnAdd(opt.getPrice());
                        opt.setChangeSinceAdded(0.0);
                    }
                }
                optionsRepository.save(opt);
                saved++;
            }
            log.info("fetchAndSaveOptions: saved/updated {} options from Robinhood", saved);
            return saved;

        } catch (Exception ex) {
            log.error("fetchAndSaveOptions: failed - {}", ex.getMessage(), ex);
            return 0;
        }
    }

    private Options mapNodeToOptions(JsonNode node, LocalDate histDate) {
        try {
            Options opt = new Options();
            opt.setHistDate(histDate);

            // Extract option ID from the instrument URL
            String instrumentUrl = getStringValue(node.get("instrument"));
            if (instrumentUrl != null && !instrumentUrl.isBlank()) {
                String[] parts = instrumentUrl.split("/");
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (!parts[i].isBlank()) { opt.setOptionId(parts[i]); break; }
                }
            }

            opt.setTicker(getStringValue(node.get("symbol")));

            // occ_symbol format: "SPY   260320C00685000" — ticker padded to 6 chars, rest is the option name
            String occSymbol = getStringValue(node.get("occ_symbol"));
            if (occSymbol != null && occSymbol.length() > 6) {
                opt.setOptionName(occSymbol.substring(6).trim());
            }

            double markPrice = getDoubleValue(node.get("mark_price"));
            double prevClose = getDoubleValue(node.get("previous_close_price"));

            opt.setPrice(markPrice);
            opt.setHigh(getDoubleValue(node.get("high_price")));
            opt.setLow(getDoubleValue(node.get("low_price")));
            opt.setVolume((int) getDoubleValue(node.get("volume")));
            opt.setOpenInterest((int) getDoubleValue(node.get("open_interest")));
            opt.setDelta(getDoubleValue(node.get("delta")));
            opt.setType(opt.getDelta() > 0 ? "CALL" : "PUT");
            opt.setGamma(getDoubleValue(node.get("gamma")));
            opt.setTheta(getDoubleValue(node.get("theta")));
            opt.setIv(getDoubleValue(node.get("implied_volatility")));
            opt.setVega(getDoubleValue(node.get("vega")));

            // change = mark_price - previous_close_price (negative means loss)
            double change = prevClose > 0 ? Math.round((markPrice - prevClose) * 10000.0) / 10000.0 : 0.0;
            opt.setChange(change);

            return opt;
        } catch (Exception ex) {
            log.warn("mapNodeToOptions: failed to map node - {}", ex.getMessage());
            return null;
        }
    }

    private String getStringValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private double getDoubleValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0.0;
        }
        double value = node.asDouble();
        return Math.round(value * 100.0) / 100.0;
    }
}

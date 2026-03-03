package org.myswan.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.myswan.model.collection.Options;
import org.myswan.repository.OptionsRepository;
import org.myswan.service.external.vo.TradingViewVO;
import org.myswan.service.internal.AppCacheService;
import org.myswan.service.internal.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RobinHoodClient {

    private static final Logger log = LoggerFactory.getLogger(RobinHoodClient.class);

    private final HttpClient httpClient;
    private final URI scanStockUri;
    private final URI scanEtfUri;
    private final String payloadStockJson;
    private final String payloadEtfJson;
    private final ObjectMapper objectMapper;
    private final StockService stockService;
    private final AppCacheService appCacheService;
    private final OptionsRepository optionsRepository;
    private final MongoTemplate mongoTemplate;

    public RobinHoodClient(
            @Value("${tradingview.scan.stock.url}") String scanStockUrl,
            @Value("${tradingview.scan.stock.payload}") Resource scanStockPayload,
            @Value("${tradingview.scan.etf.url}") String scanEtfUrl,
            @Value("${tradingview.scan.etf.payload}") Resource scanEtfPayload,
            ObjectMapper objectMapper,
            StockService stockService,
            AppCacheService appCacheService,
            OptionsRepository optionsRepository,
            MongoTemplate mongoTemplate
    ) throws IOException {

        if (scanStockUrl == null || scanStockUrl.isBlank()) {
            throw new IllegalArgumentException("tradingview.scan.stock.url must be configured");
        }
        if (scanEtfUrl == null || scanEtfUrl.isBlank()) {
            throw new IllegalArgumentException("tradingview.scan.etf.url must be configured");
        }

        this.httpClient = HttpClient.newHttpClient();
        this.scanStockUri = URI.create(scanStockUrl);
        this.scanEtfUri = URI.create(scanEtfUrl);
        this.objectMapper = objectMapper;
        this.stockService = stockService;
        this.appCacheService = appCacheService;
        this.optionsRepository = optionsRepository;
        this.mongoTemplate = mongoTemplate;

        // ----- STOCK payload -----
        String jsonStock = "{}";
        if (scanStockPayload != null && scanStockPayload.exists()) {
            try (var in = scanStockPayload.getInputStream()) {
                jsonStock = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            log.warn("TradingView STOCK payload resource not found or not configured: {}", scanStockPayload);
        }
        this.payloadStockJson = jsonStock;

        // ----- ETF payload -----
        String jsonEtf = "{}";
        if (scanEtfPayload != null && scanEtfPayload.exists()) {
            try (var in = scanEtfPayload.getInputStream()) {
                jsonEtf = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            log.warn("TradingView ETF payload resource not found or not configured: {}", scanEtfPayload);
        }
        this.payloadEtfJson = jsonEtf;
    }

    public JsonNode scanStocksOrETF(URI scanUri, String payload) throws IOException, InterruptedException {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(scanUri)
                .header("Content-Type", "application/json");

        // Attach TradingView token and cookie from app cache if present
        if (appCacheService != null && appCacheService.getAppCache() != null) {
            var cache = appCacheService.getAppCache();
            String cookie = cache.getTradingViewCookie();
            if (cookie != null && !cookie.isBlank()) {
                reqBuilder.header("Cookie", cookie);
            }
        }

        HttpRequest request = reqBuilder.POST(HttpRequest.BodyPublishers.ofString(payload)).build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("TradingView stock or ETF scan failed with status " + status +
                    ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    public String updateTradingView() {
        try {
            JsonNode stockRoot = scanStocksOrETF(scanStockUri, payloadStockJson);
            List<TradingViewVO> stocks = mapToTradingViewVO(stockRoot, "STOCK");
            JsonNode etfRoot = scanStocksOrETF(scanEtfUri, payloadEtfJson);
            List<TradingViewVO> etfs = mapToTradingViewVO(etfRoot, "ETF");
            stocks.addAll(etfs);
            stockService.updateTradingView(stocks);
            return "Updated TradingView stocks and ETFs: " + stocks.size() + " stocks and " + etfs.size() + " etfs";
        }
        catch(Exception ex)
        {
            return "Failed to update TradingView stocks and ETFs: " + ex.getMessage();
        }
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

    public JsonNode scanETFs() throws IOException, InterruptedException {
        return scanStocksOrETF(scanEtfUri, payloadEtfJson);
    }

    private List<TradingViewVO> mapToTradingViewVO(JsonNode root, String type) {
        List<TradingViewVO> result = new ArrayList<>();

        JsonNode dataNode = root.get("data");
        if (dataNode == null || !dataNode.isArray()) {
            log.warn("No data array found in TradingView response");
            return result;
        }

        for (JsonNode item : dataNode) {
            // Each item has "s" (symbol) and "d" (data array)
            JsonNode dArray = item.get("d");
            if (dArray == null || !dArray.isArray() || dArray.size() < 33) {
                log.warn("Skipping invalid item with d array size: {}", dArray != null ? dArray.size() : 0);
                continue;
            }

            try {
                TradingViewVO vo = new TradingViewVO();

                // Map based on the column order in the payload JSON
                // 0: name (ticker), 1: description, 2: update_mode, 3: type
                vo.setTicker(getStringValue(dArray.get(0)));
                vo.setType(type);

                // 4: close, 5: change_abs, 6: open, 7: high, 8: low
                vo.setPrice(getDoubleValue(dArray.get(4)));
                vo.setChange(getDoubleValue(dArray.get(5)));
                vo.setOpen(getDoubleValue(dArray.get(6)));
                vo.setHigh(getDoubleValue(dArray.get(7)));
                vo.setLow(getDoubleValue(dArray.get(8)));

                // 9: TechRating_1D, 10: MARating_1D, 11: AnalystRating
                vo.setTechRating(getStringValue(dArray.get(9)));
                vo.setMaRating(getStringValue(dArray.get(10)));
                vo.setAnalystsRating(getStringValue(dArray.get(11)));

                // 12: SMA9, 13: SMA20, 14: SMA21, 15: SMA50, 16: SMA100, 17: SMA200
                vo.setSma9(getDoubleValue(dArray.get(12)));
                vo.setSma20(getDoubleValue(dArray.get(13)));
                vo.setSma21(getDoubleValue(dArray.get(14)));
                vo.setSma50(getDoubleValue(dArray.get(15)));
                vo.setSma100(getDoubleValue(dArray.get(16)));
                vo.setSma200(getDoubleValue(dArray.get(17)));

                // 18: MACD.macd, 19: RSI, 20: ATR, 21: Mom
                vo.setMacd1226(getDoubleValue(dArray.get(18)));
                vo.setRsi14(getDoubleValue(dArray.get(19)));
                vo.setAtr14(getDoubleValue(dArray.get(20)));
                vo.setMomentum(getDoubleValue(dArray.get(21)));

                // 22: EMA9, 23: EMA20, 24: EMA21, 25: EMA50, 26: EMA100, 27: EMA200
                vo.setEma9(getDoubleValue(dArray.get(22)));
                vo.setEma20(getDoubleValue(dArray.get(23)));
                vo.setEma21(getDoubleValue(dArray.get(24)));
                vo.setEma50(getDoubleValue(dArray.get(25)));
                vo.setEma100(getDoubleValue(dArray.get(26)));
                vo.setEma200(getDoubleValue(dArray.get(27)));

                // 28: volume, 29: volume_change_abs, 30: average_volume_10d_calc, 31: VWAP
                vo.setVolume(getDoubleValue(dArray.get(28)));
                vo.setVolumeChange(getDoubleValue(dArray.get(29)));
                vo.setAvgVolume10D(getDoubleValue(dArray.get(30)));
                vo.setVwap(getDoubleValue(dArray.get(31)));

                result.add(vo);
            } catch (Exception e) {
                log.error("Error mapping TradingView row to VO: {}", e.getMessage(), e);
            }
        }

        log.info("Mapped {} TradingView records", result.size());
        return result;
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

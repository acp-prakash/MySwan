package org.myswan.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.myswan.service.external.vo.TradingViewVO;
import org.myswan.service.internal.StockService;
import org.myswan.service.internal.AppCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class TradingViewClient {

    private static final Logger log = LoggerFactory.getLogger(TradingViewClient.class);

    private final HttpClient httpClient;
    private final URI scanStockUri;
    private final URI scanEtfUri;
    private final String payloadStockJson;
    private final String payloadEtfJson;
    private final ObjectMapper objectMapper;
    private final StockService stockService;
    private final AppCacheService appCacheService;

    public TradingViewClient(
            @Value("${tradingview.scan.stock.url}") String scanStockUrl,
            @Value("${tradingview.scan.stock.payload}") Resource scanStockPayload,
            @Value("${tradingview.scan.etf.url}") String scanEtfUrl,
            @Value("${tradingview.scan.etf.payload}") Resource scanEtfPayload,
            ObjectMapper objectMapper,
            StockService stockService,
            AppCacheService appCacheService
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

    public JsonNode scanStocks() throws IOException, InterruptedException {
        return scanStocksOrETF(scanStockUri, payloadStockJson);
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

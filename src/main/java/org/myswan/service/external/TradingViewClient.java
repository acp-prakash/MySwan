package org.myswan.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class TradingViewClient {

    private static final Logger log = LoggerFactory.getLogger(TradingViewClient.class);

    private final HttpClient httpClient;
    private final URI scanStockUri;
    private final URI scanEtfUri;
    private final String payloadStockJson;
    private final String payloadEtfJson;
    private final ObjectMapper objectMapper;

    public TradingViewClient(
            @Value("${tradingview.scan.stock.url}") String scanStockUrl,
            @Value("${tradingview.scan.stock.payload}") Resource scanStockPayload,
            @Value("${tradingview.scan.etf.url}") String scanEtfUrl,
            @Value("${tradingview.scan.etf.payload}") Resource scanEtfPayload,
            ObjectMapper objectMapper
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

    public JsonNode scanStocks() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(scanStockUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payloadStockJson))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("TradingView stock scan failed with status " + status +
                    ": " + response.body());
        }

        return objectMapper.readTree(response.body());
    }

    public JsonNode scanETFs() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(scanEtfUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payloadEtfJson))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IOException("TradingView ETF scan failed with status " + status +
                    ": " + response.body());
        }

        return objectMapper.readTree(response.body());
    }
}
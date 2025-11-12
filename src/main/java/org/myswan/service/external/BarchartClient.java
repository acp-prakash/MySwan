package org.myswan.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.myswan.model.Master;
import org.myswan.model.Rating;
import org.myswan.model.Stock;
import org.myswan.repository.MasterRepository;
import org.myswan.service.external.vo.BarchartVO;
import org.myswan.service.internal.AppCacheService;
import org.myswan.service.internal.StockService;
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
import java.util.stream.Collectors;

import static org.myswan.common.UtilHelper.getFirstNonNull;

@Service
public class BarchartClient {
    private static final Logger log = LoggerFactory.getLogger(BarchartClient.class);

    private final HttpClient httpClient;
    private final String apiBase;
    private final String dailySuffix;
    private final String intraSuffix;
    private final MasterRepository masterRepository;
    private final StockService stockService;
    private final AppCacheService appCacheService;
    private final ObjectMapper objectMapper;

    public BarchartClient(
            @Value("${barchart.api.url}") String apiBase,
            @Value("${barchart.daily}") String dailySuffix,
            @Value("${barchart.intra}") String intraSuffix,
            MasterRepository masterRepository,
            StockService stockService,
            AppCacheService appCacheService,
            ObjectMapper objectMapper
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.apiBase = apiBase != null ? apiBase : "";
        this.dailySuffix = dailySuffix != null ? dailySuffix : "";
        this.intraSuffix = intraSuffix != null ? intraSuffix : "";
        this.masterRepository = masterRepository;
        this.stockService = stockService;
        this.appCacheService = appCacheService;
        this.objectMapper = objectMapper;
    }

    public List<BarchartVO> getDailyQuotes() throws IOException, InterruptedException {
        return fetchQuotes(dailySuffix);
    }

    public List<BarchartVO> getIntraDayQuotes() throws IOException, InterruptedException {
        return fetchQuotes(intraSuffix);
    }

    private List<BarchartVO> fetchQuotes(String suffix) throws IOException, InterruptedException {
        List<Master> masters = masterRepository.findAll();
        if (masters == null || masters.isEmpty()) {
            log.info("No masters found in DB - returning empty list for Barchart");
            return List.of();
        }

        String symbols = masters.stream()
                .map(Master::getTicker)
                .filter(t -> t != null && !t.isEmpty())
                .collect(Collectors.joining(","));

        if (symbols.isEmpty()) {
            log.info("No valid tickers found in masters - returning empty list");
            return List.of();
        }

        // Build URL: keep suffix separate; many barchart endpoints accept POST with body symbols
        String url = apiBase + symbols + suffix; // do not append symbols to URL here if POST body is used
        log.info("Requesting Barchart URL {}", url);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(""));

        // attach cached headers (token/cookie) if available
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
            log.error("Barchart request failed: status={}, body={}", status, body);
            throw new IOException("Barchart request failed with status " + status);
        }

        String respBody = response.body();
        try {
            JsonNode root = objectMapper.readTree(respBody);
            JsonNode dataNode = root.has("data") ? root.get("data") : root;
            if (dataNode == null || dataNode.isNull()) return List.of();

            List<BarchartVO> out = new ArrayList<>();
            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    JsonNode src = item.has("raw") && item.get("raw") != null && !item.get("raw").isNull() ? item.get("raw") : item;
                    try {
                        BarchartVO vo = objectMapper.convertValue(src, BarchartVO.class);
                        vo.setOpinion(getFirstNonNull(item, "dailyOpinion", "opinion"));
                        vo.setOpinionShortTerm(getFirstNonNull(item, "dailyOpinionShortTerm", "opinionShortTerm"));
                        vo.setOpinionLongTerm(getFirstNonNull(item, "dailyOpinionLongTerm", "opinionLongTerm"));
                        vo.setTrendSpotterSignal(getFirstNonNull(item, "dailyTrendSpotterSignal", "trendSpotterSignal"));
                        out.add(vo);
                    } catch (Exception ex) {
                        log.warn("Failed to map item to BarchartVO, skipping: {}", item.toString());
                    }
                }
                updateStockQuotes(out);
                return out;
            } else {
                JsonNode src = dataNode.has("raw") && dataNode.get("raw") != null && !dataNode.get("raw").isNull() ? dataNode.get("raw") : dataNode;
                BarchartVO single = objectMapper.convertValue(src, BarchartVO.class);
                return List.of(single);
            }
        } catch (Exception e) {
            log.error("Failed to parse/convert Barchart response JSON", e);
            throw new IOException("Failed to parse Barchart response", e);
        }
    }

    public void updateStockQuotes(List<BarchartVO> quotes) {
        if (quotes == null || quotes.isEmpty()) return;

        List<Stock> stocks = quotes.stream()
                .filter(vo -> vo.getSymbol() != null && !vo.getSymbol().isBlank())
                .map(this::applyQuoteToStock)
                .toList();

        stockService.bulkPatch(stocks,
                Set.of("id","ticker","histDate","price", "change", "open", "high", "low",
                        "volume", "prevClose", "priceChg5D", "priceChg10D", "priceChg20D",
                        "earningsDate", "rating.btAnalysts","rating.btAnalystRating","rating.btShortRating",
                        "rating.btLongRating","rating.btRating","rating.btTrend"));
    }

    private Stock applyQuoteToStock(BarchartVO vo) {
        Stock s = new Stock();
        s.setHistDate(LocalDate.now());
        s.setTicker(vo.getSymbol());
        s.setId(vo.getSymbol());
        s.setPrice(vo.getPrice());
        s.setChange(vo.getChange());
        s.setOpen(vo.getOpen());
        s.setHigh(vo.getHigh());
        s.setLow(vo.getLow());
        s.setVolume(vo.getVolume());
        s.setPrevClose(vo.getPreviousPrice());
        s.setPriceChg5D(vo.getPriceChange5d());
        s.setPriceChg10D(vo.getPriceChange10d());
        s.setPriceChg20D(vo.getPriceChange20d());
        s.setEarningsDate(vo.getNextEarningsDate());
        s.setRating(new Rating());
        if (s.getRating() == null) {
            s.setRating(new Rating());
        }
        mapRating(s.getRating(), vo);
        return s;
    }

    private void mapRating(Rating rating, BarchartVO vo) {
        rating.setBtAnalysts(vo.getTotalRecommendations());
        rating.setBtAnalystRating(vo.getAverageRecommendation());
        rating.setBtShortRating(vo.getOpinionShortTerm());
        rating.setBtLongRating(vo.getOpinionLongTerm());
        rating.setBtRating(vo.getOpinion());
        rating.setBtTrend(vo.getTrendSpotterSignal());
    }
}
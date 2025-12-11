package org.myswan.controller.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Stock;
import org.myswan.model.collection.Watchlist;
import org.myswan.service.internal.WatchlistService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final MongoTemplate mongoTemplate;

    public WatchlistController(WatchlistService watchlistService,
                               MongoTemplate mongoTemplate) {
        this.watchlistService = watchlistService;
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/watchlist/list")
    public ResponseEntity<List<Watchlist>> getWatchlist() {
        return ResponseEntity.ok(watchlistService.list());
    }

    @GetMapping("/watchlist/tickers")
    public ResponseEntity<List<String>> getWatchlistTickers() {
        return ResponseEntity.ok(watchlistService.getTickers());
    }

    @GetMapping("/watchlist/stocks")
    public ResponseEntity<List<Stock>> getWatchlistStocks() {
        try {
            List<String> tickers = watchlistService.getTickers();

            if (tickers.isEmpty()) {
                return ResponseEntity.ok(new ArrayList<>());
            }

            // Fetch stocks for watchlist tickers using case-insensitive regex
            List<Stock> stocks = new ArrayList<>();
            List<String> optionList = Arrays.asList("TSLA", "NVDA", "AMZN", "SPY", "QQQ", "PLTR");
            for (String ticker : tickers) {
                Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                List<Stock> found = mongoTemplate.find(query, Stock.class);
                if (!found.isEmpty()) {
                    if(optionList.contains(ticker))
                        found.getFirst().setOptionPref("Y");
                    stocks.add(found.getFirst());
                } else {
                    log.warn("Stock not found for watchlist ticker: {}", ticker);
                }
            }

            log.info("Fetched {} stocks for {} watchlist tickers", stocks.size(), tickers.size());
            return ResponseEntity.ok(stocks);
        } catch (Exception e) {
            log.error("Error fetching watchlist stocks", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/watchlist/add")
    public ResponseEntity<?> addToWatchlist(@RequestBody Map<String, String> request) {
        try {
            String ticker = request.get("ticker");
            if (ticker == null || ticker.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Ticker is required");
            }

            Watchlist watchlist = watchlistService.add(ticker.trim().toUpperCase());
            return ResponseEntity.ok(watchlist);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error adding to watchlist", e);
            return ResponseEntity.internalServerError().body("Failed to add ticker to watchlist");
        }
    }

    @DeleteMapping("/watchlist/delete/{ticker}")
    public ResponseEntity<String> deleteFromWatchlist(@PathVariable String ticker) {
        try {
            watchlistService.delete(ticker);
            return ResponseEntity.ok("Ticker removed from watchlist");
        } catch (Exception e) {
            log.error("Error removing from watchlist", e);
            return ResponseEntity.internalServerError().body("Failed to remove ticker from watchlist");
        }
    }

    @GetMapping("/watchlist/exists/{ticker}")
    public ResponseEntity<Boolean> existsInWatchlist(@PathVariable String ticker) {
        return ResponseEntity.ok(watchlistService.exists(ticker));
    }
}


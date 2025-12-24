package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Picks;
import org.myswan.model.collection.Stock;
import org.myswan.repository.PicksRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PicksService {

    private final PicksRepository picksRepository;
    private final MongoTemplate mongoTemplate;

    public PicksService(PicksRepository picksRepository, MongoTemplate mongoTemplate) {
        this.picksRepository = picksRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Picks> list() {
        return picksRepository.findAll();
    }

    public List<Picks> listByTicker(String ticker) {
        return picksRepository.findByTickerOrderByAddedDateDesc(ticker);
    }

    public Picks save(Picks pick) {
        if (pick == null) return null;

        if (pick.getAddedDate() == null) {
            pick.setAddedDate(LocalDate.now());
        }

        if (pick.getTicker() != null && !pick.getTicker().isBlank()) {
            try {
                Query query = new Query(Criteria.where("ticker").regex("^" + pick.getTicker() + "$", "i"));
                Stock stock = mongoTemplate.findOne(query, Stock.class);
                if (stock != null) {
                    pick.setStock(stock);
                }
            } catch (Exception e) {
                log.warn("Failed to attach stock snapshot for {}: {}", pick.getTicker(), e.getMessage());
            }
        }

        return picksRepository.save(pick);
    }

    public Picks update(Picks pick) {
        if (pick == null) return null;

        if (pick.getAddedDate() == null) {
            pick.setAddedDate(LocalDate.now());
        }

        if (pick.getTicker() != null && !pick.getTicker().isBlank()) {
            try {
                Query query = new Query(Criteria.where("ticker").regex("^" + pick.getTicker() + "$", "i"));
                Stock stock = mongoTemplate.findOne(query, Stock.class);
                if (stock != null) {
                    pick.setStock(stock);
                }
            } catch (Exception e) {
                log.warn("Failed to refresh stock snapshot for {}: {}", pick.getTicker(), e.getMessage());
            }
        }
        return picksRepository.save(pick);
    }

    public void delete(String id) {
        picksRepository.deleteById(id);
    }

    public Picks findById(String id) {
        return picksRepository.findById(id).orElse(null);
    }

    public void syncWithStockData(List<Stock> allStocks) {
        log.info("Starting picks sync with current stock data...");
        try {
            List<Picks> allPicks = list();
            List<Picks> latestHistoryPicks = getLatestPicksHistoryForAllTickers();

            // Create a map for O(1) lookup of historical picks by ticker
            Map<String, Picks> historyPicksMap = new HashMap<>();
            for (Picks histPick : latestHistoryPicks) {
                historyPicksMap.put(histPick.getTicker().toUpperCase(), histPick);
            }

            // Create a map for O(1) lookup of stocks by ticker
            Map<String, Stock> stocksMap = new HashMap<>();
            for (Stock stock : allStocks) {
                stocksMap.put(stock.getTicker().toUpperCase(), stock);
            }

            for (Picks pick : allPicks) {
                try {
                    pick.setHistoryDate(LocalDate.now().toString());

                    // Find matching stock
                    Stock stock = stocksMap.get(pick.getTicker().toUpperCase());
                    if (stock != null) {
                        pick.setStock(stock);

                        // Initialize min/max if not set (first time)
                        if (pick.getMin() == 0) {
                            pick.setMin(stock.getLow());
                        }
                        if (pick.getMax() == 0) {
                            pick.setMax(stock.getHigh());
                        }

                        // Update min/max with today's values
                        pick.setMin(Math.min(pick.getMin(), stock.getLow()));
                        pick.setMax(Math.max(pick.getMax(), stock.getHigh()));

                        // Update min/max with historical values
                        Picks histPick = historyPicksMap.get(pick.getTicker().toUpperCase());
                        if (histPick != null) {
                            if (histPick.getMin() != 0) {
                                pick.setMin(Math.min(pick.getMin(), histPick.getMin()));
                            }
                            if (histPick.getMax() != 0) {
                                pick.setMax(Math.max(pick.getMax(), histPick.getMax()));
                            }
                        }
                    }
                    if (pick.getTicker() == null || "CLOSED".equalsIgnoreCase(pick.getStatus())) continue;

                    if (pick.getStock() != null) {

                        // If pick has target/stop values (non-zero), check hits
                        try {
                            double price = pick.getStock().getPrice();
                            if (pick.getTarget() != 0 && price >= pick.getTarget() && !pick.isTargetMet()) {
                                pick.setTargetMet(true);
                                pick.setTargetMetDate(LocalDate.now());
                                pick.setStatus("CLOSED");
                                log.info("Target hit for {}: {} >= {}", pick.getTicker(), price, pick.getTarget());
                            }

                            if (pick.getStopLoss() != 0 && price <= pick.getStopLoss() && !pick.isStopLossMet()) {
                                pick.setStopLossMet(true);
                                pick.setStopLossMetDate(LocalDate.now());
                                pick.setStatus("CLOSED");
                                log.info("Stop loss hit for {}: {} <= {}", pick.getTicker(), price, pick.getStopLoss());
                            }
                        } catch (Exception ex) {
                            log.warn("Error while evaluating target/stop for {}: {}", pick.getTicker(), ex.getMessage());
                        }
                    } else {
                        log.warn("Stock not found for pick ticker: {}", pick.getTicker());
                    }
                } catch (Exception e) {
                    log.error("Error syncing pick for ticker: {}", pick.getTicker(), e);
                }
            }
            if (!allPicks.isEmpty())
                picksRepository.saveAll(allPicks);
            log.info("Picks sync completed. Updated {} picks", allPicks.size());
        } catch (Exception e) {
            log.error("Error during picks sync: {}", e.getMessage(), e);
        }
    }

    public void syncPicksHistory() {
        log.info("Syncing picks to history...");
        List<Picks> picks = list();
        if(picks != null && !picks.isEmpty()) {
            deleteHistoryByDate(picks.getFirst().getHistoryDate());
            picks.forEach(stock -> {
                stock.setId(null);
            });
            mongoTemplate.insert(picks, "picksHistory");
        }
        log.info("Synced {} picks to history", picks!= null ? picks.size() : 0);
    }

    public void deleteHistoryByDate(String histDate) {
        Query query = Query.query(Criteria.where("historyDate").is(histDate));
        mongoTemplate.remove(query, "picksHistory");
    }

    public List<Picks> getPicksHistory(String ticker) {
        Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"))
                .with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "historyDate"));
        return mongoTemplate.find(query, Picks.class, "picksHistory");
    }

    public void deleteHistoryByDate(LocalDate date) {
        Query query = new Query(Criteria.where("addedDate").is(date));
        mongoTemplate.remove(query, "picksHistory");
        log.info("Deleted picks history for date: {}", date);
    }

    public List<Picks> getLatestPicksHistoryForAllTickers() {
        log.info("Fetching latest picks history for all tickers using aggregation...");

        try {
            // MongoDB aggregation pipeline optimized for performance:
            // 1. Sort by historyDate DESC (YYYY-MM-DD format sorts correctly as strings)
            //    Uses index on historyDate for fast sorting
            // 2. Group by ticker and take the first (latest) document using $first with $$ROOT
            //    This preserves the entire document structure
            // 3. Replace root with the full document to return proper Picks objects
            Aggregation aggregation = Aggregation.newAggregation(
                    Aggregation.sort(Sort.Direction.DESC, "historyDate"),
                    Aggregation.group("ticker")
                            .first("$$ROOT").as("latestPick"),
                    Aggregation.replaceRoot("latestPick")
            );

            List<Picks> latestPicks = mongoTemplate.aggregate(
                    aggregation,
                    "picksHistory",
                    Picks.class
            ).getMappedResults();

            log.info("Found {} latest picks from history (optimized query)", latestPicks.size());
            return latestPicks;
        } catch (Exception e) {
            log.error("Error fetching latest picks history: {}", e.getMessage(), e);
            return List.of();
        }
    }
}

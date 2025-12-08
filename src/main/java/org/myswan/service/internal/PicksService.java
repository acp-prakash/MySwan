package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.myswan.model.Picks;
import org.myswan.model.Stock;
import org.myswan.repository.PicksRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

        Picks saved = picksRepository.save(pick);

        // write history for this saved pick
        writeHistoryForPick(saved);

        return saved;
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

        Picks saved = picksRepository.save(pick);

        // write history for this updated pick
        writeHistoryForPick(saved);

        return saved;
    }

    public void delete(String id) {
        picksRepository.deleteById(id);
    }

    public Picks findById(String id) {
        return picksRepository.findById(id).orElse(null);
    }

    /**
     * Refresh picks for the provided tickers: attach latest Stock snapshot to pick.stock and
     * write/update picksHistory entries for today.
     */
    public void refreshPicksForTickers(Collection<String> tickers) {
        if (tickers == null || tickers.isEmpty()) return;

        for (String ticker : tickers) {
            if (ticker == null || ticker.isBlank()) continue;
            try {
                List<Picks> picks = picksRepository.findByTickerOrderByAddedDateDesc(ticker);
                if (picks == null || picks.isEmpty()) continue;

                // fetch latest stock once
                Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                Stock stock = mongoTemplate.findOne(query, Stock.class);

                for (Picks pick : picks) {
                    try {
                        if (stock != null) pick.setStock(stock);
                        Picks saved = picksRepository.save(pick);
                        writeHistoryForPick(saved);
                    } catch (Exception e) {
                        log.warn("Failed to refresh pick {} for ticker {}: {}", pick.getId(), ticker, e.getMessage());
                    }
                }

            } catch (Exception e) {
                log.error("Failed to refresh picks for ticker {}: {}", ticker, e.getMessage());
            }
        }
    }

    /**
     * Write a picksHistory entry for the provided pick: remove existing same-day entry and insert latest snapshot.
     */
    private void writeHistoryForPick(Picks saved) {
        if (saved == null || saved.getTicker() == null) return;
        try {
            Document doc = new Document();
            MappingMongoConverter converter = (MappingMongoConverter) mongoTemplate.getConverter();
            converter.write(saved, doc);
            doc.remove("_id");
            String histDate = LocalDate.now().toString();
            doc.put("historyDate", histDate);

            // Remove existing entry for this ticker and date
            Query removeQuery = new Query(Criteria.where("ticker").regex("^" + saved.getTicker() + "$", "i").and("historyDate").is(histDate));
            mongoTemplate.remove(removeQuery, "picksHistory");

            mongoTemplate.getCollection("picksHistory").insertOne(doc);
        } catch (Exception e) {
            log.warn("Failed to write picksHistory for {}: {}", saved.getTicker(), e.getMessage());
        }
    }

    public void syncWithStockData() {
        log.info("Starting picks sync with current stock data...");

        List<Picks> allPicks = picksRepository.findAll();
        int updated = 0;

        for (Picks pick : allPicks) {
            try {
                if (pick.getTicker() == null) continue;

                Query query = new Query(Criteria.where("ticker").regex("^" + pick.getTicker() + "$", "i"));
                Stock stock = mongoTemplate.findOne(query, Stock.class);

                if (stock != null) {
                    // attach latest stock snapshot
                    pick.setStock(stock);

                    // If pick has target/stop values (non-zero), check hits
                    try {
                        double price = stock.getPrice();
                        if (pick.getTarget() != 0 && price >= pick.getTarget() && !pick.isTargetMet()) {
                            pick.setTargetMet(true);
                            pick.setTargetMetDate(LocalDate.now());
                            log.info("Target hit for {}: {} >= {}", pick.getTicker(), price, pick.getTarget());
                        }

                        if (pick.getStopLoss() != 0 && price <= pick.getStopLoss() && !pick.isStopLossMet()) {
                            pick.setStopLossMet(true);
                            pick.setStopLossMetDate(LocalDate.now());
                            log.info("Stop loss hit for {}: {} <= {}", pick.getTicker(), price, pick.getStopLoss());
                        }
                    } catch (Exception ex) {
                        log.warn("Error while evaluating target/stop for {}: {}", pick.getTicker(), ex.getMessage());
                    }

                    picksRepository.save(pick);
                    writeHistoryForPick(pick);
                    updated++;
                } else {
                    log.warn("Stock not found for pick ticker: {}", pick.getTicker());
                }
            } catch (Exception e) {
                log.error("Error syncing pick for ticker: {}", pick.getTicker(), e);
            }
        }

        log.info("Picks sync completed. Updated {} picks", updated);
    }

    public void syncPicksHistory() {
        log.info("Syncing picks to history...");

        List<Picks> allPicks = picksRepository.findAll();

        if (allPicks.isEmpty()) {
            log.info("No picks to sync to history");
            return;
        }

        allPicks.forEach(pick -> {
            Picks historyCopy = copyPick(pick);
            historyCopy.setId(null); // Create new document in history
            mongoTemplate.insert(historyCopy, "picksHistory");
        });

        log.info("Synced {} picks to history", allPicks.size());
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

    private Picks copyPick(Picks original) {
        Picks copy = new Picks();
        copy.setTicker(original.getTicker());
        copy.setReason(original.getReason());
        copy.setAddedDate(original.getAddedDate());
        copy.setAddedPrice(original.getAddedPrice());
        copy.setEntry(original.getEntry());
        copy.setTarget(original.getTarget());
        copy.setStopLoss(original.getStopLoss());
        copy.setTargetDate(original.getTargetDate());
        copy.setTargetMetDate(original.getTargetMetDate());
        copy.setStopLossMetDate(original.getStopLossMetDate());
        copy.setTargetMet(original.isTargetMet());
        copy.setStopLossMet(original.isStopLossMet());
        copy.setStock(original.getStock());
        return copy;
    }
}

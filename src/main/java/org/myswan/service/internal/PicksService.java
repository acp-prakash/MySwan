package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Picks;
import org.myswan.model.collection.Stock;
import org.myswan.repository.PicksRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
        List<Picks> allPicks = list();
        for (Picks pick : allPicks) {
            try {
                pick.setHistoryDate(LocalDate.now().toString());
                for (Stock stock : allStocks) {
                    if (stock.getTicker().equalsIgnoreCase(pick.getTicker())) {
                        pick.setStock(stock);
                        break;
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
        if(!allPicks.isEmpty())
            picksRepository.saveAll(allPicks);
        log.info("Picks sync completed. Updated {} picks", allPicks.size());
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
}

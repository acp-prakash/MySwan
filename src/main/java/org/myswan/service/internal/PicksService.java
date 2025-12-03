package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.Picks;
import org.myswan.model.Stock;
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
        return picksRepository.save(pick);
    }

    public Picks update(Picks pick) {
        return picksRepository.save(pick);
    }

    public void delete(String id) {
        picksRepository.deleteById(id);
    }

    public Picks findById(String id) {
        return picksRepository.findById(id).orElse(null);
    }

    /**
     * Sync all picks with current stock data (price, change, pattern counts)
     */
    public void syncWithStockData() {
        log.info("Starting picks sync with current stock data...");

        List<Picks> allPicks = picksRepository.findAll();
        int updated = 0;

        for (Picks pick : allPicks) {
            try {
                // Find current stock data using case-insensitive query
                Query query = new Query(Criteria.where("ticker").regex("^" + pick.getTicker() + "$", "i"));
                List<Stock> stocks = mongoTemplate.find(query, Stock.class);

                if (!stocks.isEmpty()) {
                    Stock stock = stocks.get(0);

                    // Update current price and change
                    pick.setCurrentPrice(stock.getPrice());
                    pick.setCurrentChange(stock.getChange());

                    // Update current pattern counts
                    pick.setCurrentNoOfLongPatterns(stock.getNoOfLongPatterns());
                    pick.setCurrentNoOfShortPatterns(stock.getNoOfShortPatterns());

                    // Auto-check if target or stop hit
                    if (!pick.isTargetMet() && stock.getPrice() >= pick.getTarget()) {
                        pick.setTargetMet(true);
                        pick.setTargetMetDate(LocalDate.now());
                        log.info("Target hit for {}: Price {} >= Target {}", pick.getTicker(), stock.getPrice(), pick.getTarget());
                    }

                    if (!pick.isStopLossMet() && stock.getPrice() <= pick.getStopLoss()) {
                        pick.setStopLossMet(true);
                        pick.setStopLossMetDate(LocalDate.now());
                        log.info("Stop loss hit for {}: Price {} <= Stop {}", pick.getTicker(), stock.getPrice(), pick.getStopLoss());
                    }

                    picksRepository.save(pick);
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

    /**
     * Sync picks to history collection (snapshot of current state)
     */
    public void syncPicksHistory() {
        log.info("Syncing picks to history...");

        List<Picks> allPicks = picksRepository.findAll();

        if (allPicks.isEmpty()) {
            log.info("No picks to sync to history");
            return;
        }

        // Copy each pick to history (with null ID to create new document)
        allPicks.forEach(pick -> {
            Picks historyCopy = copyPick(pick);
            historyCopy.setId(null); // Create new document in history
            mongoTemplate.insert(historyCopy, "picksHistory");
        });

        log.info("Synced {} picks to history", allPicks.size());
    }

    /**
     * Get picks history for a specific ticker
     */
    public List<Picks> getPicksHistory(String ticker) {
        Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"))
                .with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "addedDate"));
        return mongoTemplate.find(query, Picks.class, "picksHistory");
    }

    /**
     * Delete picks history for a specific date
     */
    public void deleteHistoryByDate(LocalDate date) {
        Query query = new Query(Criteria.where("addedDate").is(date));
        mongoTemplate.remove(query, "picksHistory");
        log.info("Deleted picks history for date: {}", date);
    }

    /**
     * Helper method to create a copy of a pick
     */
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
        copy.setCurrentPrice(original.getCurrentPrice());
        copy.setCurrentChange(original.getCurrentChange());
        copy.setCurrentNoOfLongPatterns(original.getCurrentNoOfLongPatterns());
        copy.setCurrentNoOfShortPatterns(original.getCurrentNoOfShortPatterns());
        copy.setNoOfLongPatterns(original.getNoOfLongPatterns());
        copy.setNoOfShortPatterns(original.getNoOfShortPatterns());
        copy.setOverAllScore(original.getOverAllScore());
        copy.setBottomScore(original.getBottomScore());
        copy.setReversalScore(original.getReversalScore());
        copy.setBreakoutScore(original.getBreakoutScore());
        copy.setPatternScore(original.getPatternScore());
        copy.setSpikeScore(original.getSpikeScore());
        copy.setSignal(original.getSignal());
        copy.setBtShortRating(original.getBtShortRating());
        copy.setBtLongRating(original.getBtLongRating());
        copy.setBtRating(original.getBtRating());
        copy.setBtTrend(original.getBtTrend());
        copy.setTradingViewTechRating(original.getTradingViewTechRating());
        copy.setTradingViewMARating(original.getTradingViewMARating());
        copy.setTradingViewOSRating(original.getTradingViewOSRating());
        return copy;
    }
}


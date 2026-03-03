package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Master;
import org.myswan.model.collection.Stock;
import org.myswan.repository.MasterRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class MasterService {
    private final MasterRepository repository;
    private final MongoTemplate mongoTemplate;

    public MasterService(MasterRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<Master> getByTicker(String ticker) {
        try {
            // Primary: try repository lookup by id (mapped @Id -> _id)
            Optional<Master> byId = repository.findById(ticker);
            if (byId.isPresent()) return byId;
        } catch (Exception ignored) {
            // fall through to raw queries
        }

        return Optional.empty();
    }

    public Master create(Master master) {
        if (master.getAddedDate() == null)
            master.setAddedDate(LocalDate.now());
        return repository.save(master);
    }

    public Master update(String ticker, Master master) {
        master.setTicker(ticker);
        return repository.save(master);
    }

    public void delete(String ticker) {
        repository.deleteById(ticker);
    }

    public boolean exists(String ticker) {
        return repository.existsById(ticker);
    }

    public List<Master> list() {
        // Try standard repository read first
        try {
            return repository.findAll();
        } catch (Exception ignored) {
            // fall through to raw DB read
        }
        return new ArrayList<>();
    }

    /**
     * Get all masters with current price populated from stock collection
     */
    public List<Master> listWithCurrentPrice() {
        List<Master> masters = list();

        // Fetch all stocks from stock collection
        List<Stock> stocks = mongoTemplate.findAll(Stock.class, "stock");

        // Create a map for O(1) lookup
        java.util.Map<String, Double> stockPriceMap = new java.util.HashMap<>();
        for (Stock stock : stocks) {
            if (stock.getTicker() != null) {
                stockPriceMap.put(stock.getTicker().toUpperCase(), stock.getPrice());
            }
        }

        // Populate current price for each master
        for (Master master : masters) {
            if (master.getTicker() != null) {
                Double currentPrice = stockPriceMap.get(master.getTicker().toUpperCase());
                master.setCurrentPrice(currentPrice);
            }
        }

        log.debug("Populated current price for {} masters", masters.size());
        return masters;
    }

    /**
     * Delete a ticker from all collections: master, stock, stockHistory, picks, picksHistory, pattern, patternHistory, watchlist, guaranteedPicks
     */
    public void deleteFromAllCollections(String ticker) {
        log.info("Starting deletion of ticker {} from all collections", ticker);

        try {
            // 1. Delete from master collection
            repository.deleteById(ticker);
            log.info("Deleted {} from master collection", ticker);

            // 2. Delete from stock collection
            Query stockQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            long stockDeleted = mongoTemplate.remove(stockQuery, "stock").getDeletedCount();
            log.info("Deleted {} records from stock collection", stockDeleted);

            // 3. Delete from stockHistory collection
            Query stockHistoryQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            long stockHistoryDeleted = mongoTemplate.remove(stockHistoryQuery, "stockHistory").getDeletedCount();
            log.info("Deleted {} records from stockHistory collection", stockHistoryDeleted);

            // 4. Delete from picks collection
            Query picksQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            long picksDeleted = mongoTemplate.remove(picksQuery, "picks").getDeletedCount();
            log.info("Deleted {} records from picks collection", picksDeleted);

            // 5. Delete from picksHistory collection
            Query picksHistoryQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            long picksHistoryDeleted = mongoTemplate.remove(picksHistoryQuery, "picksHistory").getDeletedCount();
            log.info("Deleted {} records from picksHistory collection", picksHistoryDeleted);

            // 6. Delete from pattern collection
            Query patternQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            long patternDeleted = mongoTemplate.remove(patternQuery, "pattern").getDeletedCount();
            log.info("Deleted {} records from pattern collection", patternDeleted);

            // 7. Delete from patternHistory collection
            Query patternHistoryQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            long patternHistoryDeleted = mongoTemplate.remove(patternHistoryQuery, "patternHistory").getDeletedCount();
            log.info("Deleted {} records from patternHistory collection", patternHistoryDeleted);

            // 8. Delete from watchlist collection
            Query watchlistQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            long watchlistDeleted = mongoTemplate.remove(watchlistQuery, "watchlist").getDeletedCount();
            log.info("Deleted {} records from watchlist collection", watchlistDeleted);

            // 9. Delete from guaranteedPicks collection
            Query guaranteedPicksQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            long guaranteedPicksDeleted = mongoTemplate.remove(guaranteedPicksQuery, "guaranteedPicks").getDeletedCount();
            log.info("Deleted {} records from guaranteedPicks collection", guaranteedPicksDeleted);

            // 10. Delete from options collection
            Query optionsQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            long optionsDeleted = mongoTemplate.remove(optionsQuery, "options").getDeletedCount();
            log.info("Deleted {} records from options collection", optionsDeleted);

            // 11. Delete from optionsHistory collection
            Query optionsHistoryQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
            long optionsHistoryDeleted = mongoTemplate.remove(optionsHistoryQuery, "optionsHistory").getDeletedCount();
            log.info("Deleted {} records from optionsHistory collection", optionsHistoryDeleted);

            log.info("Successfully deleted ticker {} from all collections", ticker);
        } catch (Exception e) {
            log.error("Error deleting ticker {} from collections: {}", ticker, e.getMessage(), e);
            throw new RuntimeException("Failed to delete ticker from all collections: " + e.getMessage());
        }
    }

    /**
     * Delete a ticker from specific collections
     * @param ticker The ticker symbol to delete
     * @param collections List of collection names to delete from (e.g., ["master", "stock", "stockHistory"])
     */
    public void deleteFromSpecificCollections(String ticker, List<String> collections) {
        log.info("Starting deletion of ticker {} from {} specific collections", ticker, collections.size());

        try {
            int deletedFrom = 0;

            for (String collection : collections) {
                switch (collection.toLowerCase()) {
                    case "master":
                    case "masters":
                        repository.deleteById(ticker);
                        log.info("Deleted {} from master collection", ticker);
                        deletedFrom++;
                        break;

                    case "stock":
                    case "stocks":
                        Query stockQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                        long stockDeleted = mongoTemplate.remove(stockQuery, "stock").getDeletedCount();
                        log.info("Deleted {} records from stock collection", stockDeleted);
                        deletedFrom++;
                        break;

                    case "stockhistory":
                        Query stockHistoryQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                        long stockHistoryDeleted = mongoTemplate.remove(stockHistoryQuery, "stockHistory").getDeletedCount();
                        log.info("Deleted {} records from stockHistory collection", stockHistoryDeleted);
                        deletedFrom++;
                        break;

                    case "picks":
                        Query picksQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                        long picksDeleted = mongoTemplate.remove(picksQuery, "picks").getDeletedCount();
                        log.info("Deleted {} records from picks collection", picksDeleted);
                        deletedFrom++;
                        break;

                    case "pickshistory":
                        Query picksHistoryQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                        long picksHistoryDeleted = mongoTemplate.remove(picksHistoryQuery, "picksHistory").getDeletedCount();
                        log.info("Deleted {} records from picksHistory collection", picksHistoryDeleted);
                        deletedFrom++;
                        break;

                    case "options":
                        Query optionsQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                        long optionsDeleted = mongoTemplate.remove(optionsQuery, "options").getDeletedCount();
                        log.info("Deleted {} records from options collection", optionsDeleted);
                        deletedFrom++;
                        break;

                    case "optionshistory":
                        Query optionsHistoryQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                        long optionsHistoryDeleted = mongoTemplate.remove(optionsHistoryQuery, "optionsHistory").getDeletedCount();
                        log.info("Deleted {} records from optionsHistory collection", optionsHistoryDeleted);
                        deletedFrom++;
                        break;

                    case "pattern":
                        Query patternQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                        long patternDeleted = mongoTemplate.remove(patternQuery, "pattern").getDeletedCount();
                        log.info("Deleted {} records from pattern collection", patternDeleted);
                        deletedFrom++;
                        break;

                    case "patternhistory":
                        Query patternHistoryQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                        long patternHistoryDeleted = mongoTemplate.remove(patternHistoryQuery, "patternHistory").getDeletedCount();
                        log.info("Deleted {} records from patternHistory collection", patternHistoryDeleted);
                        deletedFrom++;
                        break;

                    case "watchlist":
                        Query watchlistQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                        long watchlistDeleted = mongoTemplate.remove(watchlistQuery, "watchlist").getDeletedCount();
                        log.info("Deleted {} records from watchlist collection", watchlistDeleted);
                        deletedFrom++;
                        break;

                    case "guaranteedpicks":
                        Query guaranteedPicksQuery = Query.query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
                        long guaranteedPicksDeleted = mongoTemplate.remove(guaranteedPicksQuery, "guaranteedPicks").getDeletedCount();
                        log.info("Deleted {} records from guaranteedPicks collection", guaranteedPicksDeleted);
                        deletedFrom++;
                        break;

                    default:
                        log.warn("Unknown collection: {}", collection);
                        break;
                }
            }

            log.info("Successfully deleted ticker {} from {} collections", ticker, deletedFrom);
        } catch (Exception e) {
            log.error("Error deleting ticker {} from specific collections: {}", ticker, e.getMessage(), e);
            throw new RuntimeException("Failed to delete ticker from specific collections: " + e.getMessage());
        }
    }
}

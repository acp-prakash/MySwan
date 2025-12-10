package org.myswan.service.internal;

import org.myswan.model.collection.Futures;
import org.myswan.model.collection.Stock;
import org.myswan.repository.FuturesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FuturesService {

    private static final Logger log = LoggerFactory.getLogger(FuturesService.class);
    private final FuturesRepository repository;
    private final MongoTemplate mongoTemplate;

    public FuturesService(FuturesRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Futures> getFuturesHistory(String ticker, LocalDate from, LocalDate to) {
        Query query = Query.query(Criteria.where("ticker").is(ticker));
        if (from != null) query.addCriteria(Criteria.where("histDate").gte(from));
        if (to != null) query.addCriteria(Criteria.where("histDate").lte(to));
        return mongoTemplate.find(query, Futures.class, "futuresHistory");
    }

    public List<Futures> list() {
        try {
            List<Futures> all = repository.findAll();
            if (all != null && !all.isEmpty()) return all;
        } catch (Exception ignored) {
            // fall through to empty result
        }
        return new ArrayList<>();
    }

    /**
     * Bulk delete existing futures by tickers and insert new ones
     */
    public void bulkReplaceByTickers(List<Futures> futuresList) {
        if (futuresList == null || futuresList.isEmpty()) {
            log.warn("No futures to insert");
            return;
        }

        // Extract all tickers to delete
        List<String> tickers = futuresList.stream()
                .map(Futures::getTicker)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // Delete existing futures with these tickers
        if (!tickers.isEmpty()) {
            Query deleteQuery = Query.query(Criteria.where("ticker").in(tickers));
            long deletedCount = mongoTemplate.remove(deleteQuery, Futures.class).getDeletedCount();
            log.info("Deleted {} existing futures records", deletedCount);
        }

        // Insert all new futures
        try {
            mongoTemplate.insert(futuresList, Futures.class);
            log.info("Inserted {} new futures records", futuresList.size());
        } catch (Exception e) {
            log.error("Failed to bulk insert futures", e);
        }
    }

    public void syncFuturesHistory() {
        List<Futures> futuresList = list();
        if(futuresList != null && !futuresList.isEmpty()) {
            deleteHistoryByDate(futuresList.getFirst().getHistDate());
            futuresList.forEach(future -> {
                future.setId(null);
            });
            mongoTemplate.insert(futuresList, "futuresHistory");
        }
    }

    public void deleteHistoryByDate(LocalDate histDate) {
        Query query = Query.query(Criteria.where("histDate").is(histDate));
        mongoTemplate.remove(query, "futuresHistory");
    }

    /*public Optional<Futures> getByTicker(String ticker) {
        try {
            return repository.findById(ticker);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Futures create(Futures futures) {
        Futures saved = repository.save(futures);
        Futures history = new Futures();
        org.springframework.beans.BeanUtils.copyProperties(saved, history);
        history.setId(null);
        mongoTemplate.insert(history, "futuresHistory");
        return saved;
    }

    public Futures update(String ticker, Futures futures) {
        futures.setTicker(ticker);
        return repository.save(futures);
    }

    public void delete(String ticker) {
        repository.deleteById(ticker);
        deleteHistoryByTicker(ticker);
    }

    public boolean exists(String ticker) {
        return repository.existsById(ticker);
    }

    public void deleteHistoryByTicker(String ticker) {
        Query query = Query.query(Criteria.where("ticker").is(ticker));
        mongoTemplate.remove(query, "futuresHistory");
    }

    public void deleteHistoryByDate(LocalDate histDate) {
        Query query = Query.query(Criteria.where("histDate").is(histDate));
        mongoTemplate.remove(query, "futuresHistory");
    }

    public void deleteHistoryByTickerAndDate(String ticker, LocalDate histDate) {
        Query query = Query.query(Criteria.where("ticker").is(ticker).and("histDate").is(histDate));
        mongoTemplate.remove(query, "futuresHistory");
    }

    public List<Futures> getHistoryByDate(LocalDate histDate) {
        Query query = Query.query(Criteria.where("histDate").is(histDate));
        return mongoTemplate.find(query, Futures.class, "futuresHistory");
    }

    public void bulkPatch(List<Futures> futuresList, Set<String> fields) {
        if (futuresList == null || futuresList.isEmpty()) return;

        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Futures.class);
        for (Futures futures : futuresList) {
            if (futures.getTicker() == null) continue;

            Query query = Query.query(Criteria.where("_id").is(futures.getTicker()));
            Update update = new Update();
            if (fields.contains("id")) update.set("id", futures.getId());
            if (fields.contains("ticker")) update.set("ticker", futures.getTicker());
            if (fields.contains("type")) update.set("type", futures.getType());
            if (fields.contains("histDate")) update.set("histDate", futures.getHistDate());
            if (fields.contains("rating")) update.set("rating", futures.getRating());
            if (fields.contains("price")) update.set("price", futures.getPrice());
            if (fields.contains("open")) update.set("open", futures.getOpen());
            if (fields.contains("high")) update.set("high", futures.getHigh());
            if (fields.contains("low")) update.set("low", futures.getLow());
            if (fields.contains("change")) update.set("change", futures.getChange());
            if (fields.contains("volume")) update.set("volume", futures.getVolume());
            if (fields.contains("openInterest")) update.set("openInterest", futures.getOpenInterest());
            if (fields.contains("expiryDate")) update.set("expiryDate", futures.getExpiryDate());
            if (fields.contains("expiryDays")) update.set("expiryDays", futures.getExpiryDays());
            if (fields.contains("prevClose")) update.set("prevClose", futures.getPrevClose());
            if (fields.contains("upDays")) update.set("upDays", futures.getUpDays());
            if (fields.contains("downDays")) update.set("downDays", futures.getDownDays());
            if (fields.contains("downLow")) update.set("downLow", futures.getDownLow());
            if (fields.contains("upHigh")) update.set("upHigh", futures.getUpHigh());

            bulkOps.upsert(query, update);
        }

        try {
            bulkOps.execute();
            log.info("Bulk patched {} futures records", futuresList.size());
        } catch (Exception e) {
            log.error("Failed to bulk patch futures", e);
        }

        syncFuturesHistory();
    }*/
}
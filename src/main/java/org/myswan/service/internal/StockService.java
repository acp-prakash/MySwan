package org.myswan.service.internal;

import org.myswan.model.Stock;
import org.myswan.repository.StockRepository;
import org.myswan.service.external.vo.TradingViewVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    private final StockRepository repository;
    private final MongoTemplate mongoTemplate;

    public StockService(StockRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<Stock> getByTicker(String ticker) {
        try {
            return repository.findById(ticker);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Stock create(Stock stock) {
        Stock s = repository.save(stock);
        Stock history = new Stock();
        BeanUtils.copyProperties(s, history);
        history.setId(null);
        mongoTemplate.insert(history, "stockHistory");
        return s;
    }

    public Stock update(String ticker, Stock stock) {
        stock.setTicker(ticker);
        return repository.save(stock);

    }

    public void delete(String ticker) {
        repository.deleteById(ticker);
        deleteHistoryByTicker(ticker);
    }

    public boolean exists(String ticker) {
        return repository.existsById(ticker);
    }

    public List<Stock> list() {
        try {
            List<Stock> all = repository.findAll();
            if (all != null && !all.isEmpty()) return all;
        } catch (Exception ignored) {
            // fall through to empty result
        }

        // Return empty list if no stocks found in DB
        return new ArrayList<>();
    }

    public void deleteHistoryByDate(LocalDate histDate) {
        Query query = Query.query(Criteria.where("histDate").is(histDate));
        mongoTemplate.remove(query, "stockHistory");
    }

    public void deleteHistoryByTicker(String ticker) {
        Query query = Query.query(Criteria.where("ticker").is(ticker));
        mongoTemplate.remove(query, "stockHistory");
    }

    public void deleteHistoryByTickerAndHistDate(String ticker, LocalDate histDate) {
            Query query = Query.query(
                    Criteria.where("ticker").is(ticker)
                            .and("histDate").is(histDate)
            );
        mongoTemplate.remove(query, "stockHistory");
    }

    public void syncStockHistory() {
        List<Stock> stocks = list();
        if(stocks != null && !stocks.isEmpty()) {
            deleteHistoryByDate(stocks.get(0).getHistDate());
            stocks.forEach(stock -> {
                stock.setId(null);
            });
            mongoTemplate.insert(stocks, "stockHistory");
        }
    }
    public void bulkPatch(List<Stock> stocks, Set<String> fields) {
        if (stocks == null || stocks.isEmpty() || fields == null || fields.isEmpty()) return;

        BulkOperations ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Stock.class);

        for (Stock s : stocks) {
            if (s.getTicker() == null || s.getTicker().isBlank()) continue;

            Query query = Query.query(Criteria.where("_id").is(s.getTicker()));
            Update update = new Update();

            BeanWrapper bw = new BeanWrapperImpl(s);
            for (String field : fields) {
                Object val = bw.getPropertyValue(field);
                update.set(field, val);
            }

            ops.upsert(query, update);
        }

        ops.execute();

        syncStockHistory();
    }

    public void updateTradingView(List<TradingViewVO> tvList) {
        if (tvList == null || tvList.isEmpty()) {
            log.info("TradingView update skipped: empty list");
            return;
        }
        LocalDate today = LocalDate.now();
        List<Stock> toSave = new ArrayList<>(tvList.size());
        for (TradingViewVO vo : tvList) {
            if (vo.getTicker() == null || vo.getTicker().isBlank()) continue;
            // Try existing
            Stock existing = repository.findById(vo.getTicker()).orElse(null);
            if(existing == null || existing.getTicker() == null || existing.getTicker().isBlank())
                continue;
            existing.setPrice(vo.getPrice());
            existing.setType(vo.getType());
            existing.setOpen(vo.getOpen());
            existing.setHigh(vo.getHigh());
            existing.setLow(vo.getLow());
            existing.setChange(vo.getChange());
            existing.setSma9(vo.getSma9());
            existing.setSma20(vo.getSma20());
            existing.setSma21(vo.getSma20());
            existing.setSma50(vo.getSma50());
            existing.setSma100(vo.getSma100());
            existing.setSma200(vo.getSma200());
            existing.setEma9(vo.getEma9());
            existing.setEma20(vo.getEma20());
            existing.setEma21(vo.getEma21());
            existing.setEma50(vo.getEma50());
            existing.setEma100(vo.getEma100());
            existing.setEma200(vo.getEma200());
            existing.setMacd1226(vo.getMacd1226());
            existing.setRsi14(vo.getRsi14());
            existing.setAtr14(vo.getAtr14());
            existing.setMomentum(vo.getMomentum());
            existing.setVolume(vo.getVolume());
            existing.setVolumeChange(vo.getVolumeChange());
            existing.setAvgVolume10D(vo.getAvgVolume10D());
            existing.setVwap(vo.getVwap());
            existing.getRating().setTradingViewMARating(vo.getMaRating());
            existing.getRating().setTradingViewTechRating(vo.getTechRating());
            existing.getRating().setTradingViewAnalystsRating(vo.getAnalystsRating());
            toSave.add(existing);
        }
        repository.saveAll(toSave);
        syncStockHistory();
        log.info("TradingView update completed. Saved {} stocks", toSave.size());
    }

    public List<Stock> getStockHistory(String ticker, LocalDate from, LocalDate to) {
        if (ticker == null || ticker.isBlank()) return new ArrayList<>();
        Query query = Query.query(Criteria.where("ticker").is(ticker));
        if (from != null) {
            query.addCriteria(Criteria.where("histDate").gte(from));
        }
        if (to != null) {
            query.addCriteria(Criteria.where("histDate").lte(to));
        }
        query.with(Sort.by(Sort.Direction.DESC, "histDate"));
        try {
            return mongoTemplate.find(query, Stock.class, "stockHistory");
        } catch (Exception e) {
            log.warn("Failed to load history for {}: {}", ticker, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Stock> getHistoryByDate(LocalDate histDate) {
        if (histDate == null) return new ArrayList<>();
        Query query = Query.query(Criteria.where("histDate").is(histDate));
        query.with(Sort.by(Sort.Direction.ASC, "ticker"));
        try {
            return mongoTemplate.find(query, Stock.class, "stockHistory");
        } catch (Exception e) {
            log.warn("Failed to load history for date {}: {}", histDate, e.getMessage());
            return new ArrayList<>();
        }
    }
}

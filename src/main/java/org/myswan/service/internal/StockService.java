package org.myswan.service.internal;

import org.myswan.model.Stock;
import org.myswan.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

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
        return repository.save(stock);
    }

    public Stock update(String ticker, Stock stock) {
        stock.setTicker(ticker);
        return repository.save(stock);
    }

    public void delete(String ticker) {
        repository.deleteById(ticker);
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
    }
}

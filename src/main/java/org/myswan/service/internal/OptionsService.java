package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Options;
import org.myswan.model.collection.Stock;
import org.myswan.repository.OptionsRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class OptionsService {

    private final OptionsRepository optionsRepository;
    private final MongoTemplate mongoTemplate;

    public OptionsService(OptionsRepository optionsRepository, MongoTemplate mongoTemplate) {
        this.optionsRepository = optionsRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Options> list() {
        try {
            return optionsRepository.findAll();
        } catch (Exception ex) {
            log.error("Failed to load options", ex);
            return Collections.emptyList();
        }
    }

    public List<Options> listByTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return Collections.emptyList();
        }
        Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"))
                .with(Sort.by(Sort.Direction.DESC, "histDate"));
        return mongoTemplate.find(query, Options.class, "options");
    }

    public Options save(Options option) {
        if (option == null) return null;
        if (option.getHistDate() == null) option.setHistDate(LocalDate.now());
        return optionsRepository.save(option);
    }

    public Options update(Options option) {
        if (option == null || option.getId() == null) return null;
        if (option.getHistDate() == null) option.setHistDate(LocalDate.now());
        return optionsRepository.save(option);
    }

    public void delete(String id) {
        if (id != null) optionsRepository.deleteById(id);
    }

    public Options findById(String id) {
        if (id == null) return null;
        Optional<Options> option = optionsRepository.findById(id);
        return option.orElse(null);
    }


    public void syncWithStockData(List<Stock> allStocks) {
        List<Options> allOptions = list();
        if (allOptions.isEmpty()) return;

        Map<String, Stock> stockByTicker = allStocks.stream()
                .filter(s -> s.getTicker() != null && !s.getTicker().isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        s -> s.getTicker().toUpperCase(),
                        s -> s,
                        (existing, replacement) -> existing));

        allOptions.parallelStream().forEach(option -> {
            String ticker = option.getTicker() != null ? option.getTicker().toUpperCase() : "";
            Stock stock = stockByTicker.get(ticker);
            if (stock != null) {
                option.setStock(stock);
            }
        });

        optionsRepository.saveAll(allOptions);
        log.info("syncWithStockData: updated stock reference for {} options", allOptions.size());
    }

    /**
     * Called during Compute. Iterates all current options, calculates daysUpDown
     * from optionsHistory for each, and saves back.
     */
    public void calculateAllDaysUpDown() {
        List<Options> allOptions = list();
        if (allOptions.isEmpty()) return;
        LocalDate today = LocalDate.now();
        allOptions.forEach(opt -> opt.setDaysUpDown(calculateDaysUpDown(opt.getOptionId(), today)));
        optionsRepository.saveAll(allOptions);
        log.info("calculateAllDaysUpDown: updated {} options", allOptions.size());
    }

    /**
     * Counts consecutive up days (positive) or down days (negative) for the given optionId
     * from optionsHistory sorted by histDate DESC (most recent first).
     * Compares day[i].price vs day[i+1].price (newer vs older).
     * If newer > older → up day. If newer < older → down day.
     */
    public int calculateDaysUpDown(String optionId, LocalDate today) {
        if (optionId == null || optionId.isBlank()) return 0;

        Query q = new Query(Criteria.where("optionId").is(optionId))
                .with(Sort.by(Sort.Direction.DESC, "histDate"));
        List<Options> history = mongoTemplate.find(q, Options.class, "optionsHistory");

        if (history.size() < 2) return 0;

        int streak = 0;

        for (int i = 0; i < history.size() - 1; i++) {
            double newer = history.get(i).getPrice();
            double older  = history.get(i + 1).getPrice();

            if (newer > older) {
                // up day
                if (streak < 0) break; // direction changed
                streak++;
            } else if (newer < older) {
                // down day
                if (streak > 0) break; // direction changed
                streak--;
            } else {
                break; // flat — stop streak
            }
        }

        return streak;
    }

    public void syncOptionsHistory() {
        List<Options> options = list();
        if (options.isEmpty()) {
            log.info("Skipping options history sync: no records found");
            return;
        }
        LocalDate snapshotDate = LocalDate.now();
        deleteHistoryByDate(snapshotDate);
        List<Options> historyCopies = options.stream()
                .map(o -> copyForHistory(o, snapshotDate))
                .toList();
        mongoTemplate.insert(historyCopies, "optionsHistory");
        log.info("Synced {} options into optionsHistory for {}", historyCopies.size(), snapshotDate);
    }

    public void deleteHistoryByDate(LocalDate histDate) {
        if (histDate == null) return;
        Query query = Query.query(Criteria.where("histDate").is(histDate));
        mongoTemplate.remove(query, "optionsHistory");
    }

    public List<Options> getOptionsHistory(String ticker) {
        if (ticker == null || ticker.isBlank()) return Collections.emptyList();
        Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"))
                .with(Sort.by(Sort.Direction.DESC, "histDate"));
        return mongoTemplate.find(query, Options.class, "optionsHistory");
    }

    public List<Options> getOptionsHistoryByOptionId(String optionId) {
        if (optionId == null || optionId.isBlank()) return Collections.emptyList();
        Query query = new Query(Criteria.where("optionId").is(optionId))
                .with(Sort.by(Sort.Direction.DESC, "histDate"));
        return mongoTemplate.find(query, Options.class, "optionsHistory");
    }

    public void deleteHistoryByTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) return;
        Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
        mongoTemplate.remove(query, "optionsHistory");
    }

    private Options copyForHistory(Options source, LocalDate histDate) {
        Options copy = new Options();
        BeanUtils.copyProperties(source, copy);
        copy.setId(null);
        copy.setHistDate(histDate);
        return copy;
    }
}

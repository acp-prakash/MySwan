package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Options;
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
        if (option == null) {
            return null;
        }
        if (option.getHistDate() == null) {
            option.setHistDate(LocalDate.now());
        }
        return optionsRepository.save(option);
    }

    public Options update(Options option) {
        if (option == null || option.getId() == null) {
            return null;
        }
        if (option.getHistDate() == null) {
            option.setHistDate(LocalDate.now());
        }
        return optionsRepository.save(option);
    }

    public void delete(String id) {
        if (id == null) {
            return;
        }
        optionsRepository.deleteById(id);
    }

    public Options findById(String id) {
        if (id == null) {
            return null;
        }
        Optional<Options> option = optionsRepository.findById(id);
        return option.orElse(null);
    }

    public int refreshOptions(LocalDate histDate) {
        List<Options> options = list();
        if (options.isEmpty()) {
            return 0;
        }
        LocalDate effectiveDate = histDate != null ? histDate : LocalDate.now();
        boolean updated = false;
        for (Options option : options) {
            if (option.getHistDate() == null) {
                option.setHistDate(effectiveDate);
                updated = true;
            }
        }
        if (updated) {
            optionsRepository.saveAll(options);
        }
        return options.size();
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
                .map(option -> copyForHistory(option, snapshotDate))
                .toList();
        mongoTemplate.insert(historyCopies, "optionsHistory");
        log.info("Synced {} options into optionsHistory for {}", historyCopies.size(), snapshotDate);
    }

    public void deleteHistoryByDate(LocalDate histDate) {
        if (histDate == null) {
            return;
        }
        Query query = Query.query(Criteria.where("histDate").is(histDate));
        mongoTemplate.remove(query, "optionsHistory");
    }

    public List<Options> getOptionsHistory(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return Collections.emptyList();
        }
        Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"))
                .with(Sort.by(Sort.Direction.DESC, "histDate"));
        return mongoTemplate.find(query, Options.class, "optionsHistory");
    }

    public void deleteHistoryByTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return;
        }
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


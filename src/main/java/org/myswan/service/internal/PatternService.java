package org.myswan.service.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.Pattern;
import org.myswan.repository.PatternRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class PatternService {

    private final PatternRepository patternRepository;
    private final MongoTemplate mongoTemplate;

    public PatternService(PatternRepository patternRepository, MongoTemplate mongoTemplate) {
        this.patternRepository = patternRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Pattern> list() {
        return patternRepository.findAll();
    }

    public List<Pattern> listByTicker(String ticker) {
        // Use case-insensitive regex to handle ticker case mismatches
        Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
        List<Pattern> patterns = mongoTemplate.find(query, Pattern.class);
        log.debug("Found {} patterns for ticker: {}", patterns.size(), ticker);
        return patterns;
    }

    public List<Pattern> listHistoryByTicker(String ticker) {
        // Use case-insensitive regex to handle ticker case mismatches
        Query query = new Query(Criteria.where("ticker").regex("^" + ticker + "$", "i"));
        query.with(org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "histDate"));
        List<Pattern> patterns = mongoTemplate.find(query, Pattern.class, "patternHistory");
        log.debug("Found {} pattern history records for ticker: {}", patterns.size(), ticker);
        return patterns;
    }

    public void deleteAll() {
        patternRepository.deleteAll();
        log.info("Deleted all patterns");
    }

    public void saveAll(List<Pattern> patterns) {
        if (patterns != null && !patterns.isEmpty()) {
            patternRepository.saveAll(patterns);
            log.info("Saved {} patterns", patterns.size());
        }
    }

    public void deleteHistoryByDate(String histDate) {
        Query query = new Query(Criteria.where("histDate").is(histDate));
        mongoTemplate.remove(query, "patternHistory");
        log.info("Deleted pattern history for date: {}", histDate);
    }

    public void saveToHistory(List<Pattern> patterns) {
        if (patterns != null && !patterns.isEmpty()) {
            patterns.forEach(pattern -> {
                pattern.setId(null);
            });
            mongoTemplate.insert(patterns, "patternHistory");
            log.info("Saved {} patterns to history", patterns.size());
        }
    }
}


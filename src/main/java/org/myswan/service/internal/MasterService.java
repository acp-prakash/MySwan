package org.myswan.service.internal;

import org.myswan.model.Master;
import org.myswan.repository.MasterRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bson.Document;

@Service
public class MasterService {
    private final MasterRepository repository;

    public MasterService(MasterRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
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
}

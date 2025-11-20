package org.myswan.service.internal;

import org.myswan.model.Tactic;
import org.myswan.repository.TacticRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TacticService {
    private final TacticRepository repository;

    public TacticService(TacticRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
    }

    public Optional<Tactic> getById(String id) {
        try {
            // Primary: try repository lookup by id (mapped @Id -> _id)
            Optional<Tactic> byId = repository.findById(id);
            if (byId.isPresent()) return byId;
        } catch (Exception ignored) {
            // fall through to raw queries
        }

        return Optional.empty();
    }

    public Tactic create(Tactic tactic) {
        if (tactic.getAddedDate() == null)
            tactic.setAddedDate(LocalDate.now());
        return repository.save(tactic);
    }

    public Tactic update(String id, Tactic tactic) {
        tactic.setId(id);
        return repository.save(tactic);
    }

    public void delete(String ticker) {
        repository.deleteById(ticker);
    }

    public boolean exists(String ticker) {
        return repository.existsById(ticker);
    }

    public List<Tactic> list() {
        // Try standard repository read first
        try {
            return repository.findAll();
        } catch (Exception ignored) {
            // fall through to raw DB read
        }
        return new ArrayList<>();
    }
}

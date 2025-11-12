package org.myswan.service.internal;

import org.myswan.model.Pattern;
import org.myswan.repository.PatternRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PatternService {
    private final PatternRepository repository;

    public PatternService(PatternRepository repository) {
        this.repository = repository;
    }

    public Optional<Pattern> getByTicker(String id) {
        try {
            return repository.findById(id);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Pattern create(Pattern pattern) {
        return repository.save(pattern);
    }

    public Pattern update(String id, Pattern pattern) {
        pattern.setId(id);
        return repository.save(pattern);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public boolean exists(String id) {
        return repository.existsById(id);
    }

    public List<Pattern> list() {
        try {
            List<Pattern> all = repository.findAll();
            if (all != null && !all.isEmpty()) return all;
        } catch (Exception ignored) {
            // fall through to empty result
        }

        // Return empty list if no patterns found
        return new ArrayList<>();
    }
}

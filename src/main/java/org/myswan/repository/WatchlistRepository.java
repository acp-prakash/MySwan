package org.myswan.repository;

import org.myswan.model.Watchlist;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WatchlistRepository extends MongoRepository<Watchlist, String> {
    Optional<Watchlist> findByTicker(String ticker);
    boolean existsByTicker(String ticker);
    void deleteByTicker(String ticker);
}


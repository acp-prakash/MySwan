package org.myswan.repository;

import org.myswan.model.collection.Picks;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PicksRepository extends MongoRepository<Picks, String> {
    List<Picks> findByTicker(String ticker);
    List<Picks> findByTickerOrderByAddedDateDesc(String ticker);
}


package org.myswan.repository;

import org.myswan.model.GuaranteedPick;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for GuaranteedPick tracking
 */
@Repository
public interface GuaranteedPickRepository extends MongoRepository<GuaranteedPick, String> {

    List<GuaranteedPick> findByDate(String date);

    List<GuaranteedPick> findByTicker(String ticker);

    List<GuaranteedPick> findByDateOrderByRankAsc(String date);

    List<GuaranteedPick> findByTrackedFalse();

    List<GuaranteedPick> findByOutcome(String outcome);

    long countByOutcome(String outcome);
}


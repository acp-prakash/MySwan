package org.myswan.repository;

import org.myswan.model.collection.Futures;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FuturesRepository extends MongoRepository<Futures, String> {
}


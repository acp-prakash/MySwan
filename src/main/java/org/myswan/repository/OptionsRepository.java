package org.myswan.repository;

import org.myswan.model.collection.Options;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionsRepository extends MongoRepository<Options, String> {
    List<Options> findByTickerOrderByHistDateDesc(String ticker);
}


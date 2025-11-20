package org.myswan.repository;

import org.myswan.model.Tactic;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TacticRepository extends MongoRepository<Tactic, String> {
}


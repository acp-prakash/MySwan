package org.myswan.repository;

import org.myswan.model.Pattern;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatternRepository extends MongoRepository<Pattern, String> {
}


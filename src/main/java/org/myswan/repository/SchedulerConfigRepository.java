package org.myswan.repository;

import org.myswan.model.collection.SchedulerConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchedulerConfigRepository extends MongoRepository<SchedulerConfig, String> {
}


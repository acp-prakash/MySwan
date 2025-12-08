package org.myswan.repository;

import org.myswan.model.collection.AppCache;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppCacheRepository extends MongoRepository<AppCache, String> {
}


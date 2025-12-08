package org.myswan.repository;

import org.myswan.model.collection.Master;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterRepository extends MongoRepository<Master, String> {
}


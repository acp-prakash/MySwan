package org.myswan.repository;

import org.myswan.model.Master;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterRepository extends MongoRepository<Master, String> {
}


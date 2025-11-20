package org.myswan.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class MongoConfig {

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostConstruct
    public void initIndexes() {

        // 1. unique combined index
        mongoTemplate.indexOps("stockHistory")
                .ensureIndex(new Index()
                        .on("ticker", Sort.Direction.ASC)
                        .on("histDate", Sort.Direction.ASC)
                        .unique()
                );

        // 2. ticker index
        mongoTemplate.indexOps("stockHistory")
                .ensureIndex(new Index()
                        .on("ticker", Sort.Direction.ASC));

        // 3. histDate index
        mongoTemplate.indexOps("stockHistory")
                .ensureIndex(new Index()
                        .on("histDate", Sort.Direction.ASC));
    }
}

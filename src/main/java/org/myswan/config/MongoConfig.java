package org.myswan.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

@Configuration
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

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

        // 4. futuresHistory - clean duplicates before creating unique index
        cleanFuturesHistoryDuplicates();

        // 5. futuresHistory - unique combined index
        mongoTemplate.indexOps("futuresHistory")
                .ensureIndex(new Index()
                        .on("ticker", Sort.Direction.ASC)
                        .on("histDate", Sort.Direction.ASC)
                        .unique()
                );

        // 6. futuresHistory - ticker index
        mongoTemplate.indexOps("futuresHistory")
                .ensureIndex(new Index()
                        .on("ticker", Sort.Direction.ASC));

        // 7. futuresHistory - histDate index
        mongoTemplate.indexOps("futuresHistory")
                .ensureIndex(new Index()
                        .on("histDate", Sort.Direction.ASC));

        // 8. Compound index for efficient latest picks query
        mongoTemplate.indexOps("picksHistory")
                .ensureIndex(new Index()
                        .on("ticker", Sort.Direction.ASC)
                        .on("historyDate", Sort.Direction.DESC)
                );

        // 9. historyDate index for sorting
        mongoTemplate.indexOps("picksHistory")
                .ensureIndex(new Index()
                        .on("historyDate", Sort.Direction.DESC));
    }

    /**
     * Remove duplicate entries from futuresHistory collection before creating unique index.
     * Keeps the most recent document (by _id) for each ticker+histDate combination.
     */
    private void cleanFuturesHistoryDuplicates() {
        try {
            log.info("Checking for duplicate entries in futuresHistory collection...");

            // Use aggregation to find duplicates
            Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("ticker", "histDate")
                    .count().as("count")
                    .first("_id").as("firstId"),
                Aggregation.match(Criteria.where("count").gt(1))
            );

            AggregationResults<Map> results = mongoTemplate.aggregate(
                aggregation,
                "futuresHistory",
                Map.class
            );

            List<Map> duplicateGroups = results.getMappedResults();

            if (duplicateGroups.isEmpty()) {
                log.info("No duplicates found in futuresHistory collection");
                return;
            }

            log.warn("Found {} duplicate groups in futuresHistory collection. Cleaning up...",
                duplicateGroups.size());

            int totalDeleted = 0;

            // For each duplicate group, keep only the first one and delete the rest
            for (Map group : duplicateGroups) {
                Map<String, Object> id = (Map<String, Object>) group.get("_id");
                String ticker = (String) id.get("ticker");
                Object histDate = id.get("histDate");

                // Find all documents for this ticker+histDate
                Query query = Query.query(
                    Criteria.where("ticker").is(ticker)
                        .and("histDate").is(histDate)
                );
                query.with(Sort.by(Sort.Direction.DESC, "_id"));

                List<Map> docs = mongoTemplate.find(query, Map.class, "futuresHistory");

                // Delete all except the first one (most recent _id)
                if (docs.size() > 1) {
                    for (int i = 1; i < docs.size(); i++) {
                        Object docId = docs.get(i).get("_id");
                        Query deleteQuery = Query.query(Criteria.where("_id").is(docId));
                        mongoTemplate.remove(deleteQuery, "futuresHistory");
                        totalDeleted++;
                    }
                }
            }

            log.info("Successfully cleaned up {} duplicate entries from futuresHistory collection",
                totalDeleted);

        } catch (Exception e) {
            log.error("Error cleaning futuresHistory duplicates", e);
            // Don't throw - let the app continue, the index creation will fail with clear error
        }
    }
}

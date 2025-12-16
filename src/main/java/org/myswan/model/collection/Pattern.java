package org.myswan.model.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "pattern")
public class Pattern {
    @Id
    private String id;
    private String eventId;
    private String ticker;
    private String histDate;
    private String entry;
    private String minPT;
    private String maxPT;
    private String stop;
    private String targetDate;
    private String patternEmergenceDate;
    private String eventBeginDate;
    private String eventEndDate;
    private String name;
    private String trend;
    private String status;
    private int noOfLongPatterns;
    private int noOfShortPatterns;
    private boolean patternMet = false;

    // Stock snapshot (only populated in patternHistory collection)
    private Stock stock;
}

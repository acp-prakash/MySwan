package org.myswan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "picks")
public class Picks {
    @Id
    private String id;
    private String ticker;
    private String reason;
    private LocalDate addedDate;
    private double addedPrice;
    private double entry;
    private double target;
    private double stopLoss;
    private LocalDate targetDate;
    private LocalDate targetMetDate;
    private LocalDate stopLossMetDate;
    private boolean targetMet;
    private boolean stopLossMet;

    // Current/Latest data (updated from stock collection)
    private double currentPrice;
    private double currentChange;
    private int currentNoOfLongPatterns;
    private int currentNoOfShortPatterns;

    // Original data at time of adding
    private int noOfLongPatterns;
    private int noOfShortPatterns;
    private double overAllScore;
    private double bottomScore;
    private double reversalScore;
    private double breakoutScore;
    private double patternScore;
    private double spikeScore;
    private String signal;
    private String btShortRating;
    private String btLongRating;
    private String btRating;
    private String btTrend;
    private String tradingViewTechRating;
    private String tradingViewMARating;
    private String tradingViewOSRating;
}

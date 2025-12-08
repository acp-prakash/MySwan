package org.myswan.model.collection;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

/**
 * Tracks daily guaranteed explosive picks and their performance
 */
@Data
@Document(collection = "guaranteedPicks")
public class GuaranteedPick {

    @Id
    private String id;

    private String date;
    private String ticker;
    private int rank; // 1, 2, or 3

    // Entry data
    private double entryPrice;
    private int factorsPassed;
    private int convergenceScore;
    private int confidenceLevel;
    private List<String> passedFactors;
    private List<String> failedFactors;

    // Performance tracking (updated after 5 days)
    private Double maxPriceReached;
    private Double maxGainPct;
    private Double finalPrice;
    private Double finalGainPct;

    private Boolean moved15Percent; // Success threshold
    private Integer daysToMove;
    private String outcome; // "SUCCESS", "PARTIAL", "FAIL", "PENDING"

    private LocalDate trackingDate; // When to check results
    private boolean tracked; // Has performance been recorded?

    public GuaranteedPick() {
        this.outcome = "PENDING";
        this.tracked = false;
    }
}


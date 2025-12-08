package org.myswan.model.dto;

import lombok.Data;

/**
 * Simplified DTO for displaying all stocks with explosive scores in grid
 */
@Data
public class ExplosiveScoreDTO {

    private String ticker;
    private double price;
    private double change;
    private double changePct;
    private double volume;
    private double volumeM;

    // Convergence analysis
    private int factorsPassed;
    private int convergenceScore;
    private int confidenceLevel;
    private String confidenceText;

    // Key metrics
    private int upDays;
    private Integer noOfLongPatterns;
    private Integer noOfShortPatterns;
    private Integer spikeScore;
    private Integer overallScore;
    private String signal;

    // Top reasons (comma separated)
    private String topReasons;

    // Full factor lists for detailed view
    private java.util.List<String> passedFactors;
    private java.util.List<String> failedFactors;

    public ExplosiveScoreDTO() {
    }
}


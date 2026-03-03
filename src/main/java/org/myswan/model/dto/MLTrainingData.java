package org.myswan.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLTrainingData {
    // Identifier
    private String ticker;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    // Price features
    private double price;
    private double open;
    private double high;
    private double low;
    private double change;
    private double volume;
    private double volumeChange;

    // Your custom scores (UNIQUE EDGE!)
    private int overallScore;
    private double pickScore;
    private int safetyRank;
    private double finalRank;
    private double allocation;

    // Strategy scores
    private int dayTradingScore;
    private int swingTradingScore;
    private int reversalScore;
    private int breakoutScore;
    private int patternScore;

    // Technical indicators
    private double rsi14;
    private double macd1226;
    private double atr14;
    private double momentum;
    private double sma9;
    private double sma20;
    private double sma50;
    private double sma200;
    private double ema9;
    private double ema20;
    private double ema50;
    private double ema200;
    private double vwap;

    // Price changes
    private double priceChg5D;
    private double priceChg10D;
    private double priceChg20D;
    private double low52;
    private double high52;

    // Pattern & signal features
    private int noOfLongPatterns;
    private int noOfShortPatterns;
    private String signal;
    private int signalDays;
    private int upDays;
    private int downDays;
    private double upHigh;
    private double downLow;

    // Bottom/Spike signals
    private Integer bottomConditionsMet;
    private String bottomStrength;
    private Integer spikeScore;
    private String spikeType;

    // Target variables (for ML training)
    private Double return1d;   // Next 1 day return
    private Double return3d;   // Next 3 days return
    private Double return7d;   // Next 7 days return
    private Boolean hitTarget; // Did it hit 2% in 3 days?
    private Double maxDrawdown7d; // Worst drop in next 7 days
}

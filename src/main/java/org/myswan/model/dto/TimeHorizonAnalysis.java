package org.myswan.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing analysis results for a specific time horizon (D1-D10)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeHorizonAnalysis {

    private String timeHorizon;           // e.g., "Day +1", "Day +2", etc.
    private String bestCombo;              // e.g., "SCORE>80 + SPIKE + BOTTOM"
    private double winPercentage;          // e.g., 63.5%
    private double avgReturn;              // e.g., 3.1%
    private int totalTrades;               // e.g., 102
    private int winningTrades;             // Number of trades with positive return
    private int losingTrades;              // Number of trades with negative return
    private double maxReturn;              // Best return in this combo
    private double minReturn;              // Worst return in this combo
    private double sharpeRatio;            // Risk-adjusted return metric

    // Peak analysis - best return achieved on ANY day between D1-D10
    private double peakReturn;             // Highest return achieved on any day (D1-D10)
    private String peakDay;                // Which day achieved the peak (e.g., "Day +5")

    // Constructor for easy creation
    public TimeHorizonAnalysis(String timeHorizon, String bestCombo, double winPercentage,
                               double avgReturn, int totalTrades) {
        this.timeHorizon = timeHorizon;
        this.bestCombo = bestCombo;
        this.winPercentage = winPercentage;
        this.avgReturn = avgReturn;
        this.totalTrades = totalTrades;
    }
}


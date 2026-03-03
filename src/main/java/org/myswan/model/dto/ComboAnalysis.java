package org.myswan.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a specific factor combination analysis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComboAnalysis {

    private String combo;                  // e.g., "A+B+C", "SCORE>80 + SPIKE"
    private double winPercentage;          // Win rate %
    private double expectedReturn;         // Average return
    private int trades;                    // Total trades
    private int winningTrades;             // Wins
    private int losingTrades;              // Losses
    private double stdDeviation;           // Standard deviation of returns
    private double maxReturn;              // Best trade
    private double minReturn;              // Worst trade
    private double profitFactor;           // Total wins / Total losses
    private String factors;                // Detailed factor list

    // Constructor for summary
    public ComboAnalysis(String combo, double winPercentage, double expectedReturn, int trades) {
        this.combo = combo;
        this.winPercentage = winPercentage;
        this.expectedReturn = expectedReturn;
        this.trades = trades;
    }
}


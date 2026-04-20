package org.myswan.model.compute;

import lombok.Data;
import org.myswan.model.collection.Stock;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for Guaranteed Explosive Candidate
 */
@Data
public class GuaranteedCandidateDTO {

    private Stock stock;
    private int factorsPassed; // Out of 5 per path
    private int convergenceScore; // Out of 100
    private List<String> passedFactors;
    private List<String> failedFactors;
    private int confidenceLevel; // 1-5 stars
    private String confidenceText;
    private String strategyPath; // "OVERSOLD_BOUNCE" or "MOMENTUM_POP"

    public GuaranteedCandidateDTO() {
        this.passedFactors = new ArrayList<>();
        this.failedFactors = new ArrayList<>();
    }

    public GuaranteedCandidateDTO(Stock stock) {
        this();
        this.stock = stock;
    }

    public void calculateConfidence() {
        // Strategy label shown in confidence text
        String pathLabel = "OVERSOLD_BOUNCE".equals(strategyPath)
            ? "⬇️ Bounce Setup" : "📈 Momentum Pop";

        // 5-star confidence rating based on factors out of 5 per path
        if (factorsPassed >= 5) {
            confidenceLevel = 5;
            confidenceText = "EXTREMELY HIGH - " + pathLabel;
        } else if (factorsPassed >= 4) {
            confidenceLevel = 5;
            confidenceText = "VERY HIGH - " + pathLabel;
        } else if (factorsPassed >= 3) {
            confidenceLevel = 4;
            confidenceText = "HIGH - " + pathLabel;
        } else if (factorsPassed >= 2) {
            confidenceLevel = 3;
            confidenceText = "MODERATE - " + pathLabel;
        } else {
            confidenceLevel = 2;
            confidenceText = "LOW - Speculative";
        }
    }
}


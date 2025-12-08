package org.myswan.model.dto;

import lombok.Data;
import org.myswan.model.Stock;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for Guaranteed Explosive Candidate
 */
@Data
public class GuaranteedCandidateDTO {

    private Stock stock;
    private int factorsPassed; // Out of 10
    private int convergenceScore; // Out of 100
    private List<String> passedFactors;
    private List<String> failedFactors;
    private int confidenceLevel; // 1-5 stars
    private String confidenceText;

    public GuaranteedCandidateDTO() {
        this.passedFactors = new ArrayList<>();
        this.failedFactors = new ArrayList<>();
    }

    public GuaranteedCandidateDTO(Stock stock) {
        this();
        this.stock = stock;
    }

    public void calculateConfidence() {
        // 5-star confidence rating
        if (factorsPassed >= 9) {
            confidenceLevel = 5;
            confidenceText = "EXTREMELY HIGH - Near Guaranteed";
        } else if (factorsPassed >= 8) {
            confidenceLevel = 5;
            confidenceText = "VERY HIGH - High Probability";
        } else if (factorsPassed >= 7) {
            confidenceLevel = 4;
            confidenceText = "HIGH - Strong Conviction";
        } else if (factorsPassed >= 6) {
            confidenceLevel = 4;
            confidenceText = "GOOD - Likely to Move";
        } else if (factorsPassed >= 5) {
            confidenceLevel = 3;
            confidenceText = "MODERATE - Watch Closely";
        } else {
            confidenceLevel = 2;
            confidenceText = "LOW - Speculative";
        }
    }
}


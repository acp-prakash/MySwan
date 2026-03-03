package org.myswan.model.compute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Layer 2: Confidence Tier Rating (GPT-4 Hybrid System)
 * Controls CONFIDENCE and POSITION SIZE - Only checked after Gate passes
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfidenceTier {

    // Tier rating
    private String tier;                // "A_PLUS", "A", "B_PLUS", "B", "NONE"
    private int tierScore;              // 0-100 numerical score
    private String tierDescription;     // Human-readable tier description

    // Position sizing recommendations
    private double positionSize;        // 0.03-0.10 (3%-10% of capital)
    private double stopLoss;            // -0.05 to -0.07 (-5% to -7%)
    private String targetRange;         // "+15% to +30%", "+20% to +40%", etc.
    private String holdDays;            // "1-3 days", "2-4 days", "3-5 days"

    // Sonnet's indicators used for tier calculation
    private int spikeScore;             // From spike.spikeScore
    private int bottomConditions;       // From bottom.conditionsMet
    private int bounceScore;            // From oversold.bounceScore

    // Tier criteria met
    private boolean meetsAPlusCriteria; // spike≥65, bottom≥6, bounce≥80
    private boolean meetsACriteria;     // spike≥55, bottom≥5, bounce≥70
    private boolean meetsBPlusCriteria; // spike≥50, bottom≥4, bounce≥60

    // Metadata
    private String reason;              // Why this tier was assigned
    private boolean tradeable;          // true if Tier B+ or better
}


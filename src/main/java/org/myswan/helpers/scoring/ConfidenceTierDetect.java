package org.myswan.helpers.scoring;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Stock;
import org.myswan.model.compute.ConfidenceTier;
import org.springframework.stereotype.Component;

/**
 * Layer 2: Confidence Tier Detection (GPT-4 Hybrid System)
 * Only evaluated AFTER Gate Signal passes - Determines position size and conviction
 */
@Slf4j
@Component
public class ConfidenceTierDetect {

    /**
     * Calculate confidence tier for a stock
     * Only meaningful if gateSignal has passed
     */
    public void detectConfidenceTier(Stock stock) {
        try {
            ConfidenceTier tier = new ConfidenceTier();

            // Extract Sonnet's indicators
            int spikeScore = stock.getSpike() != null
                ? stock.getSpike().getSpikeScore() : 0;
            int bottomConditions = stock.getBottom() != null
                ? stock.getBottom().getConditionsMet() : 0;
            int bounceScore = stock.getOversold() != null
                ? stock.getOversold().getBounceScore() : 0;

            tier.setSpikeScore(spikeScore);
            tier.setBottomConditions(bottomConditions);
            tier.setBounceScore(bounceScore);

            // Check tier criteria
            boolean meetsAPlus = spikeScore >= 65 && bottomConditions >= 6 && bounceScore >= 80;
            boolean meetsA = spikeScore >= 55 && bottomConditions >= 5 && bounceScore >= 70;
            boolean meetsBPlus = spikeScore >= 50 && bottomConditions >= 4 && bounceScore >= 60;

            tier.setMeetsAPlusCriteria(meetsAPlus);
            tier.setMeetsACriteria(meetsA);
            tier.setMeetsBPlusCriteria(meetsBPlus);

            // Check if gate passed AND is Day 1 Entry (both layers must agree)
            boolean gateReady = stock.getGateSignal() != null
                && stock.getGateSignal().isGatePass()
                && "DAY_1_ENTRY".equals(stock.getGateSignal().getGateType());

            // Assign tier and parameters
            if (meetsAPlus) {
                tier.setTier("A_PLUS");
                tier.setTierScore(95);
                tier.setTierDescription("⭐ Maximum Confidence");
                tier.setPositionSize(0.10); // 10% of capital
                tier.setStopLoss(-0.07);    // -7%
                tier.setTargetRange("+30% to +50%");
                tier.setHoldDays("3-5 days");
                tier.setTradeable(gateReady); // Only tradeable if gate also ready
                tier.setReason(String.format(
                    "Tier A+: spike=%d, bottom=%d, bounce=%d - Highest conviction trade",
                    spikeScore, bottomConditions, bounceScore
                ));

            } else if (meetsA) {
                tier.setTier("A");
                tier.setTierScore(80);
                tier.setTierDescription("✅ High Confidence");
                tier.setPositionSize(0.07); // 7% of capital
                tier.setStopLoss(-0.07);    // -7%
                tier.setTargetRange("+20% to +40%");
                tier.setHoldDays("2-4 days");
                tier.setTradeable(gateReady); // Only tradeable if gate also ready
                tier.setReason(String.format(
                    "Tier A: spike=%d, bottom=%d, bounce=%d - Strong setup",
                    spikeScore, bottomConditions, bounceScore
                ));

            } else if (meetsBPlus) {
                tier.setTier("B_PLUS");
                tier.setTierScore(65);
                tier.setTierDescription("⚠️ Moderate Confidence");
                tier.setPositionSize(0.05); // 5% of capital
                tier.setStopLoss(-0.05);    // -5%
                tier.setTargetRange("+15% to +30%");
                tier.setHoldDays("1-3 days");
                tier.setTradeable(gateReady); // Only tradeable if gate also ready
                tier.setReason(String.format(
                    "Tier B+: spike=%d, bottom=%d, bounce=%d - Acceptable setup",
                    spikeScore, bottomConditions, bounceScore
                ));

            } else {
                tier.setTier("B");
                tier.setTierScore(40);
                tier.setTierDescription("❌ Low Confidence");
                tier.setPositionSize(0.03); // 3% of capital or SKIP
                tier.setStopLoss(-0.05);    // -5%
                tier.setTargetRange("+10% to +20%");
                tier.setHoldDays("1-2 days");
                tier.setTradeable(false);   // Always false for Tier B
                tier.setReason(String.format(
                    "Tier B: spike=%d, bottom=%d, bounce=%d - Weak setup, consider skipping",
                    spikeScore, bottomConditions, bounceScore
                ));
            }

            stock.setConfidenceTier(tier);

        } catch (Exception ex) {
            log.error("Error in ConfidenceTierDetect.detectConfidenceTier for ticker {}: ",
                stock.getTicker(), ex);
        }
    }

    /**
     * Check if stock is tradeable based on hybrid system (both layers)
     */
    public static boolean isTradeable(Stock stock) {
        // Layer 1: Gate must pass
        if (stock.getGateSignal() == null || !stock.getGateSignal().isGatePass()) {
            return false;
        }

        // Layer 2: Must be Tier B+ or better
        if (stock.getConfidenceTier() == null || !stock.getConfidenceTier().isTradeable()) {
            return false;
        }

        // Both layers agree
        return true;
    }

    /**
     * Get recommended position size based on tier
     */
    public static double getPositionSize(Stock stock) {
        if (stock.getConfidenceTier() == null) {
            return 0.0;
        }
        return stock.getConfidenceTier().getPositionSize();
    }
}


package org.myswan.helpers.scoring;

import org.myswan.model.collection.Stock;
import org.myswan.model.compute.DailyRank;
import org.springframework.stereotype.Component;

@Component
public class DailyRanking {

    public DailyRanking() {
        super();
    }

    public void dailyRanking(Stock s) {

       s.setDailyRank(new DailyRank());

        if(isValidSetup(s)) {
            evaluateStock(s);
        }
    }

    // ───────────────────────────────────────────────────────────────
    // STEP 1 – TRADE SETUP FILTERING
    // ───────────────────────────────────────────────────────────────
    private boolean isValidSetup(Stock s) {

        // 1. Skip NO-SETUP category
        if (s.getFilterCategory() != null &&
                "99-NO-SETUP".equalsIgnoreCase(s.getFilterCategory().getPrimaryCategory())) {
            return false;
        }

        boolean momentumPop = s.getMomPop() != null && s.getMomPop().isMomentumPop();
        boolean spikeLikely = s.getSpike() != null && s.getSpike().isSpikeLikely();
        boolean bottomStrong = s.getBottom() != null && s.getBottom().isBottom();

        // Valid setup if ANY trade setup matches
        return (momentumPop || spikeLikely || bottomStrong);
    }

    // ───────────────────────────────────────────────────────────────
    // Converts Stock → PickResult (Rank, Safety, Allocation, PickScore)
    // ───────────────────────────────────────────────────────────────
    private void evaluateStock(Stock s) {
        int finalRank = computeFinalRank(s);
        int safetyRank = computeSafetyRank(s);
        double allocation = computeAllocationPercent(s, safetyRank, finalRank);

        double pickScore =
                (finalRank * 0.60) +
                        (safetyRank * 0.25) +
                        (allocation * 0.15);

        s.getDailyRank().setFinalRank(finalRank);
        s.getDailyRank().setSafetyRank(safetyRank);
        s.getDailyRank().setAllocation(round(allocation));
        s.getDailyRank().setPickScore(round(pickScore));
    }

    // ───────────────────────────────────────────────────────────────
    // FINAL RANK (integer)
    // ───────────────────────────────────────────────────────────────
    private int computeFinalRank(Stock s) {

        boolean mom = s.getMomPop() != null && s.getMomPop().isMomentumPop();
        boolean spike = s.getSpike() != null && s.getSpike().isSpikeLikely();
        boolean bottom = s.getBottom() != null && s.getBottom().getConditionsMet() >= 4;

        if (mom) {
            return (int)round(
                    s.getMomPop().getPopScore() * 0.70 +
                            s.getScore().getOverallScore() * 0.30
            );
        }

        if (spike) {
            return (int)round(
                    s.getSpike().getSpikeScore() * 0.70 +
                            s.getScore().getReversalScore() * 0.30
            );
        }

        if (bottom) {
            return (int)round(
                    (s.getBottom().getConditionsMet() * 12) +
                            (s.getScore().getReversalScore() * 0.50)
            );
        }

        return 0;
    }

    // ───────────────────────────────────────────────────────────────
    // SAFETY RANK (0–100)
    // ───────────────────────────────────────────────────────────────
    private int computeSafetyRank(Stock s) {

        int safety = 0;

        // 1. Reversal strength
        if (s.getBottom() != null) {
            safety += s.getBottom().getConditionsMet() * 10;  // max 60
        }

        // 2. Volume > AvgVol
        try {
            if (s.getVolume() > s.getAvgVolume10D())
                safety += 10;
        } catch (Exception ignored) {}

        // 3. RSI 45–60 ideal zone
        double rsi = s.getRsi14();
        if (rsi >= 45 && rsi <= 60)
            safety += 10;

        // 4. No bearish patterns
        if (s.getNoOfShortPatterns() == 0)
            safety += 10;

        return Math.min(safety, 100);
    }

    // ───────────────────────────────────────────────────────────────
    // ALLOCATION PERCENT (3%–35%)
    // ───────────────────────────────────────────────────────────────
    private double computeAllocationPercent(Stock s, int safetyRank, double finalRank) {

        double price = s.getPrice();
        double atr = s.getAtr14();

        if (price == 0) return 5;

        double volatility = (atr / price) * 100;

        double alloc;

        // 1. Base by volatility
        if (volatility < 3)
            alloc = 30;
        else if (volatility < 6)
            alloc = 20;
        else
            alloc = 10;

        // 2. Adjust by safety
        alloc += (safetyRank - 50) / 20.0;

        // 3. Confidence by FinalRank
        if (finalRank >= 80)
            alloc += 5;
        else if (finalRank >= 60)
            alloc += 3;
        else if (finalRank >= 40)
            alloc += 1;
        else
            alloc -= 5;

        // 4. Price constraint (cheap stocks = smaller allocation)
        if (price < 20)
            alloc = Math.min(alloc, 12);

        // 5. Final clamp
        return Math.max(3, Math.min(35, alloc));
    }

    // ───────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────
    private double safe(Double d) {
        return d == null ? 0 : d;
    }

    private int safeInt(Integer i) {
        return i == null ? 0 : i;
    }

    private double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}

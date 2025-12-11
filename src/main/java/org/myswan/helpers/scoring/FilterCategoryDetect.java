package org.myswan.helpers.scoring;

import org.myswan.model.collection.Stock;
import org.myswan.model.compute.FilterCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class FilterCategoryDetect {

    private static final Logger log = LoggerFactory.getLogger(FilterCategoryDetect.class);
    public FilterCategoryDetect() {
        super();
    }

    public void filterCategory(Stock s, Stock history) {
        try {
            FilterCategory result = new FilterCategory();
            result.setCriteria(new ArrayList<>());
            result.setCategory(new ArrayList<>());
            String primary = "99-NO-SETUP";

            if (history == null) {
                result.setPrimaryCategory(primary);
                s.setFilterCategory(result);
                return;
            }

            // 1 Explosive Spike Candidate
            if (isExplosiveSpike(s)) {
                result.getCategory().add("1-Explosive Spike");
                result.getCriteria().add("Bottom true;Bottom Strength Strong;Oversold Bounce Score >= 40;Down Days >= 3;Spike Score >= 60;Spike Likely true;No of Long Patterns >= 1");
                primary = "1-Explosive Spike";
            }

            // 2 Oversold Bounce (Reversal)
            if (isOversoldBounce(s)) {
                result.getCategory().add("2-Oversold Bounce");
                result.getCriteria().add("oversoldBounce true;Oversold Bounce Score >= 40;Bottom true;Down Days >= 4;Spike Score < 40;No of Long Patterns >= 1;Signal not SELL");
                if (primary.equals("99-NO-SETUP")) primary = "2-Oversold Bounce";
            }

            // 3 High-Probability Downtrend Reversal
            if (isDowntrendReversal(s)) {
                result.getCategory().add("3-Downtrend Reversal");
                result.getCriteria().add("Down Days >= 5;Down Low within 2% of Price;Bottom Strength Reversal;Oversold Bounce Score >= 30;No of Long Patterns >= 1");
                if (primary.equals("99-NO-SETUP")) primary = "3-Downtrend Reversal";
            }

            // 4 isMomentumPop
            if (isMomentumPop(s)) {
                result.getCategory().add("4-MomentumPop");
                result.getCriteria().add("MomentumPop true;Pop Score >= 60");
                if (primary.equals("99-NO-SETUP")) primary = "4-MomentumPop";
            }

            // 5 Breakout Watchlist
            if (isBreakoutWatch(s, history)) {
                result.getCategory().add("5-Breakout Watch");
                result.getCriteria().add("Up Days 1-2;Up High > Previous High;No of Long Patterns >= 1;Spike Score 20-60;Overall Score >= 40");
                if (primary.equals("99-NO-SETUP")) primary = "5-Breakout Watch";
            }

            // 6 Trend Continuation (Uptrend Momentum)
            if (isTrendContinuation(s)) {
                result.getCategory().add("6-Trend Continuation");
                result.getCriteria().add("Up Days >= 3;Up High within 2% of Price;No of Long Patterns >= 2;Spike Score < 40;Overall Score >= 60;Signal BUY or HOLD");
                if (primary.equals("99-NO-SETUP")) primary = "6-Trend Continuation";
            }

            // 7 Danger: Overextended
            if (isOverextended(s)) {
                result.getCategory().add("7-Overextended Warning");
                result.getCriteria().add("Up Days >= 4;Up High within 5% of Price;Daily Change % > 3%;Spike Score < 20;Signal SELL");
                if (primary.equals("99-NO-SETUP")) primary = "7-Overextended Warning";
            }

            result.setPrimaryCategory(primary);
            s.setFilterCategory(result);
        } catch (Exception ex) {
            log.error("Error in FilterCategoryDetect.filterCategory for ticker {}: ", s.getTicker(), ex);
        }
    }

    // ---------------------------------------------------------
    // FILTER LOGICS
    // ---------------------------------------------------------

    // 1 Explosive Spike Candidate (CLSX / CONL / QBTS type)
    private static boolean isExplosiveSpike(Stock s) {
        return s.getBottom() != null && s.getBottom().isBottom()
                && s.getBottom().getStrength() != null && s.getBottom().getStrength().contains("Strong")
                && s.getOversold() != null && s.getOversold().getBounceScore() >= 40
                && s.getDownDays() >= 3
                && s.getSpike() != null && s.getSpike().getSpikeScore() >= 60
                && s.getSpike().isSpikeLikely()
                && s.getNoOfLongPatterns() >= 1;
    }

    // 2 Oversold Bounce
    private static boolean isOversoldBounce(Stock s) {
        return s.getOversold() != null && s.getOversold().isOversoldBounce()
                && s.getOversold().getBounceScore() >= 40
                && s.getBottom() != null && s.getBottom().isBottom()
                && s.getDownDays() >= 4
                && s.getSpike() != null && s.getSpike().getSpikeScore() < 40
                && s.getNoOfLongPatterns() >= 1
                && s.getScore() != null && s.getScore().getSignal() != null &&
                !s.getScore().getSignal().equalsIgnoreCase("SELL");
    }

    // 3 Trend Continuation (Strong Uptrend)
    private static boolean isTrendContinuation(Stock s) {
        return s.getUpDays() >= 3
                && s.getUpHigh() >= s.getPrice() * 0.98
                && s.getNoOfLongPatterns() >= 2
                && s.getSpike() != null && s.getSpike().getSpikeScore() < 40
                && s.getScore() != null && s.getScore().getOverallScore() >= 60
                && s.getScore().getSignal() != null
                && (s.getScore().getSignal().equalsIgnoreCase("BUY")
                || s.getScore().getSignal().equalsIgnoreCase("HOLD"));
    }

    // 4 ️isMomentumPop
    private static boolean isMomentumPop(Stock s) {
        return s.getMomPop() != null &&
                s.getMomPop().isMomentumPop() &&
                s.getMomPop().getPopScore() >= 60;
    }

    // 5 Breakout Watchlist
    private static boolean isBreakoutWatch(Stock s, Stock history) {
        return (s.getUpDays() == 1 || s.getUpDays() == 2)
                && s.getUpHigh() > history.getHigh()
                && s.getNoOfLongPatterns() >= 1
                && s.getSpike() != null && s.getSpike().getSpikeScore() >= 20
                && s.getSpike().getSpikeScore() < 60
                && s.getScore().getOverallScore() >= 40;
    }

    // 6 Danger: Overextended
    private static boolean isOverextended(Stock s) {
        double todayClose = s.getPrice();
        double yesterdayClose = s.getPrevClose();
        double changePct = ((todayClose - yesterdayClose) / yesterdayClose) * 100.0;

        return s.getUpDays() >= 4
                && s.getUpHigh() >= s.getPrice() * 1.05
                && changePct > 3
                && s.getSpike() != null && s.getSpike().getSpikeScore() < 20
                && s.getScore() != null && s.getScore().getSignal() != null
                && s.getScore().getSignal().equalsIgnoreCase("SELL");
    }

    // 7 High Probability Downtrend Reversal
    private static boolean isDowntrendReversal(Stock s) {
        return s.getDownDays() >= 5
                && Math.abs(s.getPrice() - s.getDownLow()) <= s.getPrice() * 0.02
                && s.getBottom() != null && s.getBottom().getStrength() != null &&
                s.getBottom().getStrength().contains("Reversal")
                && s.getOversold() != null && s.getOversold().getBounceScore() >= 30
                && s.getNoOfLongPatterns() >= 1;
    }
}
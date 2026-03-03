package org.myswan.helpers.scoring;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Stock;
import org.myswan.model.compute.GateSignal;
import org.springframework.stereotype.Component;

/**
 * Layer 1: Gate Signal Detection (GPT-4 Hybrid System)
 * Implements pre-spike setup identification to prevent chasing
 */
@Slf4j
@Component
public class GateSignalDetect {

    /**
     * Calculate gate signal for a stock
     * @param stock Current day stock data
     * @param previousDayStock Previous day data for Day 1 checks
     */
    public void detectGateSignal(Stock stock, Stock previousDayStock) {
        try {
            GateSignal gateSignal = new GateSignal();

            // Day 0: Pre-spike setup identification
            boolean day0Pass = checkDay0Setup(stock, gateSignal);

            // Day 1: Entry signal confirmation (if we have previous day data)
            boolean day1Pass = false;
            if (previousDayStock != null && previousDayStock.getGateSignal() != null
                && previousDayStock.getGateSignal().isGatePass()) {
                day1Pass = checkDay1Entry(stock, previousDayStock, gateSignal);
            }

            // Set overall gate status
            if (day1Pass) {
                gateSignal.setGatePass(true);
                gateSignal.setGateType("DAY_1_ENTRY");
                gateSignal.setGateScore(calculateGateScore(gateSignal, true));
                gateSignal.setReason("✅ Entry signal confirmed - Safe to trade");
            } else if (day0Pass) {
                gateSignal.setGatePass(true);
                gateSignal.setGateType("DAY_0_SETUP");
                gateSignal.setGateScore(calculateGateScore(gateSignal, false));
                gateSignal.setReason("⚠️ Setup detected - Watch for Day 1 entry");
            } else {
                gateSignal.setGatePass(false);
                gateSignal.setGateType("NO_GATE");
                gateSignal.setGateScore(0);
                gateSignal.setReason("❌ No gate criteria met");
            }

            stock.setGateSignal(gateSignal);

        } catch (Exception ex) {
            log.error("Error in GateSignalDetect.detectGateSignal for ticker {}: ", stock.getTicker(), ex);
        }
    }

    /**
     * Check Day 0 setup criteria (Watchlist identification - DO NOT BUY YET)
     */
    private boolean checkDay0Setup(Stock stock, GateSignal gateSignal) {
        // 1. RSI 20-35 (oversold but not extreme)
        double rsi = stock.getRsi14();
        boolean rsiCheck = rsi >= 20 && rsi <= 35;
        gateSignal.setDay0RsiCheck(rsiCheck);

        // 2. Volume spike ≥ 2× average (but price NOT up yet)
        double volRatio = stock.getAvgVolume10D() > 0
            ? stock.getVolume() / stock.getAvgVolume10D() : 0;
        boolean volumeCheck = volRatio >= 2.0;
        gateSignal.setDay0VolumeCheck(volumeCheck);

        // 3. ATR compression < 0.6
        double atrCompression = calculateAtrCompression(stock);
        boolean atrCheck = atrCompression < 0.6 && atrCompression > 0;
        gateSignal.setDay0AtrCheck(atrCheck);

        // 4. Bottom conditions ≥ 4 (reversal building)
        int bottomConditions = stock.getBottom() != null
            ? stock.getBottom().getConditionsMet() : 0;
        boolean bottomCheck = bottomConditions >= 4;
        gateSignal.setDay0BottomCheck(bottomCheck);

        // 5. Price change ≤ +5% (NOT already spiking)
        double priceChange = stock.getPrevClose() > 0
            ? (stock.getChange() / stock.getPrevClose()) * 100 : 0;
        boolean priceCheck = priceChange <= 5.0;
        gateSignal.setDay0PriceCheck(priceCheck);

        // 6. downDays ≥ 2 (coming from weakness)
        boolean downDaysCheck = stock.getDownDays() >= 2;
        gateSignal.setDay0DownDaysCheck(downDaysCheck);

        // 7. Liquidity requirements (price > $0.50, volume > 100k)
        boolean liquidityCheck = stock.getPrice() > 0.50
            && stock.getAvgVolume10D() > 100000;
        gateSignal.setDay0LiquidityCheck(liquidityCheck);

        // 8. upDays ≤ 1 (early, not chasing)
        boolean upDaysCheck = stock.getUpDays() <= 1;
        gateSignal.setDay0UpDaysCheck(upDaysCheck);

        // Day 0 passes if most criteria met (at least 6 of 8)
        int checksPass = 0;
        if (rsiCheck) checksPass++;
        if (volumeCheck) checksPass++;
        if (atrCheck) checksPass++;
        if (bottomCheck) checksPass++;
        if (priceCheck) checksPass++;
        if (downDaysCheck) checksPass++;
        if (liquidityCheck) checksPass++;
        if (upDaysCheck) checksPass++;

        return checksPass >= 6;
    }

    /**
     * Check Day 1 entry criteria (Safe entry confirmation)
     */
    private boolean checkDay1Entry(Stock today, Stock yesterday, GateSignal gateSignal) {
        // 1. Higher low (NOT lower low)
        boolean higherLow = today.getLow() >= yesterday.getLow();
        gateSignal.setDay1HigherLow(higherLow);

        // 2. Inside candle OR tight consolidation
        boolean insideCandle = today.getHigh() <= yesterday.getHigh()
            && today.getLow() >= yesterday.getLow();
        double range = today.getPrice() > 0
            ? (today.getHigh() - today.getLow()) / today.getPrice() * 100 : 100;
        boolean tightRange = range < 5.0; // < 5% daily range
        boolean consolidation = insideCandle || tightRange;
        gateSignal.setDay1Consolidation(consolidation);

        // 3. Volume cooling (≤ yesterday, not chasing)
        boolean volumeCooling = today.getVolume() <= yesterday.getVolume();
        gateSignal.setDay1VolumeCooling(volumeCooling);

        // 4. Price not extended (< +10% from Day 0 low)
        double priceMove = yesterday.getLow() > 0
            ? (today.getPrice() - yesterday.getLow()) / yesterday.getLow() * 100 : 100;
        boolean notExtended = priceMove < 10.0;
        gateSignal.setDay1NotExtended(notExtended);

        // 5. No FOMO gap (< 15%)
        double gapUp = yesterday.getHigh() > 0
            ? (today.getOpen() - yesterday.getHigh()) / yesterday.getHigh() * 100 : 0;
        boolean noFomoGap = gapUp < 15.0;
        gateSignal.setDay1NoFomoGap(noFomoGap);

        // 6. Not late (upDays ≤ 2)
        boolean notLate = today.getUpDays() <= 2;
        gateSignal.setDay1NotLate(notLate);

        // Day 1 passes if ALL critical criteria met
        return higherLow && consolidation && volumeCooling
            && notExtended && noFomoGap && notLate;
    }

    /**
     * Calculate ATR compression ratio
     */
    private double calculateAtrCompression(Stock stock) {
        // ATR compression = current range / ATR
        // Lower values = more compression (coiling spring)
        if (stock.getAtr14() <= 0) return 1.0;

        double range = stock.getHigh() - stock.getLow();
        return range / stock.getAtr14();
    }

    /**
     * Calculate gate score (0-100)
     */
    private int calculateGateScore(GateSignal gateSignal, boolean isDay1) {
        int score = 0;

        if (isDay1) {
            // Day 1 entry: All checks must pass for high score
            if (gateSignal.isDay1HigherLow()) score += 20;
            if (gateSignal.isDay1Consolidation()) score += 20;
            if (gateSignal.isDay1VolumeCooling()) score += 15;
            if (gateSignal.isDay1NotExtended()) score += 15;
            if (gateSignal.isDay1NoFomoGap()) score += 15;
            if (gateSignal.isDay1NotLate()) score += 15;
        } else {
            // Day 0 setup: Weight the most important criteria
            if (gateSignal.isDay0RsiCheck()) score += 15;
            if (gateSignal.isDay0VolumeCheck()) score += 15;
            if (gateSignal.isDay0AtrCheck()) score += 12;
            if (gateSignal.isDay0BottomCheck()) score += 15;
            if (gateSignal.isDay0PriceCheck()) score += 13;
            if (gateSignal.isDay0DownDaysCheck()) score += 10;
            if (gateSignal.isDay0LiquidityCheck()) score += 10;
            if (gateSignal.isDay0UpDaysCheck()) score += 10;
        }

        return Math.min(100, score);
    }
}


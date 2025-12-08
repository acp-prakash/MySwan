package org.myswan.helpers.scoring;

import org.myswan.model.collection.Stock;
import org.myswan.model.compute.SpikeSignal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class SpikeDetect {

    public SpikeDetect() {
        super();
    }

    public void detectSpikeSignal(Stock s, Stock history) {
        SpikeSignal result = new SpikeSignal();
        result.setReasons(new ArrayList<>());
        int score = 0;

        // Extract fields
        double price = s.getPrice();
        double prevPrice = s.getPrevClose();
        double high = s.getHigh();
        double low = s.getLow();
        double open = s.getOpen();
        double close = s.getPrice();

        double volume = s.getVolume();
        double avgVol10 = s.getAvgVolume10D();
        double vwap = s.getVwap();
        double prevVWAP = history.getVwap();

        double macd = s.getMacd1226();
        double prevMacd = history.getMacd1226();

        double ema9 = s.getEma9();
        double ema21 = s.getEma21();
        double ema50 = s.getEma50();

        double atr = s.getAtr14();

        //-----------------------------------------------------------------------
        // 1️⃣ VOLUME IMBALANCE — MOST IMPORTANT
        //-----------------------------------------------------------------------

        double volSpike = volume / (avgVol10 + 1);

        if (volSpike >= 2.0) {
            score += 15;
            result.getReasons().add("Volume spike " + String.format("%.2f", volSpike) + "x avg");
        }
        if (volSpike >= 4.0) {
            score += 15;
            result.getReasons().add("Major institutional accumulation volume");
        }

        //-----------------------------------------------------------------------
        // 2️⃣ VWAP PRESSURE — INTRADAY SPIKE DETECTOR
        //-----------------------------------------------------------------------

        boolean aboveVWAP = price > vwap;
        boolean risingVWAP = vwap > prevVWAP;

        if (aboveVWAP && risingVWAP) {
            score += 20;
            result.getReasons().add("Strong VWAP pressure (price above rising VWAP)");
        }

        //-----------------------------------------------------------------------
        // 3️⃣ VOLATILITY COMPRESSION (SPIKE SETUP)
        //-----------------------------------------------------------------------

        double range = high - low;
        double compression = range / (atr + 1);

        if (compression < 0.50) {
            score += 10;
            result.getReasons().add("Volatility compression (range < 0.5 ATR)");
        }

        if (compression < 0.30) {
            score += 10;
            result.getReasons().add("Strong volatility squeeze (range < 0.3 ATR)");
        }

        if (compression < 0.20) {
            score += 10;
            result.getReasons().add("Explosive squeeze (range < 0.2 ATR)");
        }

        //-----------------------------------------------------------------------
        // 4️⃣ ABSORPTION (BUY-WALL DETECTION)
        //-----------------------------------------------------------------------

        double candleSize = high - low;
        double lowerWick = Math.min(open, close) - low;

        if (candleSize > 0 && (lowerWick / candleSize) > 0.40) {
            score += 10;
            result.getReasons().add("Buy-wall absorption (long lower wick)");
        }

        //-----------------------------------------------------------------------
        // 5️⃣ BREAKOUT LEVELS (IMMEDIATE SPIKE TRIGGER)
        //-----------------------------------------------------------------------

        double highest10 = s.getHigh();

        if (price > highest10) {
            score += 20;
            result.getReasons().add("Breaking 10-day high → spike trigger");
        }

        //-----------------------------------------------------------------------
        // 6️⃣ MOMENTUM SHIFT (MACD SHIFT)
        //-----------------------------------------------------------------------

        if (macd > prevMacd && macd > 0) {
            score += 15;
            result.getReasons().add("MACD positive and rising → momentum shift");
        }

        //-----------------------------------------------------------------------
        // 7️⃣ TREND SHIFT (EMA9 > EMA21)
        //-----------------------------------------------------------------------

        if (ema9 > ema21) {
            score += 15;
            result.getReasons().add("EMA9 > EMA21 (micro bullish trend)");
        }

        //-----------------------------------------------------------------------
        // Final Scoring
        //-----------------------------------------------------------------------

        result.setSpikeScore(Math.min(score, 100));

        if (score >= 80) {
            result.setSpikeLikely(true);
            result.setSpikeType("EXPLOSIVE (HFT-level spike expected)");
        }
        else if (score >= 60) {
            result.setSpikeLikely(true);
            result.setSpikeType("HIGH (Strong breakout probability)");
        }
        else if (score >= 40) {
            result.setSpikeLikely(false);
            result.setSpikeType("MEDIUM (Watch closely)");
        }
        else {
            result.setSpikeLikely(false);
            result.setSpikeType("LOW");
        }
        s.setSpike(result);
    }
}

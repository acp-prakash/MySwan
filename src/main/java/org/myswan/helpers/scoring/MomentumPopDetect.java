package org.myswan.helpers.scoring;

import org.myswan.model.collection.Stock;
import org.myswan.model.compute.MomentumPopSignal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class MomentumPopDetect {

    public MomentumPopDetect() {
        super();
    }

    public void detectMomentumPop(Stock s, Stock history) {
        MomentumPopSignal result = new MomentumPopSignal();
        result.setReasons(new ArrayList<>());
        int score = 0;

        double price = s.getPrice();
        double high = s.getHigh();
        double low  = s.getLow();

        double ema9 = s.getEma9();
        double ema20 = s.getEma20();
        double ema21 = s.getEma21();
        double ema50 = s.getEma50();

        double sma20 = s.getSma20();
        double sma50 = s.getSma50();

        double rsi = s.getRsi14();
        double volume = s.getVolume();
        double avgVol = s.getAvgVolume10D();

        int longSignals = s.getNoOfLongPatterns(); // your 15m Long# count

        double spikeScore = s.getSpike().getSpikeScore();

        // -----------------------------------------------------
        // 1. Trend up — EMA and SMA alignment
        // -----------------------------------------------------
        if (price > ema9 && ema9 > ema21 && ema21 > ema50) {
            score += 20;
            result.getReasons().add("Bullish EMA alignment (price > 9 > 21 > 50)");
        }

        if (price > sma20 && sma20 > sma50) {
            score += 15;
            result.getReasons().add("Bullish SMA trend (price > SMA20 > SMA50)");
        }

        // -----------------------------------------------------
        // 2. Higher lows and squeeze pattern
        // -----------------------------------------------------
        if (price > ema21) {
            score += 10;
            result.getReasons().add("Price holding above EMA21 (higher lows)");
        }

        // Tight range consolidation (squeeze)
        double rangePct = ((high - low) / price) * 100.0;
        if (rangePct < 3.0) {
            score += 10;
            result.getReasons().add("Tight consolidation (volatility contraction)");
        }

        // EMA20 close to EMA50 → compression buildup
        if (Math.abs((ema20 - ema50) / ema20) < 0.02) {   // within 2%
            score += 10;
            result.getReasons().add("EMA20 and EMA50 compressing (squeeze setup)");
        }

        // -----------------------------------------------------
        // 3. Volume expansion but not huge spike (not reversal)
        // -----------------------------------------------------
        double volRatio = (double) volume / (double) avgVol;

        if (volRatio > 1.2 && volRatio < 3.0) {
            score += 10;
            result.getReasons().add("Moderate volume expansion (accumulation)");
        }

        // -----------------------------------------------------
        // 4. SpikeScore in the 30–60 band → near-term energy
        // -----------------------------------------------------
        if (spikeScore >= 30 && spikeScore <= 60) {
            score += 15;
            result.getReasons().add("SpikeScore in pressure zone (30–60)");
        }

        // -----------------------------------------------------
        // 5. Intraday bullish pressure
        // -----------------------------------------------------
        if (longSignals >= 1) {
            score += 10;
            result.getReasons().add("Intraday 15-min long patterns showing strength");
        }

        // -----------------------------------------------------
        // 6. RSI in momentum band (not weak, not overbought)
        // -----------------------------------------------------
        if (rsi >= 50 && rsi <= 65) {
            score += 10;
            result.getReasons().add("RSI in ideal momentum range (50–65)");
        }

        result.setPopScore(score);

        // -----------------------------------------------------
        // FINAL CLASSIFICATION
        // -----------------------------------------------------
        if (score >= 60) {
            result.setMomentumPop(true);

            if (rangePct < 2.0)
                result.setPopType("Squeeze Breakout");
            else if (price > sma20)
                result.setPopType("Trend Continuation");
            else
                result.setPopType("Momentum Pop");
        } else {
            result.setMomentumPop(false);
            result.setPopType("None");
        }
        s.setMomPop(result);
    }
}

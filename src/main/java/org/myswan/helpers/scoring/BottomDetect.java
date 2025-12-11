package org.myswan.helpers.scoring;

import org.myswan.model.collection.Stock;
import org.myswan.model.compute.BottomSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Objects;

@Component
public class BottomDetect {

    private static final Logger log = LoggerFactory.getLogger(BottomDetect.class);
    public BottomDetect() {
        super();
    }

    public void detectBottomSignal(Stock s, Stock history) {
        try {
            BottomSignal result = new BottomSignal();
            if(history == null) {
                s.setBottom(result);
                return;
            }
            int score = 0;

            double price = s.getPrice();
            double rsi = s.getRsi14();
            double ema9 = s.getEma9();
            double ema21 = s.getEma21();
            double ema20 = s.getEma20();
            double ema50 = s.getEma50();
            double prevHigh = history != null ? history.getHigh() : 0.0;
            double prevLow = history != null ? history.getLow() : 0.0;
            double todayLow = s.getLow();
            double todayHigh = s.getHigh();
            double macd = s.getMacd1226();
            double volume = s.getVolume();
            double avgVolume10 = s.getAvgVolume10D();
            double atr = s.getAtr14();

            if (result.getReasons() == null)
                result.setReasons(new ArrayList<>());

            //Condition 1 — RSI < 30 (preferably < 25)
            if (rsi < 25) {
                score++;
                result.getReasons().add("RSI extremely oversold (" + rsi + ")");
            }

            //Condition 2 — Price far below EMA20/EMA50 (mean reversion setup)
            //10–50% below
            double dev20 = ((ema20 - price) / ema20) * 100;
            double dev50 = ((ema50 - price) / ema50) * 100;
            if (dev20 > 10) {
                score++;
                result.getReasons().add("Price " + (int) dev20 + "% below EMA20");
            }
            if (dev50 > 20) {
                score++;
                result.getReasons().add("Price " + (int) dev50 + "% below EMA50");
            }

            //Condition 3 — Volume spike ≥ 2×–10× average
            double volSpike = volume / avgVolume10;
            if (volSpike >= 2) {
                score++;
                result.getReasons().add("Volume spike: " + String.format("%.2f", volSpike) + "× average");
            }
            //If ≥ 4× → add extra point
            if (volSpike >= 4) {
                score++;
                result.getReasons().add("Major capitulation volume (" + String.format("%.2f", volSpike) + "×)");
            }

            //Condition 4 — MACD Bullish Divergence
            //Histogram rising for 3+ periods OR
            //MACD turning upward from extreme negative
            if (macd > 0 || macd > Objects.requireNonNull(history).getMacd1226()) {
                score++;
                result.getReasons().add("MACD turning bullish");
            }

            //Condition 5 — Higher low + break above prior high
            boolean higherLow = todayLow > prevLow;
            boolean breakPrevHigh = price > prevHigh;
            if (higherLow) {
                score++;
                result.getReasons().add("Higher low (" + todayLow + " > " + prevLow + ")");
            }
            if (breakPrevHigh) {
                score++;
                result.getReasons().add("Breaking above previous high (" + price + " > " + prevHigh + ")");
            }

            //Condition 6 — EMA9 cross above EMA21 (trend shift signal)
            if (ema9 > ema21) {
                score++;
                result.getReasons().add("EMA9 bullish crossover above EMA21");
            }

            //Condition 7 — Reversal candle (hammer / long wick / engulfing)
            //Hammer / long wick:
            //Lower wick > 40% of candle height
            //Close > open (green candle)
            double candleSize = todayHigh - todayLow;
            double lowerWick = Math.min(s.getOpen(), s.getPrice()) - todayLow;

            if (candleSize > 0 && (lowerWick / candleSize) > 0.40 && s.getPrice() > s.getOpen()) {
                score++;
                result.getReasons().add("Bullish reversal candle (Hammer / long wick)");
            }

            //Bullish Engulfing:
            if (s.getPrice() > s.getPrevClose() && s.getOpen() < history.getOpen()) {
                score++;
                result.getReasons().add("Bullish engulfing pattern");
            }

            //Condition 8 — CLOSE above prior high → CONFIRMED BUY
            if (s.getPrice() > prevHigh) {
                score++;
                result.getReasons().add("Close above previous day's high → CONFIRMED reversal");
            }

            result.setConditionsMet(score);
            if (score >= 8) {
                result.setBottom(true);
                result.setStrength("Mega Bounce (like QBTS / SOXL / CONL)");
            } else if (score >= 5) {
                result.setBottom(true);
                result.setStrength("Strong Reversal");
            } else if (score >= 3) {
                result.setBottom(false);
                result.setStrength("Weak Signal");
            } else {
                result.setBottom(false);
                result.setStrength("None");
            }
            s.setBottom(result);
        } catch (Exception ex) {
            log.error("Error in BottomDetect.detectBottomSignal for ticker {}: ", s.getTicker(), ex);
        }
    }
}

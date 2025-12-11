package org.myswan.helpers.scoring;

import org.myswan.model.collection.Stock;
import org.myswan.model.compute.OversoldBounceSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class OversoldBounceDetect {

    private static final Logger log = LoggerFactory.getLogger(OversoldBounceDetect.class);
    public OversoldBounceDetect() {
        super();
    }

    public void detectOversoldBounce(Stock s, Stock history) {
        try {
            OversoldBounceSignal result = new OversoldBounceSignal();
            result.setReasons(new ArrayList<>());
            if(history == null) {
                s.setOversold(result);
                return;
            }
            int score = 0;

            double price = s.getPrice();
            double ema50 = s.getEma50();
            double rsi = s.getRsi14();
            int bottom = s.getBottom().getConditionsMet();

            // 1. Deep discount vs EMA50
            double ema50Gap = (ema50 - price) / ema50 * 100;

            if (ema50Gap >= 20 && ema50Gap <= 60) {
                score += 30;
                result.getReasons().add("Price " + (int) ema50Gap + "% below EMA50 (deep discount)");
            }

            // 2. RSI oversold
            if (rsi < 30) {
                score += 20;
                result.getReasons().add("RSI " + rsi + " is oversold");
            }
            if (rsi < 25) {
                score += 30;
                result.getReasons().add("RSI extremely oversold");
            }

            // 3. Strong Bottom conditions
            if (bottom >= 5) {
                score += 30;
                result.getReasons().add("Strong bottom reversal detected (" + bottom + " conditions)");
            }

            // 4. Spike & compression combination
            if (s.getSpike().getSpikeScore() >= 20) {
                score += 10;
                result.getReasons().add("Volume + candle compression before bounce");
            }

            // finalize
            result.setBounceScore(score);
            result.setOversoldBounce(score >= 60);

            if (score >= 80)
                result.setBounceType("Explosive Bounce");
            else if (score >= 60)
                result.setBounceType("Deep Oversold");
            else if (score >= 40)
                result.setBounceType("Oversold");
            else
                result.setBounceType("None");

            s.setOversold(result);
        } catch (Exception ex) {
            log.error("Error in OversoldBounceDetect.detectOversoldBounce for ticker {}", s.getTicker(), ex);
        }
    }
}

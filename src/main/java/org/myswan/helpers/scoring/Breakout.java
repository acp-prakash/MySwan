package org.myswan.helpers.scoring;

import org.myswan.model.Score;
import org.myswan.model.Stock;
import org.springframework.stereotype.Component;

@Component
public class Breakout {

    public Breakout() {
        super();
    }

    public void calculateScore(Stock stock) {

        double score = 0.0;
        StringBuilder reason = new StringBuilder();

        // 1. Price above EMA50 = entering breakout zone
        if (stock.getPrice() > stock.getEma50()) {
            score += 20;
            reason.append("Price above EMA50 (breakout zone) (+20); ");
        }

        // 2. Volume Surge Confirmation
        if (stock.getAvgVolume10D() > 0) {
            double volPct = ((stock.getVolume() - stock.getAvgVolume10D())
                    / stock.getAvgVolume10D()) * 100;

            if (volPct > 0) {
                double s = Math.min(volPct, 50.0);
                score += s;
                reason.append("Volume spike ")
                        .append(String.format("%.1f", volPct))
                                .append("% (+").append((int)s).append("); ");
            }
        }

        // 3. MACD Momentum
        if (stock.getMacd1226() > 0) {
            double s = stock.getMacd1226() * 5;
            score += s;
            reason.append("MACD momentum increasing (+")
                    .append(String.format("%.2f", s))
                            .append("); ");
        }

        // No signals?
        if (reason.isEmpty()) {
            reason.append("No breakout confirmation signals");
        }

        if(stock.getScore() == null)
            stock.setScore(new Score());

        stock.getScore().setBreakoutScore((int) score);
        stock.getScore().setBreakoutReason(reason.toString());
    }

    public void calculateScoreETF(Stock stock) {

        double score = 0.0;
        StringBuilder reason = new StringBuilder();

        // 1. Momentum scoring
        if (stock.getMomentum() > 0) {
            double s = stock.getMomentum() * 2;
            score += s;
            reason.append("Positive momentum (+").append(s).append("); ");
        }

        // 2. Volume Surge Scoring
        if (stock.getAvgVolume10D() > 0) {
            double volPct = ((stock.getVolume() - stock.getAvgVolume10D())
                    / stock.getAvgVolume10D()) * 100;

            if (volPct > 0) {
                double s = Math.min(volPct, 50.0);
                score += s;
                reason.append("Volume surge ").append(String.format("%.1f", volPct))
                        .append("% (+").append((int) s).append("); ");
            }
        }

        // 3. Price above VWAP
        if (stock.getPrice() > stock.getVwap()) {
            score += 10;
            reason.append("Price above VWAP (+10); ");
        }

        // 4. MACD scoring
        if (stock.getMacd1226() > 0) {
            double s = stock.getMacd1226() * 3;
            score += s;
            reason.append("MACD rising (+").append(String.format("%.2f", s)).append("); ");
        }

        // If no reasoning found
        if (reason.isEmpty()) {
            reason.append("No strong intraday signals");
        }

        if(stock.getScore() == null)
            stock.setScore(new Score());

        stock.getScore().setBreakoutScore((int) score);
        stock.getScore().setBreakoutReason(reason.toString());
    }
}

package org.myswan.helpers.scoring;

import org.myswan.model.compute.Score;
import org.myswan.model.Stock;
import org.springframework.stereotype.Component;

@Component
public class DayTrading {

    public DayTrading() {
        super();
    }

    public void calculateScore(Stock stock) {

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

        stock.getScore().setDayTradingScore((int) score);
        stock.getScore().setDayTradingReason(reason.toString());
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

        stock.getScore().setDayTradingScore((int) score);
        stock.getScore().setDayTradingReason(reason.toString());

    }
}

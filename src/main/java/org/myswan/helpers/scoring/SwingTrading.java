package org.myswan.helpers.scoring;

import org.myswan.model.compute.Score;
import org.myswan.model.collection.Stock;
import org.springframework.stereotype.Component;

@Component
public class SwingTrading {

    public SwingTrading() {
        super();
    }

    public void calculateScore(Stock stock) {

        double score = 0.0;
        StringBuilder reason = new StringBuilder();

        // 1. EMA Trend Alignment
        if (stock.getEma9() > stock.getEma21() && stock.getEma21() > stock.getEma50()) {
            score += 25;
            reason.append("EMA9 > EMA21 > EMA50 (strong bullish trend) (+25); ");
        }

        // 2. MACD Scoring
        if (stock.getMacd1226() > 0) {
            double s = stock.getMacd1226() * 5;
            score += s;
            reason.append("MACD bullish momentum (+")
                    .append(String.format("%.2f", s))
                    .append("); ");
        }

        // 3. Barchart Long-Term Trend
        if (stock.getRating().getBtLongRating() != null &&
                stock.getRating().getBtLongRating().toLowerCase().contains("buy")) {

            score += 20;
            reason.append("Barchart long-term BUY (+20); ");
        }

        // Default reasoning if nothing matched
        if (reason.isEmpty()) {
            reason.append("No strong swing trading signals");
        }

        if(stock.getScore() == null)
            stock.setScore(new Score());

        stock.getScore().setSwingTradingScore((int) score);
        stock.getScore().setSwingTradingReason(reason.toString());
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

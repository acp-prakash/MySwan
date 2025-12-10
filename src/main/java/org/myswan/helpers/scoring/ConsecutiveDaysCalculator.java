package org.myswan.helpers.scoring;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Stock;
import org.myswan.service.internal.StockService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
public class ConsecutiveDaysCalculator {

    private final StockService stockService;

    public ConsecutiveDaysCalculator(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * Calculate consecutive up/down days and their extremes based on stock history
     *
     * @param stock The stock to calculate for
     */
    public void calculateConsecutiveDays(Stock stock) {
        if (stock == null || stock.getTicker() == null) {
            return;
        }

        try {
            // Get last 30 days of history (enough to find patterns)
            LocalDate to = LocalDate.now();
			//LocalDate to = LocalDate.now().minusDays(1);
            LocalDate from = to.minusDays(30);

            List<Stock> history = stockService.getStockHistory(stock.getTicker(), from, to);

            if (history == null || history.isEmpty()) {
                log.debug("No history found for ticker: {}", stock.getTicker());
                stock.setUpDays(0);
                stock.setDownDays(0);
                stock.setUpHigh(0.0);
                stock.setDownLow(0.0);
                return;
            }


            // Sort by date ascending (oldest first) for consecutive calculation
            history.sort((a, b) -> a.getHistDate().compareTo(b.getHistDate()));

            //log.info("History Dates for upDown Ticker: {} - {} and {}", stock.getTicker(), history.getLast().getHistDate(), stock.getHistDate());
            if(!history.getLast().getHistDate().equals(stock.getHistDate())) {
                history.addLast(stock); // Include current day
                //log.info("History Dates for upDown Ticker: {} - {} and {}", stock.getTicker(), history.getLast().getHistDate(), stock.getHistDate());
            }

            // Find current consecutive streak
            int consecutiveUpDays = 0;
            int consecutiveDownDays = 0;
            double currentUpHigh = 0.0;
            double currentDownLow = Double.MAX_VALUE;

            boolean inUpStreak = false;
            boolean inDownStreak = false;

            // Start from most recent and work backwards
            for (int i = history.size() - 1; i >= 0; i--) {
                Stock histStock = history.get(i);
                double change = histStock.getChange();

                if (change > 0) {
                    // Up day
                    if (!inUpStreak && consecutiveDownDays == 0) {
                        // Start counting up days
                        inUpStreak = true;
                        inDownStreak = false;
                        consecutiveUpDays = 1;
                        currentUpHigh = histStock.getHigh();
                    } else if (inUpStreak) {
                        // Continue up streak
                        consecutiveUpDays++;
                        if (histStock.getHigh() > currentUpHigh) {
                            currentUpHigh = histStock.getHigh();
                        }
                    } else {
                        // Was in down streak, now up - stop counting
                        break;
                    }
                } else if (change < 0) {
                    // Down day
                    if (!inDownStreak && consecutiveUpDays == 0) {
                        // Start counting down days
                        inDownStreak = true;
                        inUpStreak = false;
                        consecutiveDownDays = 1;
                        currentDownLow = histStock.getLow();
                    } else if (inDownStreak) {
                        // Continue down streak
                        consecutiveDownDays++;
                        if (histStock.getLow() < currentDownLow) {
                            currentDownLow = histStock.getLow();
                        }
                    } else {
                        // Was in up streak, now down - stop counting
                        break;
                    }
                } else {
                    // Flat day (change = 0) - breaks the streak
                    break;
                }
            }

            // Set the calculated values
            stock.setUpDays(consecutiveUpDays);
            stock.setDownDays(consecutiveDownDays);
            stock.setUpHigh(inUpStreak ? currentUpHigh : 0.0);
            stock.setDownLow(inDownStreak && currentDownLow != Double.MAX_VALUE ? currentDownLow : 0.0);

            if (consecutiveUpDays > 0 || consecutiveDownDays > 0) {
                log.debug("Ticker {}: {} consecutive up days (high: {}), {} consecutive down days (low: {})",
                        stock.getTicker(), consecutiveUpDays, currentUpHigh, consecutiveDownDays,
                        currentDownLow != Double.MAX_VALUE ? currentDownLow : 0);
            }

        } catch (Exception e) {
            log.error("Error calculating consecutive days for ticker: {}", stock.getTicker(), e);
            stock.setUpDays(0);
            stock.setDownDays(0);
            stock.setUpHigh(0.0);
            stock.setDownLow(0.0);
        }
    }
}


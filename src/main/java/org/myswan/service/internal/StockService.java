package org.myswan.service.internal;

import org.myswan.model.dto.MLTrainingData;
import org.myswan.model.collection.Master;
import org.myswan.model.collection.Stock;
import org.myswan.model.collection.Pattern;
import org.myswan.model.dto.TickerGroupDTO;
import org.myswan.repository.StockRepository;
import org.myswan.service.external.vo.TradingViewVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    private final StockRepository repository;
    private final MongoTemplate mongoTemplate;

    public StockService(StockRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Bulk delete existing stocks by tickers and insert new ones
     */
    public void replaceStocks(List<Stock> stockList) {
        try {
            if (stockList == null || stockList.isEmpty()) {
                log.warn("No Stocks to insert");
                return;
            }

            // Extract all tickers to delete
            List<String> tickers = stockList.stream()
                    .map(Stock::getTicker)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            // Delete existing stocks with these tickers
            if (!tickers.isEmpty()) {
                Query deleteQuery = Query.query(Criteria.where("ticker").in(tickers));
                long deletedCount = mongoTemplate.remove(deleteQuery, Stock.class).getDeletedCount();
                log.info("Deleted {} existing stocks records", deletedCount);
            }

            // Insert all new stocks
            mongoTemplate.insert(stockList, Stock.class);
            log.info("Inserted {} new stocks records", stockList.size());
        } catch (Exception e) {
            log.error("Failed to bulk insert stocks", e);
        }
    }

    public void syncStockHistory() {
        List<Stock> stocks = list();
        if(stocks != null && !stocks.isEmpty()) {
            deleteHistoryByDate(stocks.getFirst().getHistDate());
            stocks.forEach(stock -> {
                stock.setId(null);
            });
            mongoTemplate.insert(stocks, "stockHistory");
        }
    }

    public void deleteHistoryByDate(LocalDate histDate) {
        Query query = Query.query(Criteria.where("histDate").is(histDate));
        mongoTemplate.remove(query, "stockHistory");
    }

    public Optional<Stock> getByTicker(String ticker) {
        try {
            return repository.findById(ticker);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Stock create(Stock stock) {
        return repository.save(stock);
    }

    public Stock update(String ticker, Stock stock) {
        stock.setTicker(ticker);
        return repository.save(stock);
    }

    public void delete(String ticker) {
        repository.deleteById(ticker);
        deleteHistoryByTicker(ticker);
    }

    public boolean exists(String ticker) {
        return repository.existsById(ticker);
    }

    public List<Stock> list() {
        try {
            List<Stock> all = repository.findAll();
            if (all != null && !all.isEmpty()) return all;
        } catch (Exception ignored) {
            // fall through to empty result
        }

        // Return empty list if no stocks found in DB
        return new ArrayList<>();
    }



    public void deleteHistoryByTicker(String ticker) {
        Query query = Query.query(Criteria.where("ticker").is(ticker));
        mongoTemplate.remove(query, "stockHistory");
    }

    public void deleteHistoryByTickerAndHistDate(String ticker, LocalDate histDate) {
            Query query = Query.query(
                    Criteria.where("ticker").is(ticker)
                            .and("histDate").is(histDate)
            );
        mongoTemplate.remove(query, "stockHistory");
    }

    public void updateTradingView(List<TradingViewVO> tvList) {
        if (tvList == null || tvList.isEmpty()) {
            log.info("TradingView update skipped: empty list");
            return;
        }
        // Load all existing stocks once
        List<Stock> existList = list();

        // Create a map for fast lookup by ticker
        Map<String, Stock> stockMap = existList.stream()
                .filter(s -> s.getTicker() != null && !s.getTicker().isBlank())
                .collect(Collectors.toMap(Stock::getTicker, s -> s, (a, b) -> a));

        List<Stock> toSave = new ArrayList<>(tvList.size());

        // Loop through TradingView data and update from existList map
        for (TradingViewVO vo : tvList) {
            if (vo.getTicker() == null || vo.getTicker().isBlank()) continue;

            // Get existing stock from map instead of repository
            Stock existing = stockMap.get(vo.getTicker());
            if (existing == null || existing.getTicker() == null || existing.getTicker().isBlank()) {
                continue;
            }

            // Update stock with TradingView data
            existing.setPrice(vo.getPrice());
            existing.setType(vo.getType());
            existing.setOpen(vo.getOpen());
            existing.setHigh(vo.getHigh());
            existing.setLow(vo.getLow());
            existing.setChange(vo.getChange());
            existing.setSma9(vo.getSma9());
            existing.setSma20(vo.getSma20());
            existing.setSma21(vo.getSma20());
            existing.setSma50(vo.getSma50());
            existing.setSma100(vo.getSma100());
            existing.setSma200(vo.getSma200());
            existing.setEma9(vo.getEma9());
            existing.setEma20(vo.getEma20());
            existing.setEma21(vo.getEma21());
            existing.setEma50(vo.getEma50());
            existing.setEma100(vo.getEma100());
            existing.setEma200(vo.getEma200());
            existing.setMacd1226(vo.getMacd1226());
            existing.setRsi14(vo.getRsi14());
            existing.setAtr14(vo.getAtr14());
            existing.setMomentum(vo.getMomentum());
            existing.setVolume(vo.getVolume());
            existing.setVolumeChange(vo.getVolumeChange());
            existing.setAvgVolume10D(vo.getAvgVolume10D());
            existing.setVwap(vo.getVwap());
            existing.getRating().setTradingViewMARating(vo.getMaRating());
            existing.getRating().setTradingViewTechRating(vo.getTechRating());
            existing.getRating().setTradingViewAnalystsRating(vo.getAnalystsRating());
            toSave.add(existing);
        }
        replaceStocks(toSave);
        log.info("TradingView update completed. Saved {} stocks", toSave.size());
    }

    public List<Stock> getStockHistory(String ticker, LocalDate from, LocalDate to) {
        if (ticker == null || ticker.isBlank()) return new ArrayList<>();

        // Build criteria for ticker
        Criteria criteria = Criteria.where("ticker").is(ticker);

        // Build date range criteria (combine gte and lte in single criteria)
        if (from != null && to != null) {
            criteria.and("histDate").gte(from).lte(to);
        } else if (from != null) {
            criteria.and("histDate").gte(from);
        } else if (to != null) {
            criteria.and("histDate").lte(to);
        }

        Query query = Query.query(criteria);
        query.with(Sort.by(Sort.Direction.DESC, "histDate"));

        try {
            return mongoTemplate.find(query, Stock.class, "stockHistory");
        } catch (Exception e) {
            log.warn("Failed to load history for {}: {}", ticker, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Stock> getHistoryByDate(LocalDate histDate) {
        if (histDate == null) return new ArrayList<>();
        Query query = Query.query(Criteria.where("histDate").is(histDate));
        query.with(Sort.by(Sort.Direction.ASC, "ticker"));
        try {
            return mongoTemplate.find(query, Stock.class, "stockHistory");
        } catch (Exception e) {
            log.warn("Failed to load history for date {}: {}", histDate, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Enrich stocks with their patterns.
     * This populates the transient patterns field in Stock objects.
     *
     * @param stocks List of stocks to enrich
     * @return Same list with patterns populated
     */
    public List<Stock> enrichWithPatterns(List<Stock> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return stocks;
        }

        try {
            // Get unique tickers
            Set<String> tickers = new java.util.HashSet<>();
            stocks.forEach(stock -> {
                if (stock.getTicker() != null) {
                    tickers.add(stock.getTicker());
                }
            });

            if (tickers.isEmpty()) {
                return stocks;
            }

            // Fetch all patterns for these tickers in one query
            Query query = new Query(Criteria.where("ticker").in(tickers));
            List<Pattern> allPatterns = mongoTemplate.find(query, Pattern.class, "pattern");

            // Group patterns by ticker
            java.util.Map<String, List<Pattern>> patternsByTicker = new java.util.HashMap<>();
            allPatterns.forEach(pattern -> {
                patternsByTicker.computeIfAbsent(pattern.getTicker(), k -> new ArrayList<>())
                    .add(pattern);
            });

            // Enrich each stock with its patterns
            stocks.forEach(stock -> {
                List<Pattern> stockPatterns = patternsByTicker.get(stock.getTicker());
                stock.setPatterns(stockPatterns != null ? stockPatterns : new ArrayList<>());
            });

            log.debug("Enriched {} stocks with patterns", stocks.size());
            return stocks;

        } catch (Exception e) {
            log.error("Error enriching stocks with patterns", e);
            // Return stocks without patterns rather than failing
            return stocks;
        }
    }

    /**
     * Get tickers grouped with their related tickers (ETFs) whose description contains the main ticker.
     * Rules:
     * 1. Main ticker type CANNOT be "ETF" (can be STOCK, DR, or anything except ETF)
     * 2. Related ticker type MUST be "ETF" (only ETFs can be related tickers)
     * 3. Case-sensitive matching: " TICKER " (exact case with spaces)
     * Returns flattened list where main ticker row repeats for each related ticker.
     *
     * @return List of TickerGroupDTO with main ticker and ONE related ticker per row
     */
    public List<TickerGroupDTO> getGroupedTickers() {
        try {
            // Get all masters
            List<Master> allMasters = mongoTemplate.findAll(Master.class, "master");
            if (allMasters == null || allMasters.isEmpty()) {
                return new ArrayList<>();
            }

            // Get all stocks for price data
            List<Stock> allStocks = list();
            Map<String, Stock> stockMap = allStocks.stream()
                    .collect(Collectors.toMap(Stock::getTicker, s -> s, (a, b) -> a));

            // Flattened list - one row per main+related pair
            List<TickerGroupDTO> flattenedGroups = new ArrayList<>();

            for (Master mainMaster : allMasters) {
                String mainTicker = mainMaster.getTicker();
                String mainType = mainMaster.getType();

                if (mainTicker == null || mainTicker.isEmpty()) continue;

                // Rule 1: Main ticker type CANNOT be "ETF" (only STOCK/DR/etc can be main tickers)
                if ("ETF".equalsIgnoreCase(mainType)) {
                    continue; // Skip ETFs as main tickers
                }

                Stock mainStock = stockMap.get(mainTicker);

                // Find all tickers whose description contains this ticker with spaces around it
                for (Master relatedMaster : allMasters) {
                    if (relatedMaster.getTicker().equals(mainTicker)) continue;

                    String relatedType = relatedMaster.getType();

                    // Rule 2: Related ticker MUST be ETF type
                    if (!"ETF".equalsIgnoreCase(relatedType)) {
                        continue; // Skip if related ticker is not ETF
                    }

                    String relatedName = relatedMaster.getName();
                    if (relatedName != null) {
                        // Rule 3: Case-sensitive exact match with spaces: " TICKER "
                        String paddedRelatedName = " " + relatedName + " ";
                        String searchPattern = " " + mainTicker + " ";

                        if (paddedRelatedName.contains(searchPattern)) {
                            // Create a flattened row for this main+related pair
                            TickerGroupDTO group = new TickerGroupDTO();

                            // Set main ticker data
                            group.setMainTicker(mainTicker);
                            group.setMainName(mainMaster.getName());
                            group.setMainType(mainMaster.getType());

                            if (mainStock != null) {
                                group.setMainPrice(mainStock.getPrice());
                                group.setMainChange(mainStock.getChange());
                                group.setMainHigh(mainStock.getHigh());
                                group.setMainLow(mainStock.getLow());
                                group.setMainVolume(mainStock.getVolume());
                            }

                            // Set related ticker data (single item)
                            Stock relatedStock = stockMap.get(relatedMaster.getTicker());
                            TickerGroupDTO.RelatedTickerDTO related = new TickerGroupDTO.RelatedTickerDTO();
                            related.setTicker(relatedMaster.getTicker());
                            related.setName(relatedMaster.getName());
                            related.setType(relatedMaster.getType());

                            // Determine leverage type: Short if description contains "short", "bear", or "inverse" (case-insensitive)
                            String relatedNameLower = relatedName.toLowerCase();
                            if (relatedNameLower.contains("short") || relatedNameLower.contains("bear") || relatedNameLower.contains("inverse")) {
                                related.setLeverageType("Short");
                            } else {
                                related.setLeverageType("Long");
                            }

                            if (relatedStock != null) {
                                related.setPrice(relatedStock.getPrice());
                                related.setChange(relatedStock.getChange());
                                related.setHigh(relatedStock.getHigh());
                                related.setLow(relatedStock.getLow());
                                related.setVolume(relatedStock.getVolume());
                            }

                            // Add single related ticker to list
                            List<TickerGroupDTO.RelatedTickerDTO> relatedList = new ArrayList<>();
                            relatedList.add(related);
                            group.setRelatedTickers(relatedList);

                            flattenedGroups.add(group);
                        }
                    }
                }
            }

            log.info("Found {} flattened ticker group rows (non-ETF main tickers with ETF related tickers)", flattenedGroups.size());
            return flattenedGroups;

        } catch (Exception e) {
            log.error("Error getting grouped tickers", e);
            return new ArrayList<>();
        }
    }


    // ========== ML EXPORT METHODS ==========

    public List<Stock> getStocksByDateRange(LocalDate from, LocalDate to) {
        Query query = Query.query(
                Criteria.where("histDate").gte(from).lte(to)
        );
        query.with(Sort.by(Sort.Direction.ASC, "histDate"));
        return mongoTemplate.find(query, Stock.class, "stockHistory");
    }

    public List<Stock> getStocksByTickersAndDateRange(List<String> tickers, LocalDate from, LocalDate to) {
        Query query = Query.query(
                Criteria.where("ticker").in(tickers)
                        .and("histDate").gte(from).lte(to)
        );
        query.with(Sort.by(Sort.Direction.ASC, "ticker", "histDate"));
        return mongoTemplate.find(query, Stock.class, "stockHistory");
    }

    public List<MLTrainingData> convertToMLFormat(List<Stock> historicalData) {
        log.info("Converting {} stock records to ML format", historicalData.size());

        // Group by ticker to calculate future returns
        Map<String, List<Stock>> byTicker = historicalData.stream()
                .collect(Collectors.groupingBy(Stock::getTicker));

        List<MLTrainingData> mlDataList = new ArrayList<>();

        for (Map.Entry<String, List<Stock>> entry : byTicker.entrySet()) {
            String ticker = entry.getKey();
            List<Stock> tickerHistory = entry.getValue();

            // Sort by date
            tickerHistory.sort(Comparator.comparing(Stock::getHistDate));

            for (int i = 0; i < tickerHistory.size(); i++) {
                Stock current = tickerHistory.get(i);

                // Skip if missing critical data
                if (current.getPrice() == 0) continue;

                // Calculate future returns (if we have future data)
                Double return1d = null, return3d = null, return7d = null;
                Boolean hitTarget = null;
                Double maxDrawdown7d = null;

                if (i + 1 < tickerHistory.size()) {
                    Stock next1d = tickerHistory.get(i + 1);
                    if (next1d.getPrice() > 0) {
                        return1d = (next1d.getPrice() - current.getPrice()) / current.getPrice();
                    }
                }

                if (i + 3 < tickerHistory.size()) {
                    Stock next3d = tickerHistory.get(i + 3);
                    if (next3d.getPrice() > 0) {
                        return3d = (next3d.getPrice() - current.getPrice()) / current.getPrice();
                        hitTarget = return3d >= 0.02; // 2% gain
                    }
                }

                if (i + 7 < tickerHistory.size()) {
                    Stock next7d = tickerHistory.get(i + 7);
                    if (next7d.getPrice() > 0) {
                        return7d = (next7d.getPrice() - current.getPrice()) / current.getPrice();

                        // Calculate max drawdown in next 7 days
                        double minPrice = current.getPrice();
                        for (int j = i + 1; j <= Math.min(i + 7, tickerHistory.size() - 1); j++) {
                            if (tickerHistory.get(j).getPrice() > 0) {
                                minPrice = Math.min(minPrice, tickerHistory.get(j).getPrice());
                            }
                        }
                        maxDrawdown7d = (minPrice - current.getPrice()) / current.getPrice();
                    }
                }

                // Build ML training data
                MLTrainingData mlData = MLTrainingData.builder()
                        .ticker(current.getTicker())
                        .date(current.getHistDate())
                        .price(current.getPrice())
                        .open(current.getOpen())
                        .high(current.getHigh())
                        .low(current.getLow())
                        .change(current.getChange())
                        .volume(current.getVolume())
                        .volumeChange(current.getVolumeChange())
                        .overallScore(current.getScore() != null ? current.getScore().getOverallScore() : 0)
                        .pickScore(current.getDailyRank() != null ? current.getDailyRank().getPickScore() : 0)
                        .safetyRank(current.getDailyRank() != null ? current.getDailyRank().getSafetyRank() : 0)
                        .finalRank(current.getDailyRank() != null ? current.getDailyRank().getFinalRank() : 0)
                        .allocation(current.getDailyRank() != null ? current.getDailyRank().getAllocation() : 0)
                        .dayTradingScore(current.getScore() != null ? current.getScore().getDayTradingScore() : 0)
                        .swingTradingScore(current.getScore() != null ? current.getScore().getSwingTradingScore() : 0)
                        .reversalScore(current.getScore() != null ? current.getScore().getReversalScore() : 0)
                        .breakoutScore(current.getScore() != null ? current.getScore().getBreakoutScore() : 0)
                        .patternScore(current.getScore() != null ? current.getScore().getPatternScore() : 0)
                        .rsi14(current.getRsi14())
                        .macd1226(current.getMacd1226())
                        .atr14(current.getAtr14())
                        .momentum(current.getMomentum())
                        .sma9(current.getSma9())
                        .sma20(current.getSma20())
                        .sma50(current.getSma50())
                        .sma200(current.getSma200())
                        .ema9(current.getEma9())
                        .ema20(current.getEma20())
                        .ema50(current.getEma50())
                        .ema200(current.getEma200())
                        .vwap(current.getVwap())
                        .priceChg5D(current.getPriceChg5D())
                        .priceChg10D(current.getPriceChg10D())
                        .priceChg20D(current.getPriceChg20D())
                        .low52(current.getLow52())
                        .high52(current.getHigh52())
                        .noOfLongPatterns(current.getNoOfLongPatterns())
                        .noOfShortPatterns(current.getNoOfShortPatterns())
                        .signal(current.getScore() != null ? current.getScore().getSignal() : null)
                        .signalDays(current.getScore() != null ? current.getScore().getSignalDays() : 0)
                        .upDays(current.getUpDays())
                        .downDays(current.getDownDays())
                        .upHigh(current.getUpHigh())
                        .downLow(current.getDownLow())
                        .bottomConditionsMet(current.getBottom() != null ? current.getBottom().getConditionsMet() : null)
                        .bottomStrength(current.getBottom() != null ? current.getBottom().getStrength() : null)
                        .spikeScore(current.getSpike() != null ? current.getSpike().getSpikeScore() : null)
                        .spikeType(current.getSpike() != null ? current.getSpike().getSpikeType() : null)
                        // Target variables
                        .return1d(return1d)
                        .return3d(return3d)
                        .return7d(return7d)
                        .hitTarget(hitTarget)
                        .maxDrawdown7d(maxDrawdown7d)
                        .build();

                mlDataList.add(mlData);
            }
        }

        log.info("Converted to {} ML training records", mlDataList.size());
        return mlDataList;
    }
}

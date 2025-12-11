package org.myswan.service.internal;

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
}

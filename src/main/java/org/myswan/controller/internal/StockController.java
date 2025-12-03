package org.myswan.controller.internal;

import org.myswan.model.Stock;
import org.myswan.model.dto.TickerGroupDTO;
import org.myswan.service.internal.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Stock", description = "Stock management APIs - CRUD operations and history")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }


    @Operation(
        summary = "Get all stocks",
        description = "Returns a list of all stocks with their details including price, ratings, scores, and signals"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved list of stocks")
    @GetMapping("/stock/list")
    public ResponseEntity<List<Stock>> getAllStocks() {
        return ResponseEntity.ok(stockService.list());
    }

    @Operation(
        summary = "Get all stocks with patterns",
        description = "Returns a list of all stocks enriched with their associated patterns from the pattern collection"
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved stocks with patterns")
    @GetMapping("/stock/list-with-patterns")
    public ResponseEntity<List<Stock>> getAllStocksWithPatterns() {
        List<Stock> stocks = stockService.list();
        stocks = stockService.enrichWithPatterns(stocks);
        return ResponseEntity.ok(stocks);
    }

    @Operation(
        summary = "Get stock by ticker",
        description = "Returns details for a specific stock identified by ticker symbol"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Stock found"),
        @ApiResponse(responseCode = "404", description = "Stock not found")
    })
    @GetMapping("/stock/{ticker}")
    public ResponseEntity<Stock> getStock(
        @Parameter(description = "Stock ticker symbol (e.g., AAPL, MSFT)")
        @PathVariable String ticker
    ) {
        return stockService.getByTicker(ticker).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/stock/add")
    public ResponseEntity<Stock> addStock(@RequestBody Stock stock) {
        if (stock == null || stock.getTicker() == null) return ResponseEntity.badRequest().build();
        Stock saved = stockService.create(stock);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/stock/{ticker}")
    public ResponseEntity<Stock> updateStock(@PathVariable String ticker, @RequestBody Stock stock) {
        if (!stockService.exists(ticker)) return ResponseEntity.notFound().build();
        Stock saved = stockService.update(ticker, stock);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/stock/{ticker:.+}")
    public ResponseEntity<Void> deleteStock(@PathVariable String ticker) {
        if (!stockService.exists(ticker)) return ResponseEntity.notFound().build();
        stockService.delete(ticker);
        return ResponseEntity.noContent().build();
    }

    // Backwards-compatible endpoint: /api/master/delete/{ticker}
    @DeleteMapping("/stock/delete/{ticker:.+}")
    public ResponseEntity<Void> deleteStockLegacy(@PathVariable String ticker) {
        // Delegate to the canonical delete behavior
        return deleteStock(ticker);
    }

    @GetMapping("/stock/history/{ticker:.+}")
    public ResponseEntity<List<Stock>> getStockHistory(
            @PathVariable String ticker,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<Stock> history = stockService.getStockHistory(ticker, from, to);
        return ResponseEntity.ok(history);
    }

    @Operation(
        summary = "Get grouped tickers with related tickers/ETFs",
        description = "Returns tickers grouped with their related tickers (ETFs) whose description contains the main ticker. " +
                     "For example, CLSK is grouped with CLSX (ETF containing CLSK in description)."
    )
    @ApiResponse(responseCode = "200", description = "Successfully retrieved grouped tickers")
    @GetMapping("/stock/grouped-tickers")
    public ResponseEntity<List<TickerGroupDTO>> getGroupedTickers() {
        List<TickerGroupDTO> groups = stockService.getGroupedTickers();
        return ResponseEntity.ok(groups);
    }
}
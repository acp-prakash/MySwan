package org.myswan.controller.internal;

import org.myswan.model.Stock;
import org.myswan.service.internal.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }


    @GetMapping("/stock/list")
    public ResponseEntity<List<Stock>> getAllStocks() {
        return ResponseEntity.ok(stockService.list());
    }

    @GetMapping("/stock/{ticker}")
    public ResponseEntity<Stock> getStock(@PathVariable String ticker) {
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
}
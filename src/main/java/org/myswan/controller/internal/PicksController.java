package org.myswan.controller.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Picks;
import org.myswan.service.internal.PicksService;
import org.myswan.service.internal.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
public class PicksController {

    private final PicksService picksService;
    private final StockService stockService;

    public PicksController(PicksService picksService, StockService stockservice) {
        this.picksService = picksService;
        this.stockService = stockservice;
    }

    @GetMapping("/picks/list")
    public ResponseEntity<List<Picks>> getAllPicks() {
        return ResponseEntity.ok(picksService.list());
    }

    @GetMapping("/picks/{ticker}")
    public ResponseEntity<List<Picks>> getPicksByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(picksService.listByTicker(ticker));
    }

    @PostMapping("/picks/create")
    public ResponseEntity<Picks> createPick(@RequestBody Picks pick) {
        log.info("Creating new pick for ticker: {}", pick.getTicker());
        Picks saved = picksService.save(pick);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/picks/update")
    public ResponseEntity<Picks> updatePick(@RequestBody Picks pick) {
        log.info("Updating pick: {} for ticker: {}", pick.getId(), pick.getTicker());
        Picks updated = picksService.update(pick);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/picks/delete/{id}")
    public ResponseEntity<String> deletePick(@PathVariable String id) {
        log.info("Deleting pick: {}", id);
        picksService.delete(id);
        return ResponseEntity.ok("Pick deleted successfully");
    }

    @GetMapping("/picks/byId/{id}")
    public ResponseEntity<Picks> getPickById(@PathVariable String id) {
        Picks pick = picksService.findById(id);
        if (pick != null) {
            return ResponseEntity.ok(pick);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/picks/sync")
    public ResponseEntity<String> syncPicksWithStockData() {
        log.info("Syncing picks with current stock data...");
        try {
            picksService.syncWithStockData(stockService.list());
            return ResponseEntity.ok("Picks synced successfully with current stock data");
        } catch (Exception e) {
            log.error("Error syncing picks", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to sync picks: " + e.getMessage());
        }
    }

    @PostMapping("/picks/sync-history")
    public ResponseEntity<String> syncPicksHistory() {
        log.info("Syncing picks to history...");
        try {
            picksService.syncPicksHistory();
            return ResponseEntity.ok("Picks history synced successfully");
        } catch (Exception e) {
            log.error("Error syncing picks history", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to sync picks history: " + e.getMessage());
        }
    }

    @GetMapping("/picks/history/{ticker}")
    public ResponseEntity<List<Picks>> getPicksHistory(@PathVariable String ticker) {
        log.info("Getting picks history for ticker: {}", ticker);
        return ResponseEntity.ok(picksService.getPicksHistory(ticker));
    }
}


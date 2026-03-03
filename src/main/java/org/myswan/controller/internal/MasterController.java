package org.myswan.controller.internal;

import org.myswan.model.collection.Master;
import org.myswan.service.internal.MasterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class MasterController {

    private final MasterService masterService;

    public MasterController(MasterService masterService) {
        this.masterService = masterService;
    }

    @GetMapping("/master/{ticker:.+}")
    public ResponseEntity<Master> getMaster(@PathVariable String ticker) {
        return masterService.getByTicker(ticker).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/master/add")
    public ResponseEntity<Master> addMaster(@RequestBody Master master) {
        if (master == null || master.getTicker() == null) return ResponseEntity.badRequest().build();
        Master saved = masterService.create(master);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/master/{ticker:.+}")
    public ResponseEntity<Void> deleteMaster(@PathVariable String ticker) {
        if (!masterService.exists(ticker)) return ResponseEntity.notFound().build();
        masterService.delete(ticker);
        return ResponseEntity.noContent().build();
    }

    // Backwards-compatible endpoint: /api/master/delete/{ticker}
    @DeleteMapping("/master/delete/{ticker:.+}")
    public ResponseEntity<Void> deleteMasterLegacy(@PathVariable String ticker) {
        // Delegate to the canonical delete behavior
        return deleteMaster(ticker);
    }

    /**
     * Delete a ticker from all collections
     */
    @DeleteMapping("/master/delete-all/{ticker:.+}")
    public ResponseEntity<String> deleteFromAllCollections(@PathVariable String ticker) {
        if (!masterService.exists(ticker)) {
            return ResponseEntity.notFound().build();
        }
        try {
            masterService.deleteFromAllCollections(ticker);
            return ResponseEntity.ok("Ticker " + ticker + " deleted from all collections successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error deleting ticker: " + e.getMessage());
        }
    }

    /**
     * Delete a ticker from specific collections
     * Expected body: ["master", "stock", "stockHistory", ...]
     */
    @DeleteMapping("/master/delete-custom/{ticker:.+}")
    public ResponseEntity<String> deleteFromCustomCollections(
            @PathVariable String ticker,
            @RequestBody List<String> collections) {
        if (!masterService.exists(ticker)) {
            return ResponseEntity.notFound().build();
        }
        try {
            masterService.deleteFromSpecificCollections(ticker, collections);
            return ResponseEntity.ok("Ticker " + ticker + " deleted from " + collections.size() + " collection(s) successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error deleting ticker: " + e.getMessage());
        }
    }

    /**
     * Bulk delete multiple tickers from all collections
     * Expected body: ["AAPL", "TSLA", "MSFT", ...]
     */
    @DeleteMapping("/master/delete-bulk")
    public ResponseEntity<String> deleteBulkFromAllCollections(@RequestBody List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return ResponseEntity.badRequest().body("No tickers provided");
        }
        try {
            int successCount = 0;
            int failCount = 0;
            StringBuilder errors = new StringBuilder();

            for (String ticker : tickers) {
                try {
                    if (masterService.exists(ticker)) {
                        masterService.deleteFromAllCollections(ticker);
                        successCount++;
                    } else {
                        failCount++;
                        errors.append(ticker).append(" (not found), ");
                    }
                } catch (Exception e) {
                    failCount++;
                    errors.append(ticker).append(" (error: ").append(e.getMessage()).append("), ");
                }
            }

            String message = String.format("Deleted %d ticker(s) successfully", successCount);
            if (failCount > 0) {
                message += String.format(", %d failed: %s", failCount, errors);
            }
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Bulk delete failed: " + e.getMessage());
        }
    }

    @PutMapping("/master/{ticker:.+}")
    public ResponseEntity<Master> updateMaster(@PathVariable String ticker, @RequestBody Master master) {
        if (!masterService.exists(ticker)) return ResponseEntity.notFound().build();
        Master saved = masterService.update(ticker, master);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/master/list")
    public ResponseEntity<List<Master>> getAllMaster() {
        return ResponseEntity.ok(masterService.list());
    }

    /**
     * Get all masters with current price from stock collection
     */
    @GetMapping("/master/list-with-price")
    public ResponseEntity<List<Master>> getAllMasterWithPrice() {
        return ResponseEntity.ok(masterService.listWithCurrentPrice());
    }

    @PatchMapping("/master/{ticker:.+}/etrade-pattern")
    public ResponseEntity<Master> updateEtradePatternLookup(@PathVariable String ticker, @RequestParam boolean enabled) {
        Master master = masterService.getByTicker(ticker).orElse(null);
        if (master == null) return ResponseEntity.notFound().build();

        master.setEtradePatternLookup(enabled);
        Master saved = masterService.update(ticker, master);
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/master/{ticker:.+}/my-favorite")
    public ResponseEntity<Master> updateMyFavorite(@PathVariable String ticker, @RequestParam String value) {
        Master master = masterService.getByTicker(ticker).orElse(null);
        if (master == null) return ResponseEntity.notFound().build();

        master.setMyFavorite(value);
        Master saved = masterService.update(ticker, master);
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/master/{ticker:.+}/my-daytrade")
    public ResponseEntity<Master> updateMyDayTrade(@PathVariable String ticker, @RequestParam String value) {
        Master master = masterService.getByTicker(ticker).orElse(null);
        if (master == null) return ResponseEntity.notFound().build();

        master.setMyDayTrade(value);
        Master saved = masterService.update(ticker, master);
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/master/{ticker:.+}/etf2x")
    public ResponseEntity<Master> updateEtf2X(@PathVariable String ticker, @RequestParam String value) {
        Master master = masterService.getByTicker(ticker).orElse(null);
        if (master == null) return ResponseEntity.notFound().build();

        master.setEtf2X(value);
        Master saved = masterService.update(ticker, master);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/master/etrade-pattern/enable-all")
    public ResponseEntity<String> enableAllEtradePatternLookup() {
        List<Master> allMasters = masterService.list();
        int updated = 0;

        for (Master master : allMasters) {
            master.setEtradePatternLookup(true);
            masterService.update(master.getTicker(), master);
            updated++;
        }

        return ResponseEntity.ok("Updated " + updated + " master records to Y (enabled)");
    }
}
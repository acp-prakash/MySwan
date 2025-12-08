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

    @PatchMapping("/master/{ticker:.+}/etrade-pattern")
    public ResponseEntity<Master> updateEtradePatternLookup(@PathVariable String ticker, @RequestParam boolean enabled) {
        Master master = masterService.getByTicker(ticker).orElse(null);
        if (master == null) return ResponseEntity.notFound().build();

        master.setEtradePatternLookup(enabled);
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
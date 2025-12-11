package org.myswan.controller.internal;

import org.myswan.service.internal.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/syncup/history/all")
    public ResponseEntity<String> syncAllHistory() {
        return ResponseEntity.ok(syncService.syncAllHistory());
    }

    @PostMapping("/syncup/history/futures")
    public ResponseEntity<String> syncFutureHistory() {
        return ResponseEntity.ok(syncService.syncFutureHistory());
    }

    @PostMapping("/syncup/history/stock")
    public ResponseEntity<String> syncStockHistory() {
        return ResponseEntity.ok(syncService.syncStockHistory());
    }

    @PostMapping("/syncup/history/pattern")
    public ResponseEntity<String> syncPatternHistory() {
        return ResponseEntity.ok(syncService.syncPatternHistory());
    }

    @PostMapping("/syncup/history/picks")
    public ResponseEntity<String> syncPicksHistory() {
        return ResponseEntity.ok(syncService.syncPicksHistory());
    }

    @PostMapping("/syncup/history/guaranteedPicks")
    public ResponseEntity<String> syncGuranteedPicksHistory() {
        return ResponseEntity.ok(syncService.syncGuaranteedPicksHistory());
    }
}
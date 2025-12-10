package org.myswan.controller.internal;

import org.myswan.service.internal.SyncupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SyncupController {

    private final SyncupService syncupService;

    public SyncupController(SyncupService syncupService) {
        this.syncupService = syncupService;
    }

    @PostMapping("/syncup/history/all")
    public ResponseEntity<String> syncupAllHistory() {
        return ResponseEntity.ok(syncupService.syncupAllHistory());
    }

    @PostMapping("/syncup/history/futures")
    public ResponseEntity<String> syncupFutureHistory() {
        return ResponseEntity.ok(syncupService.syncupFutureHistory());
    }

    @PostMapping("/syncup/history/stock")
    public ResponseEntity<String> syncupStockHistory() {
        return ResponseEntity.ok(syncupService.syncupStockHistory());
    }

    @PostMapping("/syncup/history/pattern")
    public ResponseEntity<String> syncupPatternHistory() {
        return ResponseEntity.ok(syncupService.syncupPatternHistory());
    }

    @PostMapping("/syncup/history/picks")
    public ResponseEntity<String> syncupPicksHistory() {
        return ResponseEntity.ok(syncupService.syncPicksHistory());
    }

    @PostMapping("/syncup/history/guaranteedPicks")
    public ResponseEntity<String> syncupGuranteedPicksHistory() {
        return ResponseEntity.ok(syncupService.syncGuaranteedPicksHistory());
    }
}
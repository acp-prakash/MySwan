package org.myswan.controller.internal;

import org.myswan.model.Tactic;
import org.myswan.service.internal.TacticService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TacticController {

    private final TacticService tacticService;

    public TacticController(TacticService tacticService) {
        this.tacticService = tacticService;
    }

    @GetMapping("/tactic/{id:.+}")
    public ResponseEntity<Tactic> getTactics(@PathVariable String id) {
        return tacticService.getById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/tactic/add")
    public ResponseEntity<Tactic> addTactic(@RequestBody Tactic tactic) {
        if (tactic == null || tactic.getId() == null) return ResponseEntity.badRequest().build();
        Tactic saved = tacticService.create(tactic);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/tactic/{ticker:.+}")
    public ResponseEntity<Void> deleteTactic(@PathVariable String ticker) {
        if (!tacticService.exists(ticker)) return ResponseEntity.notFound().build();
        tacticService.delete(ticker);
        return ResponseEntity.noContent().build();
    }

    // Backwards-compatible endpoint: /api/master/delete/{ticker}
    @DeleteMapping("/tactic/delete/{ticker:.+}")
    public ResponseEntity<Void> deleteMasterLegacy(@PathVariable String ticker) {
        // Delegate to the canonical delete behavior
        return deleteTactic(ticker);
    }

    @PutMapping("/tactic/{ticker:.+}")
    public ResponseEntity<Tactic> updateTactics(@PathVariable String ticker, @RequestBody Tactic tactic) {
        if (!tacticService.exists(ticker)) return ResponseEntity.notFound().build();
        Tactic saved = tacticService.update(ticker, tactic);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/tactic/list")
    public ResponseEntity<List<Tactic>> getAllTactics() {
        return ResponseEntity.ok(tacticService.list());
    }
}
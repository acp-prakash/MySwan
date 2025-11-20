package org.myswan.controller.internal;

import org.myswan.model.Master;
import org.myswan.service.internal.MasterService;
import org.myswan.service.internal.ScoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ScoringEngine {

    private final ScoringService scoringService;

    public ScoringEngine(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    @PostMapping("/scoring/calculate")
    public ResponseEntity<String> calculateScore() {
        return ResponseEntity.ok(scoringService.calculateScore());
    }

}
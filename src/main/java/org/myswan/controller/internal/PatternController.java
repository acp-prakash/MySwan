package org.myswan.controller.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Pattern;
import org.myswan.service.internal.PatternService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
public class PatternController {

    private final PatternService patternService;

    public PatternController(PatternService patternService) {
        this.patternService = patternService;
    }

    @GetMapping("/pattern/list")
    public ResponseEntity<List<Pattern>> getAllPatterns() {
        return ResponseEntity.ok(patternService.list());
    }

    @GetMapping("/pattern/{ticker}")
    public ResponseEntity<List<Pattern>> getPatternsByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(patternService.listByTicker(ticker));
    }

    @GetMapping("/pattern/history/{ticker}")
    public ResponseEntity<List<Pattern>> getPatternHistoryByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(patternService.listHistoryByTicker(ticker));
    }
}

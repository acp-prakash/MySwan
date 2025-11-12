package org.myswan.controller.internal;

import org.myswan.model.Pattern;
import org.myswan.service.internal.PatternService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PatternController {

    private final PatternService patternService;

    public PatternController(PatternService patternService) {
        this.patternService = patternService;
    }

    @GetMapping("/pattern/{ticker}")
    public ResponseEntity<Pattern> getPattern(@PathVariable String ticker) {
        return patternService.getByTicker(ticker).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/pattern/add")
    public ResponseEntity<Pattern> addPattern(@RequestBody Pattern pattern) {
        if (pattern == null || pattern.getTicker() == null) return ResponseEntity.badRequest().build();
        Pattern saved = patternService.create(pattern);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/pattern/{ticker}")
    public ResponseEntity<Void> deletePattern(@PathVariable String ticker) {
        if (!patternService.exists(ticker)) return ResponseEntity.notFound().build();
        patternService.delete(ticker);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/pattern/{ticker}")
    public ResponseEntity<Pattern> updatePattern(@PathVariable String ticker, @RequestBody Pattern pattern) {
        if (!patternService.exists(ticker)) return ResponseEntity.notFound().build();
        Pattern saved = patternService.update(ticker, pattern);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/pattern/list")
    public ResponseEntity<List<Pattern>> getAllPatterns() {
        return ResponseEntity.ok(patternService.list());
    }
}
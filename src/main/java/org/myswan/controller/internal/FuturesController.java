package org.myswan.controller.internal;

import org.myswan.model.collection.Futures;
import org.myswan.service.internal.FuturesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FuturesController {

    private final FuturesService futuresService;

    public FuturesController(FuturesService futuresService) {
        this.futuresService = futuresService;
    }

    @GetMapping("/futures/list")
    public ResponseEntity<List<Futures>> getAllFutures() {
        return ResponseEntity.ok(futuresService.list());
    }

    @GetMapping("/futures/history/{ticker:.+}")
    public ResponseEntity<List<Futures>> getFuturesHistory(
            @PathVariable String ticker,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<Futures> history = futuresService.getFuturesHistory(ticker, from, to);
        return ResponseEntity.ok(history);
    }
}
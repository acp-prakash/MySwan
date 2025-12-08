package org.myswan.controller.internal;

import org.myswan.model.collection.Futures;
import org.myswan.service.internal.FuturesService;
import org.myswan.service.external.FuturesBarchartClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FuturesController {

    private final FuturesService futuresService;
    private final FuturesBarchartClient futuresBarchartClient;

    public FuturesController(FuturesService futuresService, FuturesBarchartClient futuresBarchartClient) {
        this.futuresService = futuresService;
        this.futuresBarchartClient = futuresBarchartClient;
    }

    @GetMapping("/futures/list")
    public ResponseEntity<List<Futures>> getAllFutures() {
        return ResponseEntity.ok(futuresService.list());
    }

    @GetMapping("/futures/{ticker}")
    public ResponseEntity<Futures> getFutures(@PathVariable String ticker) {
        return futuresService.getByTicker(ticker)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/futures/add")
    public ResponseEntity<Futures> addFutures(@RequestBody Futures futures) {
        if (futures == null || futures.getTicker() == null) {
            return ResponseEntity.badRequest().build();
        }
        Futures saved = futuresService.create(futures);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/futures/{ticker}")
    public ResponseEntity<Futures> updateFutures(@PathVariable String ticker, @RequestBody Futures futures) {
        if (!futuresService.exists(ticker)) {
            return ResponseEntity.notFound().build();
        }
        Futures saved = futuresService.update(ticker, futures);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/futures/{ticker:.+}")
    public ResponseEntity<Void> deleteFutures(@PathVariable String ticker) {
        if (!futuresService.exists(ticker)) {
            return ResponseEntity.notFound().build();
        }
        futuresService.delete(ticker);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/futures/delete/{ticker:.+}")
    public ResponseEntity<Void> deleteFuturesLegacy(@PathVariable String ticker) {
        return deleteFutures(ticker);
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

    @PostMapping("/futures/fetch-barchart")
    public ResponseEntity<List<Futures>> fetchBarchartData() {
        try {
            List<Futures> futures = futuresBarchartClient.fetchAllFutures();
            return ResponseEntity.ok(futures);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

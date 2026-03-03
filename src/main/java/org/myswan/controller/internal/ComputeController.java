package org.myswan.controller.internal;

import org.myswan.model.dto.PredictionAnalysisResponse;
import org.myswan.service.internal.ComputeService;
import org.myswan.service.internal.PredictionAnalysisService;
import org.myswan.service.internal.onetime.DayChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ComputeController {

    private static final Logger log = LoggerFactory.getLogger(ComputeController.class);

    private final ComputeService computeService;
    private final DayChange dayChange;
    private final PredictionAnalysisService predictionAnalysisService;

    public ComputeController(ComputeService computeService, DayChange dayChange,
                            PredictionAnalysisService predictionAnalysisService) {
        this.computeService = computeService;
        this.dayChange = dayChange;
        this.predictionAnalysisService = predictionAnalysisService;
    }

    @PostMapping("/compute/process")
    public ResponseEntity<String> compute() {
        return ResponseEntity.ok(computeService.compute());
    }

    @PostMapping("/compute/day-change-metrics")
    public ResponseEntity<String> computeDayChangeMetrics() {
        try {
            log.info("Starting Day Change metrics computation for all stock history...");
            dayChange.computeDayChangeForAllHistory();
            return ResponseEntity.ok("Day Change metrics computation completed successfully!");
        } catch (Exception e) {
            log.error("Failed to compute Day Change metrics", e);
            return ResponseEntity.status(500).body("Failed to compute metrics: " + e.getMessage());
        }
    }

    @GetMapping("/compute/analyze-predictions")
    public ResponseEntity<PredictionAnalysisResponse> analyzePredictions(
            @RequestParam(defaultValue = "5") double threshold) {
        try {
            log.info("Starting prediction analysis with threshold: {}%...", threshold);
            PredictionAnalysisResponse response = predictionAnalysisService.analyzeMetrics(threshold);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to analyze predictions", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/compute/analyze-tickers")
    public ResponseEntity<?> analyzeTickerPerformance(
            @RequestParam(defaultValue = "20") double threshold) {
        try {
            log.info("Starting ticker performance analysis with threshold: {}%...", threshold);
            List<?> response = predictionAnalysisService.analyzeTickerPerformance(threshold);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to analyze ticker performance", e);
            return ResponseEntity.status(500).body("Failed: " + e.getMessage());
        }
    }

    @GetMapping("/compute/ticker-stats/{ticker}")
    public ResponseEntity<?> getTickerStats(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "20") double threshold) {
        try {
            log.info("Getting stats for ticker: {} with threshold: {}%", ticker, threshold);
            Map<String, Object> response = predictionAnalysisService.getTickerStats(ticker.toUpperCase(), threshold);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get ticker stats for: {}", ticker, e);
            return ResponseEntity.status(500).body("Failed: " + e.getMessage());
        }
    }

}
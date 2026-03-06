package org.myswan.controller.external;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Pattern;
import org.myswan.service.external.EtradeClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/external")
public class ETradeController {

    private final EtradeClient etradeClient;

    public ETradeController(EtradeClient etradeClient) {
        this.etradeClient = etradeClient;
    }

    @GetMapping("/pattern/fetch-etrade-pattern/{ticker}")
    public ResponseEntity<List<Pattern>> fetchETradePattern(@PathVariable String ticker) {
        try {
            log.info("Fetching eTrade pattern for ticker: {}", ticker);
            List<Pattern> list = etradeClient.fetchPatternsForTicker(ticker);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.error("Error fetching eTrade pattern for ticker: {}", ticker, e);
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }

    @PostMapping("/pattern/fetch-etrade")
    public ResponseEntity<String> fetchETradePatterns(@RequestParam(required = false, defaultValue = "N") String onlyMyFav) {
        try {
            String result = etradeClient.fetchAndSaveAllPatterns(onlyMyFav);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in fetchEtradePatterns", e);
            return ResponseEntity.internalServerError()
                .body("Error fetching eTrade patterns: " + e.getMessage());
        }
    }
}

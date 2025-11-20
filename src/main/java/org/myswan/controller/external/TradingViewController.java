package org.myswan.controller.external;

import org.myswan.service.external.TradingViewClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/external")
public class TradingViewController {

    private final TradingViewClient tradingViewClient;

    public TradingViewController(TradingViewClient tradingViewClient) {
        this.tradingViewClient = tradingViewClient;
    }

    @GetMapping("/getAllStocks")
    public ResponseEntity<JsonNode> getAllStocks() throws Exception {
        JsonNode result = tradingViewClient.scanStocks();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/getAllETFs")
    public ResponseEntity<JsonNode> getAllETFs() throws Exception {
        JsonNode result = tradingViewClient.scanETFs();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/updateTradingView")
    public ResponseEntity<String> updateTradingView() throws Exception {
        return ResponseEntity.ok(tradingViewClient.updateTradingView());
    }
}
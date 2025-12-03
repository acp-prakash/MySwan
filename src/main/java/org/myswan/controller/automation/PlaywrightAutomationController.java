package org.myswan.controller.automation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.myswan.service.automation.BarchartPlaywrightService;
import org.myswan.service.automation.TradingViewPlaywrightService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/automation")
@Slf4j
@Tag(name = "Automation", description = "Playwright automation for login and credential extraction")
public class PlaywrightAutomationController {

    private final BarchartPlaywrightService barchartPlaywrightService;
    private final TradingViewPlaywrightService tradingViewPlaywrightService;

    public PlaywrightAutomationController(BarchartPlaywrightService barchartPlaywrightService,
                                          TradingViewPlaywrightService tradingViewPlaywrightService) {
        this.barchartPlaywrightService = barchartPlaywrightService;
        this.tradingViewPlaywrightService = tradingViewPlaywrightService;
    }

    @PostMapping("/barchart/login")
    @Operation(summary = "Auto-login to Barchart and extract credentials",
               description = "Uses Playwright to automatically login to Barchart, intercept API calls, extract x-xsrf-token and cookies, and store them in AppCache")
    public ResponseEntity<Map<String, String>> autoLoginBarchart() {
        log.info("Received request to auto-login to Barchart");
        try {
            Map<String, String> result = barchartPlaywrightService.autoLoginAndExtractCredentials();

            if ("success".equals(result.get("status"))) {
                return ResponseEntity.ok(result);
            } else if ("partial".equals(result.get("status"))) {
                return ResponseEntity.status(206).body(result); // 206 Partial Content
            } else {
                return ResponseEntity.status(500).body(result);
            }
        } catch (Exception e) {
            log.error("Error in auto-login controller: ", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Unexpected error: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/barchart/test-credentials")
    @Operation(summary = "Test if Barchart credentials exist",
               description = "Checks if valid Barchart token and cookie exist in AppCache")
    public ResponseEntity<Map<String, Object>> testBarchartCredentials() {
        log.info("Testing Barchart credentials");
        boolean valid = barchartPlaywrightService.testCredentials();
        return ResponseEntity.ok(Map.of(
                "valid", valid,
                "message", valid ? "Credentials found in AppCache" : "No credentials found in AppCache"
        ));
    }

    @PostMapping("/tradingview/login")
    @Operation(summary = "Auto-login to TradingView and extract cookie",
               description = "Uses Playwright to automatically login to TradingView, intercept API calls, extract cookie, and store it in AppCache")
    public ResponseEntity<Map<String, String>> autoLoginTradingView() {
        log.info("Received request to auto-login to TradingView");
        try {
            Map<String, String> result = tradingViewPlaywrightService.autoLoginAndExtractCredentials();

            if ("success".equals(result.get("status"))) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(500).body(result);
            }
        } catch (Exception e) {
            log.error("Error in TradingView auto-login controller: ", e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Unexpected error: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/tradingview/test-credentials")
    @Operation(summary = "Test if TradingView cookie exists",
               description = "Checks if valid TradingView cookie exists in AppCache")
    public ResponseEntity<Map<String, Object>> testTradingViewCredentials() {
        log.info("Testing TradingView credentials");
        boolean valid = tradingViewPlaywrightService.testCredentials();
        return ResponseEntity.ok(Map.of(
                "valid", valid,
                "message", valid ? "Cookie found in AppCache" : "No cookie found in AppCache"
        ));
    }
}


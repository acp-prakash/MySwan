package org.myswan.controller.external;

import org.myswan.service.external.RobinHoodClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/external")
public class RobinHoodController {

    private final RobinHoodClient robinHoodClient;

    public RobinHoodController(RobinHoodClient robinHoodClient) {
        this.robinHoodClient = robinHoodClient;
    }

    @PostMapping("/options/getOptions")
    public ResponseEntity<String> getOptions(
            @RequestParam(value = "histDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate histDate) {
        int count = robinHoodClient.fetchAndSaveOptions(histDate);
        if (count == 0) {
            return ResponseEntity.ok("No options fetched. Ensure the Robinhood Token and Options URL are saved in Settings.");
        }
        return ResponseEntity.ok("✅ Fetched and saved " + count + " options from Robinhood.");
    }
}
package org.myswan.controller.internal;

import lombok.extern.slf4j.Slf4j;
import org.myswan.model.collection.Options;
import org.myswan.service.internal.OptionsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
public class OptionsController {

    private final OptionsService optionsService;

    public OptionsController(OptionsService optionsService) {
        this.optionsService = optionsService;
    }

    @GetMapping("/options/list")
    public ResponseEntity<List<Options>> getAllOptions() {
        return ResponseEntity.ok(optionsService.list());
    }

    @GetMapping("/options/{ticker}")
    public ResponseEntity<List<Options>> getOptionsByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(optionsService.listByTicker(ticker));
    }

    @GetMapping("/options/byId/{id}")
    public ResponseEntity<Options> getOptionById(@PathVariable String id) {
        Options option = optionsService.findById(id);
        if (option == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(option);
    }

    @PostMapping("/options/create")
    public ResponseEntity<Options> createOption(@RequestBody Options option) {
        log.info("Creating option for ticker {}", option != null ? option.getTicker() : "-unknown-");
        Options saved = optionsService.save(option);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/options/update")
    public ResponseEntity<Options> updateOption(@RequestBody Options option) {
        if (option == null || option.getId() == null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Updating option {}", option.getId());
        Options updated = optionsService.update(option);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/options/delete/{id}")
    public ResponseEntity<String> deleteOption(@PathVariable String id) {
        log.info("Deleting option {}", id);
        optionsService.delete(id);
        return ResponseEntity.ok("Option deleted successfully");
    }


    @GetMapping("/options/history/{ticker}")
    public ResponseEntity<List<Options>> getOptionsHistory(@PathVariable String ticker) {
        return ResponseEntity.ok(optionsService.getOptionsHistory(ticker));
    }

    @GetMapping("/options/history/byOptionId/{optionId}")
    public ResponseEntity<List<Options>> getOptionsHistoryByOptionId(@PathVariable String optionId) {
        return ResponseEntity.ok(optionsService.getOptionsHistoryByOptionId(optionId));
    }
}


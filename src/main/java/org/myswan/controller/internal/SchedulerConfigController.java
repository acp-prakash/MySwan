package org.myswan.controller.internal;

import org.myswan.model.collection.SchedulerConfig;
import org.myswan.service.internal.SchedulerConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scheduler")
public class SchedulerConfigController {

    private final SchedulerConfigService schedulerConfigService;

    public SchedulerConfigController(SchedulerConfigService schedulerConfigService) {
        this.schedulerConfigService = schedulerConfigService;
    }

    /** GET current scheduler config */
    @GetMapping("/config")
    public ResponseEntity<SchedulerConfig> getConfig() {
        return ResponseEntity.ok(schedulerConfigService.get());
    }

    /** PUT update scheduler config (interval, window, enabled flag) */
    @PutMapping("/config")
    public ResponseEntity<SchedulerConfig> updateConfig(@RequestBody SchedulerConfig config) {
        return ResponseEntity.ok(schedulerConfigService.update(config));
    }

    /** POST enable – clears failure state and enables */
    @PostMapping("/enable")
    public ResponseEntity<SchedulerConfig> enable() {
        return ResponseEntity.ok(schedulerConfigService.enable());
    }

    /** POST disable */
    @PostMapping("/disable")
    public ResponseEntity<SchedulerConfig> disable() {
        return ResponseEntity.ok(schedulerConfigService.disable());
    }
}


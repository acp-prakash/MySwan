package org.myswan.controller.internal;

import org.myswan.service.internal.ComputeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ComputeController {

    private final ComputeService computeService;

    public ComputeController(ComputeService computeService) {
        this.computeService = computeService;
    }

    @PostMapping("/scoring/calculate")
    public ResponseEntity<String> calculateScore() {
        return ResponseEntity.ok(computeService.calculateScore());
    }

}
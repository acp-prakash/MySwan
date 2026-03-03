package org.myswan.controller.external;

import org.myswan.model.collection.Options;
import org.myswan.service.external.RobinHoodClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/external")
public class RobinHoodController {

    private final RobinHoodClient robinHoodClient;

    public RobinHoodController(RobinHoodClient robinHoodClient) {
        this.robinHoodClient = robinHoodClient;
    }

    @PostMapping("/getOptions")
    public ResponseEntity<List<Options>> getOptions() throws Exception {
        return ResponseEntity.ok(robinHoodClient.getOptions());
    }
}
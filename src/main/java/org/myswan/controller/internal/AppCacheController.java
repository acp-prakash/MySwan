package org.myswan.controller.internal;

import org.myswan.model.collection.AppCache;
import org.myswan.service.internal.AppCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AppCacheController {

    private final AppCacheService appCacheService;

    public AppCacheController(AppCacheService appCacheService) {
        this.appCacheService = appCacheService;
    }

    @GetMapping("/cache/get")
    public ResponseEntity<AppCache> getAppCache() {
        return ResponseEntity.ok(appCacheService.getAppCache());
    }

    @PostMapping("/cache/add")
    public ResponseEntity<AppCache> updateAppCache(@RequestBody AppCache appCache) {
        if (appCache == null) return ResponseEntity.badRequest().build();
        AppCache saved = appCacheService.update(appCache);
        return ResponseEntity.ok(saved);
    }
}
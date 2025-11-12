package org.myswan.service.internal;

import org.myswan.model.AppCache;
import org.myswan.repository.AppCacheRepository;
import org.springframework.stereotype.Service;

import org.springframework.util.ReflectionUtils;
import java.lang.reflect.Field;
import java.time.LocalDate;

@Service
public class AppCacheService {
    private final AppCacheRepository repository;

    public AppCacheService(AppCacheRepository repository) {
        this.repository = repository;
    }

    public AppCache create(AppCache cache) {
        return repository.save(cache);
    }

    public AppCache update(AppCache cache) {
        AppCache existingCache = getAppCache();
        if(existingCache == null) {
            cache.setCreatedAt(LocalDate.now());
            return repository.save(cache);
        }
        for (Field field : AppCache.class.getDeclaredFields()) {
            field.setAccessible(true);
            Object newValue = ReflectionUtils.getField(field, cache);
            if (newValue != null) {
                if (newValue instanceof String s && !s.isEmpty()) {
                    ReflectionUtils.setField(field, existingCache, newValue);
                } else if (!(newValue instanceof String)) {
                    ReflectionUtils.setField(field, existingCache, newValue);
                }
            }
        }
        existingCache.setUpdatedAt(LocalDate.now());
        return repository.save(existingCache);
    }

    public void delete(String cacheId) {
        repository.deleteById(cacheId);
    }

    public boolean exists(String cacheId) {
        return repository.existsById(cacheId);
    }

    public AppCache getAppCache() {
        try {
            return repository.findById("APP_CACHE").orElse(null);
        } catch (Exception ignored) {

        }
        // Return empty list if no stocks found in DB
        return new AppCache();
    }
}
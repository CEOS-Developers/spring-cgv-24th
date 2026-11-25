package com.cgvclone.cgv.domain.cinema;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import java.util.Objects;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.stereotype.Component;

@Component
public class CinemaListCacheInvalidator {

    private final Cache lists;

    public CinemaListCacheInvalidator(CacheManager cacheManager) {
        lists = new TransactionAwareCacheDecorator(Objects.requireNonNull(cacheManager.getCache("cinemas")));
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void clearAfterCommit(Cinema cinema) {
        lists.clear();
    }
}

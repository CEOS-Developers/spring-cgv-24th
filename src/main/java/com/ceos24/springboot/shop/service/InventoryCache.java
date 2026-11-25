package com.ceos24.springboot.shop.service;

import com.ceos24.springboot.shop.dto.InventoryResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

@Component
class InventoryCache {
    private static final long TTL_NANOS = Duration.ofSeconds(5).toNanos();
    private static final int MAX_ENTRIES = 1_024;

    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long invalidations;

    List<InventoryResponse> get(Long theaterId, Supplier<List<InventoryResponse>> load) {
        long observedInvalidations;
        synchronized (entries) {
            Entry entry = entries.get(theaterId);
            if (entry != null && System.nanoTime() - entry.loadedAt() < TTL_NANOS) {
                return entry.inventories();
            }
            observedInvalidations = invalidations;
        }

        long loadStarted = System.nanoTime();
        List<InventoryResponse> inventories = List.copyOf(load.get());
        synchronized (entries) {
            // A read that overlaps a committed write must not repopulate the invalidated cache.
            if (observedInvalidations == invalidations) {
                entries.put(theaterId, new Entry(loadStarted, inventories));
                if (entries.size() > MAX_ENTRIES) {
                    entries.pollFirstEntry();
                }
            }
        }
        return inventories;
    }

    void invalidateAfterCommit(Long theaterId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                synchronized (entries) {
                    entries.remove(theaterId);
                    invalidations++;
                }
            }
        });
    }

    private record Entry(long loadedAt, List<InventoryResponse> inventories) {}
}

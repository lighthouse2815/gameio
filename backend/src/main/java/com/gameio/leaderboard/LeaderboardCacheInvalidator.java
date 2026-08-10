package com.gameio.leaderboard;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LeaderboardCacheInvalidator {
    private final LeaderboardCache cache;

    LeaderboardCacheInvalidator(LeaderboardCache cache) {
        this.cache = cache;
    }

    public void afterCommit(UUID gameId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.invalidate(gameId);
                }
            });
            return;
        }
        cache.invalidate(gameId);
    }
}

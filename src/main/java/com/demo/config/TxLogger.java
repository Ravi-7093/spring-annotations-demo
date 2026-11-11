package com.demo.config;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Helper that PRINTS what @Transactional is doing, so you can see it in the console.
 * Not an annotation - just for learning.
 */
public final class TxLogger {

    private TxLogger() {}

    public static void log(String where) {
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        String name = TransactionSynchronizationManager.getCurrentTransactionName();
        boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        System.out.println("  [TX] " + where + " | active=" + active + " | name=" + name
                + " | readOnly=" + readOnly + " | thread=" + Thread.currentThread().getName());

        if (active) {
            // Called by Spring when the transaction finishes: tells us COMMIT or ROLLBACK.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    String result = status == STATUS_COMMITTED ? "COMMITTED" : "ROLLED BACK";
                    System.out.println("  [TX] " + where + " -> " + result);
                }
            });
        }
    }
}

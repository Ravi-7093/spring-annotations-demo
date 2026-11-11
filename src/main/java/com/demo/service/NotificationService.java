package com.demo.service;

import com.demo.config.TxLogger;
import com.demo.dto.User;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
public class NotificationService {

    /*
     * @Async("appExecutor")
     *   "Run this method on a thread from the 'appExecutor' pool. Caller does NOT wait."
     *   Return type void -> fire and forget. An exception here goes to AsyncUncaughtExceptionHandler.
     *
     * @Transactional together with @Async
     *   The transaction starts INSIDE the async thread, so it is a SEPARATE transaction
     *   from the caller's. If the caller rolls back, this one is not affected (and vice versa).
     *
     * Console (note the thread names):
     *   [UserService.create] thread=http-nio-8080-exec-1
     *   [EMAIL] sending welcome mail to ravi@test.com | thread=async-1
     *   [TX] NotificationService.sendWelcomeEmail | active=true | name=...sendWelcomeEmail | thread=async-1
     *
     * Also NOT copied to the async thread by default: SecurityContext, MDC (log trace id), request scope.
     */
    @Async("appExecutor")
    @Transactional
    public void sendWelcomeEmail(User user) {
        sleep(500); // pretend SMTP is slow; the HTTP response has already gone back
        System.out.println("[EMAIL] sending welcome mail to " + user.getEmail()
                + " | thread=" + Thread.currentThread().getName());
        TxLogger.log("NotificationService.sendWelcomeEmail");

        if ("fail@test.com".equals(user.getEmail())) {
            // void @Async -> caller never sees this. Our AsyncConfig handler prints it.
            throw new IllegalStateException("SMTP down");
        }
    }

    /*
     * @Async returning CompletableFuture<T>
     *   Caller gets a future immediately and can wait/compose: future.join(), thenApply(...).
     *   Exceptions are stored INSIDE the future (not lost).
     *
     * Console:
     *   [REPORT] building report for 3 users | thread=async-2
     */
    @Async("appExecutor")
    public CompletableFuture<String> buildReport(int userCount) {
        System.out.println("[REPORT] building report for " + userCount + " users | thread="
                + Thread.currentThread().getName());
        sleep(1000);
        return CompletableFuture.completedFuture("Report ready: " + userCount + " users");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

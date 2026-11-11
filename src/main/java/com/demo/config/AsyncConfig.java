package com.demo.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/*
 * @Configuration: this class defines beans (like an XML config file, but in Java).
 *
 * AsyncConfigurer lets us set:
 *   1. the default executor for @Async
 *   2. a handler for exceptions thrown by @Async VOID methods (otherwise they are LOST)
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    // @Bean("appExecutor"): register the returned object as a bean named "appExecutor".
    // @Async("appExecutor") picks this pool by name.
    @Bean("appExecutor")
    public ThreadPoolTaskExecutor appExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);          // threads always alive
        ex.setMaxPoolSize(4);           // extra threads only created when queue is FULL
        ex.setQueueCapacity(50);        // tasks wait here first
        ex.setThreadNamePrefix("async-"); // shows up in logs: [async-1]
        ex.initialize();
        return ex;
    }

    @Override
    public Executor getAsyncExecutor() {
        return appExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // Without this, an exception inside an @Async void method just disappears.
        return (ex, method, params) ->
                System.out.println("[@Async ERROR] " + method.getName() + " failed: " + ex.getMessage());
    }
}

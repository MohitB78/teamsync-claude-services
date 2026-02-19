package com.teamsync.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async and scheduling configuration for background tasks.
 *
 * SECURITY FIX (Round 14 #H21): Added RejectedExecutionHandler to all executors
 * to prevent silent task drops when queue is full. Tasks that are rejected
 * are logged at WARN level for monitoring and alerting.
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AsyncConfig {

    /**
     * SECURITY FIX (Round 14 #H21): Custom rejection handler that logs rejected tasks.
     * Prevents silent failures when the task queue is full.
     */
    private RejectedExecutionHandler createLoggingRejectionHandler(String executorName) {
        return (runnable, executor) -> {
            log.warn("SECURITY: Task rejected by {} - queue full. Active: {}, Queue: {}/{}. " +
                            "Consider increasing pool size or reviewing task throughput.",
                    executorName,
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    executor.getQueue().remainingCapacity() + executor.getQueue().size());

            // Use CallerRunsPolicy as fallback - runs the task in the calling thread
            // This provides backpressure and prevents task loss at the cost of blocking
            if (!executor.isShutdown()) {
                runnable.run();
            }
        };
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        // SECURITY FIX (Round 14 #H21): Add rejection handler
        executor.setRejectedExecutionHandler(createLoggingRejectionHandler("taskExecutor"));
        executor.initialize();
        return executor;
    }

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-sender-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // SECURITY FIX (Round 14 #H21): Add rejection handler
        executor.setRejectedExecutionHandler(createLoggingRejectionHandler("emailExecutor"));
        executor.initialize();
        return executor;
    }
}

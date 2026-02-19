package com.teamsync.storage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Async configuration for storage service.
 * Uses virtual threads for fire-and-forget operations like Kafka event publishing.
 *
 * Virtual threads (Project Loom) provide lightweight concurrency without the overhead
 * of platform threads, making them ideal for I/O-bound async operations.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Virtual thread executor for async event publishing.
     * Each task gets its own virtual thread, which is very lightweight (~1KB vs ~1MB for platform threads).
     * Perfect for fire-and-forget operations like Kafka event publishing.
     */
    @Bean(name = "eventPublisherExecutor")
    public Executor eventPublisherExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Virtual thread executor for parallel presigned URL generation.
     * Used when initializing multipart uploads to generate URLs for all parts concurrently.
     */
    @Bean(name = "presignExecutor")
    public Executor presignExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

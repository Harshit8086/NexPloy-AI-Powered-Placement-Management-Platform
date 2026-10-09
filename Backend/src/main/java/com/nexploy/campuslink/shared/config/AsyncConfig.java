package com.nexploy.campuslink.shared.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables Spring's {@code @Async} support and provides a small bounded pool for off-request
 * background work — currently the readiness recompute triggered after a profile / assessment /
 * mock-interview change (see {@code ReadinessRecomputeListener}).
 *
 * <p>Kept intentionally small: a readiness recompute is a short, deterministic call to the ML
 * baseline. A {@link ThreadPoolExecutor.CallerRunsPolicy} means a saturated queue degrades to
 * running the recompute inline rather than silently dropping it, so no change is ever lost.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("readinessRecomputeExecutor")
    public Executor readinessRecomputeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("readiness-recompute-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

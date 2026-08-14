package com.tc.traumchatroom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置 — 用于 AI 回复等异步任务
 * 不配置的话，@Async 会用默认的 SimpleAsyncTaskExecutor（每次新建线程，性能差）
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);                                   // 核心线程数（常驻）
        executor.setMaxPoolSize(10);                                   // 最大线程数（高峰期）
        executor.setQueueCapacity(50);                                 // 队列容量（排队等待）
        executor.setThreadNamePrefix("ai-reply-");                     // 线程名前缀（方便日志排查）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 拒绝策略：谁提交谁执行
        executor.initialize();
        return executor;
    }
}

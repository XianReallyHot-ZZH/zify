package com.zify.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async executor configuration.
 */
@Configuration
@EnableAsync
public class AsyncExecutorConfig {

    private static final int ASYNC_CORE_POOL_SIZE = 4;
    private static final int ASYNC_MAX_POOL_SIZE = 16;
    private static final int ASYNC_QUEUE_CAPACITY = 200;

    private static final int DOCUMENT_PARSE_CORE_POOL_SIZE = 2;
    private static final int DOCUMENT_PARSE_MAX_POOL_SIZE = 2;
    private static final int DOCUMENT_PARSE_QUEUE_CAPACITY = 100;

    private static final int WORKFLOW_CORE_POOL_SIZE = 4;
    private static final int WORKFLOW_MAX_POOL_SIZE = 8;
    private static final int WORKFLOW_QUEUE_CAPACITY = 200;

    private static final long KEEP_ALIVE_SECONDS = 60L;

    @Bean(name = "asyncTaskExecutor", destroyMethod = "shutdown")
    public ExecutorService asyncTaskExecutor() {
        return newThreadPoolExecutor(
                ASYNC_CORE_POOL_SIZE,
                ASYNC_MAX_POOL_SIZE,
                ASYNC_QUEUE_CAPACITY,
                "zify-async-"
        );
    }

    @Bean(name = "documentParseExecutor", destroyMethod = "shutdown")
    public ExecutorService documentParseExecutor() {
        return newThreadPoolExecutor(
                DOCUMENT_PARSE_CORE_POOL_SIZE,
                DOCUMENT_PARSE_MAX_POOL_SIZE,
                DOCUMENT_PARSE_QUEUE_CAPACITY,
                "zify-doc-parse-"
        );
    }

    @Bean(name = "workflowExecutor", destroyMethod = "shutdown")
    public ExecutorService workflowExecutor() {
        return newThreadPoolExecutor(
                WORKFLOW_CORE_POOL_SIZE,
                WORKFLOW_MAX_POOL_SIZE,
                WORKFLOW_QUEUE_CAPACITY,
                "zify-workflow-"
        );
    }

    private ThreadPoolExecutor newThreadPoolExecutor(
            int corePoolSize,
            int maximumPoolSize,
            int queueCapacity,
            String threadNamePrefix
    ) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new NamedThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final String threadNamePrefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedThreadFactory(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, threadNamePrefix + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}

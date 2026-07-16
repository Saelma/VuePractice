package com.glassvue.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 실행(@Async) 설정 — 지금은 도메인 이벤트 리스너의 후처리를 요청 스레드에서 떼어내는 용도.
 * - 기본 SimpleAsyncTaskExecutor는 스레드를 무한 생성하므로 **바운드 ThreadPoolTaskExecutor**로 교체.
 * - 큐가 가득 차면 CallerRuns(호출 스레드가 대신 실행)로 유실 대신 백프레셔.
 * - void @Async에서 던진 예외는 호출자에게 안 가므로 UncaughtExceptionHandler로 로깅.
 *
 * 주의: 인프로세스 @Async는 best-effort(앱 다운 시 유실). "유실 금지"가 필요하면 아웃박스/RabbitMQ(MSA 단계).
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("비동기 이벤트 처리 실패: {}", method, ex);
    }
}

package com.glassvue.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 활성화 (조회수 플러시 등).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

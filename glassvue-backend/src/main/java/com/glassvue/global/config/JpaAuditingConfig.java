package com.glassvue.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseTimeEntity의 createdAt/updatedAt 자동 세팅을 활성화한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}

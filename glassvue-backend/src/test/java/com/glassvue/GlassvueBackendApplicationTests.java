package com.glassvue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 전체 컨텍스트 로드 스모크 테스트. Oracle·Redis·JWT_SECRET(.env) 등 실인프라가 필요해
 * 인프라 없는 기본 빌드(CI 등)를 깨뜨리므로, 통합 테스트 계층(별도 테스트 스키마) 도입 전까지 비활성화한다.
 * 도메인/서비스 로직은 src/test/java/com/glassvue/domain/* 의 순수 단위 테스트가 커버한다.
 */
@Disabled("실인프라(Oracle·Redis·.env) 필요 — 통합 테스트 계층 도입 시 활성화")
@SpringBootTest
class GlassvueBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}

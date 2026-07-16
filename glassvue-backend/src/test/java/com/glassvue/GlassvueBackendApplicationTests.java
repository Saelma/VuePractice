package com.glassvue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 전체 컨텍스트 로드 스모크 테스트(통합). Oracle·Redis·JWT_SECRET(.env) 등 실인프라가 필요하다.
 * DB_HOST 환경변수가 있을 때만 실행 → `set -a; . .env; set +a; ./gradlew test`.
 * 인프라 없는 기본 빌드에서는 자동 skip(단위 테스트는 항상 실행).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class GlassvueBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}

package com.glassvue.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * ⚠ <b>테스트가 운영과 다른 Redis DB 를 쓰는지</b> 고정한다 (2026-07-29).
 *
 * <p><b>왜 이 테스트가 생겼나</b>: 그전엔 테스트와 운영이 db0 를 공유했고, 그래서 두 방향으로 샜다.
 * <ul>
 *   <li>운영(:8080)의 조회수 플러셔가 30초마다 {@code notice:view:*} 를 SCAN+GETDEL 하면서
 *       <b>테스트가 만든 키까지 가져갔다</b> — {@code NoticeQueryServiceIntegrationTest} 가
 *       간헐적으로 실패하던 진짜 원인이다. <b>{@code @AfterEach} 정리로는 못 막는다</b>
 *       (지우는 게 같은 프로세스가 아니다).</li>
 *   <li>반대로 테스트가 운영 Redis 를 더럽혔다(비밀번호 재설정·이메일 인증 토큰 등).</li>
 * </ul>
 *
 * <p>격리는 {@code build.gradle} 의 {@code systemProperty 'spring.data.redis.database'} 로 준다.
 * 그건 <b>빌드 설정이라 조용히 사라지기 쉬운 자리</b>다 — 누가 지우면 위 증상이 다시 나타나는데,
 * 그때는 <b>"가끔 실패하는 테스트"</b> 로만 보여서 원인을 찾는 데 또 며칠이 걸린다.
 * 그래서 여기서 못박는다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class RedisIsolationTest {

    @Autowired RedisConnectionFactory connectionFactory;

    @Test
    @DisplayName("테스트는 운영(db0)이 아닌 Redis DB 를 쓴다")
    void usesNonProductionDatabase() {
        assertThat(connectionFactory).isInstanceOf(LettuceConnectionFactory.class);
        int db = ((LettuceConnectionFactory) connectionFactory).getDatabase();

        assertThat(db)
                .as("테스트가 운영과 같은 Redis DB(0)를 쓰고 있다 — build.gradle 의 "
                        + "systemProperty 'spring.data.redis.database' 가 사라졌는지 확인할 것. "
                        + "공유하면 운영 플러셔가 테스트 키를 가져가 조회수 테스트가 간헐 실패한다.")
                .isNotZero();
    }
}

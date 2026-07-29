package com.glassvue.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 이메일 인증번호 저장소 (B-14, 2026-07-29) — <b>실제 Redis</b> 로 검증한다.
 *
 * <p>여기서 고정하는 핵심은 <b>무차별 대입 차단</b>이다. 6자리는 100만 가지뿐이라, 시도 제한이 없으면
 * 남의 주소를 넣고 코드를 찍어 맞혀 <b>소유하지도 않은 주소를 인증됨으로 만들 수 있다.</b>
 * 목으로는 이 성질(카운터·TTL·폐기)이 검증되지 않아 통합 테스트로 둔다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class EmailVerificationCodeStoreTest {

    @Autowired EmailVerificationCodeStore store;
    @Autowired StringRedisTemplate redis;

    /** ⚠ 테스트가 만든 키는 스스로 치운다 — 공유 Redis 다(WA §3). */
    private void cleanup(UUID id) {
        redis.delete("auth:email-verify:" + id);
        redis.delete("auth:email-verify:attempt:" + id);
    }

    @Test
    @DisplayName("발급한 코드로 확인하면 통과하고, 그 코드는 소비되어 재사용 불가")
    void issueAndVerify_consumes() {
        UUID id = UUID.randomUUID();
        try {
            String code = store.issue(id);
            assertThat(code).matches("\\d{6}");
            assertThat(store.hasPending(id)).isTrue();

            assertThat(store.verify(id, code)).isTrue();
            // 소비됐으므로 같은 코드를 또 넣어도 실패한다
            assertThat(store.verify(id, code)).isFalse();
            assertThat(store.hasPending(id)).isFalse();
        } finally {
            cleanup(id);
        }
    }

    @Test
    @DisplayName("⚠ 5회 틀리면 코드가 폐기된다 — 그 뒤엔 맞는 값을 넣어도 실패(무차별 대입 차단)")
    void bruteForce_isBlockedAfterMaxAttempts() {
        UUID id = UUID.randomUUID();
        try {
            String code = store.issue(id);
            String wrong = code.equals("000000") ? "111111" : "000000";

            for (int i = 0; i < 5; i++) {
                assertThat(store.verify(id, wrong)).as("틀린 코드는 매번 false").isFalse();
            }

            // 여기가 핵심 — 5회를 넘겼으므로 **정답도 통하지 않는다**(재발송해야 한다)
            assertThat(store.verify(id, code))
                    .as("시도 횟수를 넘기면 코드가 폐기되어 정답도 실패해야 한다")
                    .isFalse();
            assertThat(store.hasPending(id)).isFalse();
        } finally {
            cleanup(id);
        }
    }

    @Test
    @DisplayName("재발송하면 코드가 새로 나오고 시도 횟수도 초기화된다")
    void reissue_resetsAttempts() {
        UUID id = UUID.randomUUID();
        try {
            store.issue(id);
            for (int i = 0; i < 4; i++) {
                store.verify(id, "000000");
            }
            String fresh = store.issue(id); // 재발송 — 카운터가 리셋되어야 한다

            // 리셋이 안 됐다면 아래 한 번의 실패로 5회에 도달해 코드가 폐기됐을 것이다
            store.verify(id, "111111");
            assertThat(store.verify(id, fresh))
                    .as("재발송 후에는 시도 횟수가 처음부터 세어져야 한다")
                    .isTrue();
        } finally {
            cleanup(id);
        }
    }

    @Test
    @DisplayName("발급한 적 없는 회원은 어떤 코드도 통하지 않는다")
    void noCode_alwaysFalse() {
        UUID id = UUID.randomUUID();
        assertThat(store.verify(id, "123456")).isFalse();
        assertThat(store.hasPending(id)).isFalse();
    }

    @Test
    @DisplayName("null 코드는 조용히 실패한다(NPE 아님)")
    void nullCode_isFalse() {
        UUID id = UUID.randomUUID();
        try {
            store.issue(id);
            assertThat(store.verify(id, null)).isFalse();
        } finally {
            cleanup(id);
        }
    }
}

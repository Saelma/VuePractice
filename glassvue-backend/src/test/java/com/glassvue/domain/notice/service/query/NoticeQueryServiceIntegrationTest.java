package com.glassvue.domain.notice.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassvue.domain.notice.dto.NoticeResponse;
import com.glassvue.domain.notice.entity.Notice;
import com.glassvue.domain.notice.repository.NoticeRepository;
import com.glassvue.domain.notice.viewcount.NoticeViewCountStore;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * NoticeQueryService 통합 — 실 DB+Redis. get()이 "DB view_count + Redis 미반영분"을 합쳐 주는지 검증.
 * (단위 테스트로는 이 조합을 못 잡음.) @Transactional 로 DB 롤백, Redis 조회수 키는 @AfterEach 정리.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@Transactional
class NoticeQueryServiceIntegrationTest {

    @Autowired NoticeQueryService queryService;
    @Autowired NoticeRepository noticeRepository;
    @Autowired NoticeViewCountStore viewCountStore;
    @Autowired StringRedisTemplate redis;

    private UUID noticeId;

    @AfterEach
    void cleanupRedis() {
        if (noticeId != null) {
            redis.delete("notice:view:" + noticeId); // 롤백 안 되는 Redis 조회수 키 정리
        }
    }

    @Test
    @DisplayName("조회수 = DB값(0) + Redis 미반영분(2) = 2 로 합산")
    void viewCountMerge() {
        Notice n = noticeRepository.save(
                Notice.builder().title("t").content("c").author("nick").pinned(false).build());
        noticeId = n.getId();
        viewCountStore.increment(noticeId);
        viewCountStore.increment(noticeId);

        NoticeResponse r = queryService.get(noticeId);
        assertThat(r.viewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("없는 공지 → NOTICE_NOT_FOUND")
    void notFound() {
        assertThatThrownBy(() -> queryService.get(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
    }
}

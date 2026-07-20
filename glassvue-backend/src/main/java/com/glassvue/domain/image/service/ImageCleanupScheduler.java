package com.glassvue.domain.image.service;

import com.glassvue.domain.image.config.ImageProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 고아 이미지(업로드만 하고 어디에도 붙이지 않은 것) 주기 정리.
 *
 * <p>이게 필요해진 배경: 2026-07-20에 포토 리뷰 때문에 이미지 업로드를 일반 사용자에게 열었다.
 * 그전엔 ADMIN만 올려서 사실상 안 쌓였지만, 이제 사진을 올렸다가 리뷰 등록을 취소하면 그대로 남는다.
 *
 * <p>Spring Batch는 아직 도입 전이고 이 정도 작업엔 과하다 —
 * {@code @Scheduled}로 충분하다(공지 조회수 플러시와 같은 방식).
 * 다중 인스턴스가 되면 중복 실행되므로, 그때는 분산 락이나 배치 잡으로 옮겨야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageCleanupScheduler {

    private final ImageService imageService;
    private final ImageProperties imageProperties;

    /** 1시간마다. 기동 직후 바로 돌지 않도록 지연을 둔다(검증 시 짧게 덮어쓸 수 있게 property로). */
    @Scheduled(fixedDelayString = "${image.cleanup-interval-ms:3600000}",
               initialDelayString = "${image.cleanup-initial-delay-ms:300000}")
    public void sweep() {
        if (!imageProperties.cleanupEnabled()) {
            return;
        }
        Instant threshold = Instant.now().minus(Duration.ofHours(imageProperties.cleanupGraceHours()));
        int deleted = imageService.sweepOrphans(threshold);
        if (deleted > 0) {
            log.info("[이미지] 고아 이미지 {}건 정리 (기준: {} 이전 업로드)", deleted, threshold);
        }
    }
}

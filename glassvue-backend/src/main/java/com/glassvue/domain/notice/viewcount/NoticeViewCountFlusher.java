package com.glassvue.domain.notice.viewcount;

import java.util.Map;
import java.util.UUID;
import com.glassvue.domain.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redis에 쌓인 조회수 누적분을 주기적으로 DB에 반영한다.
 * 주의: drainAll(Redis GETDEL)과 DB 반영은 원자적이지 않다. DB 반영 실패 시 그 주기의 증가분은
 * 유실될 수 있다(연습 단계에선 허용). 엄밀함이 필요하면 outbox/재시도 도입.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeViewCountFlusher {

    private final NoticeViewCountStore store;
    private final NoticeRepository noticeRepository;

    @Scheduled(fixedDelayString = "${notice.view-count.flush-interval-ms:30000}")
    @Transactional
    public void flush() {
        Map<UUID, Long> deltas = store.drainAll();
        if (deltas.isEmpty()) {
            return;
        }
        deltas.forEach(noticeRepository::addViewCount);
        log.info("조회수 플러시: {}건 DB 반영", deltas.size());
    }
}

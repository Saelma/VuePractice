package com.glassvue.domain.notice.service.query;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.glassvue.domain.notice.dto.NoticeResponse;
import com.glassvue.domain.notice.dto.NoticeSearchCondition;
import com.glassvue.domain.notice.entity.Notice;
import com.glassvue.domain.notice.repository.NoticeRepository;
import com.glassvue.domain.notice.viewcount.NoticeViewCountStore;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 조회(query) — 단건 · 목록 검색. 읽기 전용 트랜잭션.
 * 조회수는 "DB 값 + Redis 미반영분"을 합쳐 최신 값으로 보여준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeQueryService {

    private final NoticeRepository noticeRepository;
    private final NoticeViewCountStore viewCountStore;

    public NoticeResponse get(UUID id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        long viewCount = notice.getViewCount() + viewCountStore.getPending(id);
        return NoticeResponse.from(notice, viewCount);
    }

    public PageResponse<NoticeResponse> search(NoticeSearchCondition condition, Pageable pageable) {
        Page<Notice> page = noticeRepository.search(condition, pageable);

        List<UUID> ids = page.getContent().stream().map(Notice::getId).toList();
        Map<UUID, Long> pending = viewCountStore.getPending(ids);

        Page<NoticeResponse> mapped = page.map(n ->
                NoticeResponse.from(n, n.getViewCount() + pending.getOrDefault(n.getId(), 0L)));
        return PageResponse.from(mapped);
    }
}

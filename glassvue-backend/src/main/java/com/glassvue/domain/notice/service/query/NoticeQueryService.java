package com.glassvue.domain.notice.service.query;

import java.util.UUID;
import com.glassvue.domain.notice.dto.NoticeResponse;
import com.glassvue.domain.notice.dto.NoticeSearchCondition;
import com.glassvue.domain.notice.entity.Notice;
import com.glassvue.domain.notice.repository.NoticeRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 조회(query) — 단건 · 목록 검색. 읽기 전용 트랜잭션.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeQueryService {

    private final NoticeRepository noticeRepository;

    public NoticeResponse get(UUID id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        return NoticeResponse.from(notice);
    }

    public PageResponse<NoticeResponse> search(NoticeSearchCondition condition, Pageable pageable) {
        return PageResponse.from(
                noticeRepository.search(condition, pageable).map(NoticeResponse::from));
    }
}

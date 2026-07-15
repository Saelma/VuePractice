package kr.co.ecstel.esp.domain.notice.service.query;

import java.util.UUID;
import kr.co.ecstel.esp.domain.notice.dto.NoticeResponse;
import kr.co.ecstel.esp.domain.notice.dto.NoticeSearchCondition;
import kr.co.ecstel.esp.domain.notice.entity.Notice;
import kr.co.ecstel.esp.domain.notice.repository.NoticeRepository;
import kr.co.ecstel.esp.global.exception.BusinessException;
import kr.co.ecstel.esp.global.exception.ErrorCode;
import kr.co.ecstel.esp.global.response.PageResponse;
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

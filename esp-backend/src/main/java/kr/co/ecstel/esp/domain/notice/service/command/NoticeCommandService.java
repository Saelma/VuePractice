package kr.co.ecstel.esp.domain.notice.service.command;

import java.util.UUID;
import kr.co.ecstel.esp.domain.notice.dto.NoticeCreateRequest;
import kr.co.ecstel.esp.domain.notice.dto.NoticeUpdateRequest;
import kr.co.ecstel.esp.domain.notice.entity.Notice;
import kr.co.ecstel.esp.domain.notice.repository.NoticeRepository;
import kr.co.ecstel.esp.global.exception.BusinessException;
import kr.co.ecstel.esp.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 조작(command) — 등록 · 수정 · 삭제.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NoticeCommandService {

    private final NoticeRepository noticeRepository;

    public UUID create(NoticeCreateRequest req) {
        Notice notice = Notice.builder()
                .title(req.title())
                .content(req.content())
                .author(req.author())
                .pinned(req.pinned())
                .build();
        Notice saved = noticeRepository.save(notice);
        log.info("Notice created: id={}", saved.getId());
        return saved.getId();
    }

    public void update(UUID id, NoticeUpdateRequest req) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        notice.update(req.title(), req.content(), req.pinned());
    }

    public void delete(UUID id) {
        if (!noticeRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
        noticeRepository.deleteById(id);
    }
}

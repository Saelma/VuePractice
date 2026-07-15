package com.glassvue.domain.notice.service.command;

import java.util.UUID;
import com.glassvue.domain.notice.dto.NoticeCreateRequest;
import com.glassvue.domain.notice.dto.NoticeUpdateRequest;
import com.glassvue.domain.notice.entity.Notice;
import com.glassvue.domain.notice.repository.NoticeRepository;
import com.glassvue.domain.notice.viewcount.NoticeViewCountStore;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 조작(command) — 등록 · 수정 · 삭제 · 조회수 증가.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeCommandService {

    private final NoticeRepository noticeRepository;
    private final NoticeViewCountStore viewCountStore;

    @Transactional
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

    @Transactional
    public void update(UUID id, NoticeUpdateRequest req) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        notice.update(req.title(), req.content(), req.pinned());
    }

    @Transactional
    public void delete(UUID id) {
        if (!noticeRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
        noticeRepository.deleteById(id);
    }

    /**
     * 조회수 증가 — DB가 아니라 Redis에 누적(INCR). DB 반영은 Flusher가 주기적으로.
     * 성능을 위해 존재 여부를 확인하지 않는다(없는 id의 누적분은 플러시 때 조용히 버려짐).
     */
    public void increaseView(UUID id) {
        viewCountStore.increment(id);
    }
}

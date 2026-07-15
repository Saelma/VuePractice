package com.glassvue.domain.notice.service.command;

import java.util.UUID;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.notice.dto.NoticeCreateRequest;
import com.glassvue.domain.notice.dto.NoticeUpdateRequest;
import com.glassvue.domain.notice.entity.Notice;
import com.glassvue.domain.notice.repository.NoticeRepository;
import com.glassvue.domain.notice.viewcount.NoticeViewCountStore;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
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

    @CacheEvict(cacheNames = "notices:list", allEntries = true)
    @Transactional
    public UUID create(NoticeCreateRequest req, UUID authorId, String author) {
        Notice notice = Notice.builder()
                .title(req.title())
                .content(req.content())
                .author(author)
                .authorId(authorId)
                .pinned(req.pinned())
                .build();
        Notice saved = noticeRepository.save(notice);
        log.info("Notice created: id={} by={}", saved.getId(), authorId);
        return saved.getId();
    }

    @CacheEvict(cacheNames = "notices:list", allEntries = true)
    @Transactional
    public void update(UUID id, NoticeUpdateRequest req, AuthUser user) {
        Notice notice = findManageable(id, user);
        notice.update(req.title(), req.content(), req.pinned());
    }

    @CacheEvict(cacheNames = "notices:list", allEntries = true)
    @Transactional
    public void delete(UUID id, AuthUser user) {
        Notice notice = findManageable(id, user);
        noticeRepository.delete(notice);
    }

    /** 존재 확인 + (본인 글이거나 ADMIN이면) 반환. 아니면 403. */
    private Notice findManageable(UUID id, AuthUser user) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        boolean allowed = user.role() == Role.ADMIN || notice.isOwnedBy(user.id());
        if (!allowed) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_OWNER);
        }
        return notice;
    }

    /**
     * 조회수 증가 — DB가 아니라 Redis에 누적(INCR). DB 반영은 Flusher가 주기적으로.
     * 성능을 위해 존재 여부를 확인하지 않는다(없는 id의 누적분은 플러시 때 조용히 버려짐).
     */
    public void increaseView(UUID id) {
        viewCountStore.increment(id);
    }
}

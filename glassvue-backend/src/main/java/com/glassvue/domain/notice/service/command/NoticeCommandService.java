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
    public void update(UUID id, NoticeUpdateRequest req) {
        Notice notice = find(id);
        notice.update(req.title(), req.content(), req.pinned());
    }

    @CacheEvict(cacheNames = "notices:list", allEntries = true)
    @Transactional
    public void delete(UUID id) {
        noticeRepository.delete(find(id));
    }

    /**
     * 존재 확인만 한다 — <b>권한은 여기서 안 본다</b> (2026-08-20, BACKLOG E-4).
     *
     * <p>🔴 <b>전에는 {@code user.isAdmin() || notice.isOwnedBy(user.id())} 였다.</b> 공지가
     * 관리자 전용이 되면서 <b>{@code isAdmin()} 이 항상 참</b>이라 소유권 갈래에 도달할 수 없다 —
     * 남겨 두면 <b>«지키고 있다» 는 착각만 만드는 죽은 코드</b>가 된다(2026-08-04 M2 의 교훈:
     * 같은 규칙을 두 곳이 지키면 한쪽은 죽은 코드다).
     *
     * <p>⚠ <b>«앱과 DB 가 이중으로 지킨다»(V36 가입 쿠폰)와 갈리는 지점이다.</b> 거기는 두 층의
     * <b>실패 모드가 달라서</b>(동시 요청은 앱이 못 막고 DB 가 막는다) 둘 다 값을 했다.
     * 여기는 <b>같은 프로세스 안 두 겹</b>이라 뒤쪽이 하는 일이 없다.
     *
     * <p>→ 권한은 {@code SecurityConfig} <b>한 곳</b>이다. 관리자 아닌 요청은 여기 닿지 않는다.
     */
    private Notice find(UUID id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
    }

    /**
     * 조회수 증가 — DB가 아니라 Redis에 누적(INCR). DB 반영은 Flusher가 주기적으로.
     * 성능을 위해 존재 여부를 확인하지 않는다(없는 id의 누적분은 플러시 때 조용히 버려짐).
     */
    public void increaseView(UUID id) {
        viewCountStore.increment(id);
    }
}

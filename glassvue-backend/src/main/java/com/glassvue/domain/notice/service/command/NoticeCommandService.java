package com.glassvue.domain.notice.service.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 공지 등록. 관리자 조작이라 원장에 남긴다 (2026-08-21, V56 — 감사 확대 4차).
     *
     * <p>⚠ <b>여기서는 작성자와 행위자가 같다</b> — 그래서 파라미터를 늘리지 않았다.
     * 수정·삭제는 다르다(남이 고칠 수 있다) → 거기만 {@code AuthUser} 를 받는다.
     */
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
        eventPublisher.publishEvent(new AdminActionEvent(
                AuditAction.NOTICE_CREATE, authorId, author, saved.getId(), null, describe(saved)));
        return saved.getId();
    }

    /**
     * 공지 수정 — <b>바뀐 것만 «전→후»</b> 로 원장에 남긴다 (2026-08-21, V56).
     *
     * <p>🔴 <b>등록자가 아니라 «지금 누른 사람» 이 행위자다.</b> 공지는 관리자 아무나 고칠 수 있어
     * {@code notice.getAuthor()} 를 쓰면 <b>남이 고친 것을 등록자가 한 것처럼</b> 적게 된다.
     * ⚠ 바로 그래서 이 감사가 값을 한다 — 수정은 그전까지 «누가» 를 아무 데도 안 남겼다.
     */
    @CacheEvict(cacheNames = "notices:list", allEntries = true)
    @Transactional
    public void update(UUID id, NoticeUpdateRequest req, AuthUser actor) {
        Notice notice = find(id);
        // ⚠ 바꾸기 **전에** 읽는다 — update() 뒤에 읽으면 전/후가 같아져 «변경 없음» 만 나온다.
        NoticeSnapshot before = NoticeSnapshot.of(notice);
        notice.update(req.title(), req.content(), req.pinned());
        eventPublisher.publishEvent(new AdminActionEvent(
                AuditAction.NOTICE_UPDATE, actor.id(), actor.nickname(), id, null,
                describeChanges(before, NoticeSnapshot.of(notice))));
    }

    /**
     * 공지 삭제 — 🔴 <b>되돌릴 수 없다.</b> 공지에는 유예(F-7)가 없어 행이 진짜로 사라진다.
     * 그래서 {@code detail} 의 제목이 <b>유일하게 남는 흔적</b>이다 (2026-08-21, V56).
     */
    @CacheEvict(cacheNames = "notices:list", allEntries = true)
    @Transactional
    public void delete(UUID id, AuthUser actor) {
        Notice notice = find(id);
        // ⚠ 지우기 **전에** 읽는다(CATEGORY_DELETE·DISCOUNT_DELETE 와 같은 자리).
        String detail = describe(notice);
        noticeRepository.delete(notice);
        eventPublisher.publishEvent(new AdminActionEvent(
                AuditAction.NOTICE_DELETE, actor.id(), actor.nickname(), id, null, detail));
    }

    /**
     * 감사 {@code detail} 에 적을 «전» 상태.
     *
     * <p>⚠ <b>본문을 그대로 담는다</b> — 비교에만 쓰고 원장에는 «바뀜» 만 나간다
     * ({@link #describeChanges} 가 접는다). 담지 않으면 «본문이 바뀌었나» 를 알 방법이 없다.
     *
     * <p>⚠ <b>조회수는 담지 않는다</b> — 관리자가 정하는 값이 아니다(Redis 가 올린다).
     * 담으면 <b>손대지 않아도 늘 «바뀜»</b> 이라 정보가 아니라 소음이 된다
     * (상품이 이미지 그룹을 안 담은 것과 같은 이유 — V53).
     */
    private record NoticeSnapshot(String title, String content, boolean pinned) {

        static NoticeSnapshot of(Notice notice) {
            return new NoticeSnapshot(notice.getTitle(), notice.getContent(), notice.isPinned());
        }
    }

    /** 등록·삭제의 {@code detail} — 제목과 고정 여부. 그 조작이 정한 것이 그 둘이다. */
    private String describe(Notice notice) {
        return notice.isPinned() ? notice.getTitle() + " · 고정" : notice.getTitle();
    }

    /**
     * «무엇이 바뀌었나» 를 한 줄로 — {@code PRODUCT_UPDATE}(V53)의 규칙을 그대로 따른다.
     *
     * <p>🔴 <b>본문은 «바뀜» 만 적는다.</b> 공지 본문은 상품 설명보다 길어서 전/후를 다 실으면
     * {@code detail}(1000자)을 확실히 넘긴다 — <b>잘린 원장은 틀린 원장이다.</b>
     *
     * <p>⚠ 바뀐 것이 하나도 없어도 «변경 없음» 으로 <b>줄은 남긴다</b>. 관리 화면이 공지 전체를
     * 다시 보내 흔한 경우인데, «누가 언제 손댔나» 자체를 접근 기록으로 본다(V53 과 같은 선택).
     */
    private String describeChanges(NoticeSnapshot before, NoticeSnapshot after) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(before.title(), after.title())) {
            changes.add("제목 " + before.title() + "→" + after.title());
        }
        if (before.pinned() != after.pinned()) {
            changes.add(before.pinned() ? "고정 해제" : "상단 고정");
        }
        if (!Objects.equals(before.content(), after.content())) {
            changes.add("본문 바뀜");
        }
        return changes.isEmpty() ? "변경 없음" : String.join(" · ", changes);
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

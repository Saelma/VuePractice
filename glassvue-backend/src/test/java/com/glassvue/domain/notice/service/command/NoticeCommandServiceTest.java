package com.glassvue.domain.notice.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.notice.dto.NoticeCreateRequest;
import com.glassvue.domain.notice.dto.NoticeUpdateRequest;
import com.glassvue.domain.notice.entity.Notice;
import com.glassvue.domain.notice.repository.NoticeRepository;
import com.glassvue.domain.notice.viewcount.NoticeViewCountStore;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NoticeCommandServiceTest {

    @Mock NoticeRepository noticeRepository;
    @Mock NoticeViewCountStore viewCountStore;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks NoticeCommandService service;

    private final AuthUser user = new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin");

    private Notice noticeBy(UUID authorId) {
        return Notice.builder().title("t").content("c").author("nick").authorId(authorId).pinned(false).build();
    }
    /** 원장에 나간 이벤트 하나를 집어 온다(2026-08-21, V56). */
    private AdminActionEvent capturedEvent() {
        ArgumentCaptor<AdminActionEvent> captor = ArgumentCaptor.forClass(AdminActionEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("작성: 저장 후 id 반환")
    void create() {
        when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID id = service.create(new NoticeCreateRequest("제목", "본문", false), user.id(), user.nickname());
        assertThat(id).isNotNull();
        verify(noticeRepository).save(any(Notice.class));
    }

    @Test
    @DisplayName("수정: 없는 글 → NOTICE_NOT_FOUND")
    void update_notFound() {
        when(noticeRepository.findById(any())).thenReturn(Optional.empty());
        assertErrorCode(() -> service.update(UUID.randomUUID(), new NoticeUpdateRequest("t", "c", false), user),
                ErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    @DisplayName("수정: 남의 글도 고친다 — 🔴 공지는 관리자 콘텐츠라 소유권 개념이 없다")
    void update_anyNotice() {
        // ⚠ 2026-08-20(BACKLOG E-4) 전에는 «본인 글만» 이었고, 남의 글이면 NOTICE_NOT_OWNER 였다.
        //    공지가 관리자 전용이 되면서 그 갈래는 **도달할 수 없다** — 여기 오는 요청은 이미 관리자다.
        //    🔴 권한은 SecurityConfig 한 곳이 본다(서비스에 같은 규칙을 또 두면 죽은 코드가 된다).
        //    «관리자 아닌 요청이 막히는가» 는 AuthFlowIntegrationTest 가 403 으로 본다.
        Notice other = noticeBy(UUID.randomUUID());
        when(noticeRepository.findById(any())).thenReturn(Optional.of(other));
        service.update(UUID.randomUUID(), new NoticeUpdateRequest("새제목", "새본문", true), user);
        assertThat(other.getTitle()).isEqualTo("새제목");
        assertThat(other.isPinned()).isTrue();
    }

    @Test
    @DisplayName("삭제: 남의 글도 삭제된다")
    void delete_anyNotice() {
        Notice other = noticeBy(UUID.randomUUID());
        when(noticeRepository.findById(any())).thenReturn(Optional.of(other));
        service.delete(UUID.randomUUID(), user);
        verify(noticeRepository).delete(other);
    }

    @Test
    @DisplayName("등록: 원장에 NOTICE_CREATE — detail 은 제목 (2026-08-21, V56)")
    void create_audit() {
        when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(new NoticeCreateRequest("점검 안내", "본문", false), user.id(), user.nickname());

        AdminActionEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo(AuditAction.NOTICE_CREATE);
        assertThat(event.detail()).isEqualTo("점검 안내");
        // 🔴 대상이 회원이 아니다 — 「대상 아이디」로 못 찾고 target_type(NOTICE)으로만 걸린다.
        assertThat(event.targetLogin()).isNull();
    }

    @Test
    @DisplayName("등록: 상단 고정이면 detail 에 적힌다 — 그 조작이 정한 것이 제목과 고정 여부다")
    void create_audit_pinned() {
        when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create(new NoticeCreateRequest("긴급", "본문", true), user.id(), user.nickname());
        assertThat(capturedEvent().detail()).isEqualTo("긴급 · 고정");
    }

    @Test
    @DisplayName("🔴 수정: **바뀐 것만** «전→후» 로 적는다 — 본문은 «바뀜» 만 (V53 규칙 그대로)")
    void update_audit_changes() {
        Notice notice = noticeBy(UUID.randomUUID());  // title=t · content=c · pinned=false
        when(noticeRepository.findById(any())).thenReturn(Optional.of(notice));
        service.update(UUID.randomUUID(), new NoticeUpdateRequest("새제목", "새본문", true), user);

        AdminActionEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo(AuditAction.NOTICE_UPDATE);
        // ⚠ 본문을 전/후로 다 실으면 detail(1000자)을 넘긴다 — «바뀜» 으로 접는 것이 규칙이다.
        assertThat(event.detail()).isEqualTo("제목 t→새제목 · 상단 고정 · 본문 바뀜");
    }

    @Test
    @DisplayName("🔴 수정: 바뀐 것이 없어도 «변경 없음» 으로 줄을 남긴다 — 접근 기록으로 본다")
    void update_audit_noChange() {
        // 관리 화면이 공지 전체를 다시 보내므로 흔한 경우다. PRODUCT_UPDATE(V53)와 같은 선택 —
        // 조작이 **실제로 저장까지 갔으므로** «누가 언제 손댔나» 를 남긴다.
        Notice notice = noticeBy(UUID.randomUUID());
        when(noticeRepository.findById(any())).thenReturn(Optional.of(notice));
        service.update(UUID.randomUUID(), new NoticeUpdateRequest("t", "c", false), user);
        assertThat(capturedEvent().detail()).isEqualTo("변경 없음");
    }

    @Test
    @DisplayName("🔴 수정: 행위자는 등록자가 아니라 **지금 누른 사람**이다")
    void update_audit_actorIsNotAuthor() {
        // 공지는 관리자 아무나 고칠 수 있다. author 를 쓰면 «남이 고친 것을 등록자가 한 것처럼» 적힌다 —
        // 이 감사가 값을 하는 이유가 정확히 그 자리다(수정은 그전까지 «누가» 를 안 남겼다).
        UUID otherAuthor = UUID.randomUUID();
        Notice notice = noticeBy(otherAuthor);
        when(noticeRepository.findById(any())).thenReturn(Optional.of(notice));
        service.update(UUID.randomUUID(), new NoticeUpdateRequest("고침", "c", false), user);

        AdminActionEvent event = capturedEvent();
        assertThat(event.actorId()).isEqualTo(user.id());
        assertThat(event.actorId()).isNotEqualTo(otherAuthor);
    }

    @Test
    @DisplayName("🔴 삭제: 제목을 **지우기 전에** 읽는다 — 공지엔 soft delete 가 없다 (V56)")
    void delete_audit() {
        UUID id = UUID.randomUUID();
        Notice notice = Notice.builder().title("사라질공지").content("c")
                .author("nick").authorId(UUID.randomUUID()).pinned(false).build();
        when(noticeRepository.findById(any())).thenReturn(Optional.of(notice));
        service.delete(id, user);

        AdminActionEvent event = capturedEvent();
        assertThat(event.action()).isEqualTo(AuditAction.NOTICE_DELETE);
        assertThat(event.detail()).isEqualTo("사라질공지");
        assertThat(event.targetId()).isEqualTo(id);
    }

    @Test
    @DisplayName("조회수 증가는 Redis 스토어에 위임(존재 확인 안 함)")
    void increaseView() {
        UUID id = UUID.randomUUID();
        service.increaseView(id);
        verify(viewCountStore).increment(id);
        // ⚠ 원장에 안 남긴다 — 고객이 읽은 것이고 관리자 조작이 아니다(2026-08-21, V56).
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(AdminActionEvent.class));
    }
}

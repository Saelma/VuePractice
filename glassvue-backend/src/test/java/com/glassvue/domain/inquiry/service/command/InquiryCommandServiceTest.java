package com.glassvue.domain.inquiry.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.event.AdminActionEvent;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.image.service.ImageService;
import com.glassvue.domain.inquiry.dto.InquiryAnswerRequest;
import com.glassvue.domain.inquiry.dto.InquiryCreateRequest;
import com.glassvue.domain.inquiry.dto.InquiryUpdateRequest;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryType;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.domain.inquiry.event.InquiryAnsweredEvent;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.service.MemberService;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class InquiryCommandServiceTest {

    @Mock InquiryRepository inquiryRepository;
    @Mock ProductQueryService productQueryService;
    @Mock ImageService imageService;
    @Mock MemberService memberService;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks InquiryCommandService service;

    private final AuthUser user = new AuthUser(UUID.randomUUID(), Role.USER, "kim");
    private final AuthUser admin = new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin");

    private Inquiry inquiryBy(UUID authorId) {
        return Inquiry.builder().productId(UUID.randomUUID()).type(InquiryType.PRODUCT).authorId(authorId)
                .author("nick").title("t").content("c").secret(false).build();
    }
    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    /**
     * 나간 이벤트 중 원하는 종류만 골라 온다.
     *
     * <p>⚠ <b>답변 하나가 이벤트 <b>둘</b>을 낸다</b>(2026-08-21, V56): 고객 알림({@code InquiryAnsweredEvent})
     * 과 감사({@code AdminActionEvent}). 그래서 «한 번 발행됐다» 로는 못 세고 <b>종류로 갈라</b> 봐야 한다.
     */
    private <T> List<T> publishedEventsOf(Class<T> type) {
        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    @DisplayName("답변: 본인이 등록한 문의엔 답변 불가 → INQUIRY_SELF_ANSWER")
    void answer_self() {
        Inquiry mine = inquiryBy(admin.id()); // 관리자가 자기 문의에 답변 시도
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(mine));
        assertErrorCode(() -> service.answer(UUID.randomUUID(), new InquiryAnswerRequest("셀프"), admin),
                ErrorCode.INQUIRY_SELF_ANSWER);
    }

    @Test
    @DisplayName("답변: 타인 문의 → ANSWERED로 전이")
    void answer_other() {
        Inquiry q = inquiryBy(user.id());
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(q));
        service.answer(UUID.randomUUID(), new InquiryAnswerRequest("네 답변드립니다"), admin);
        assertThat(q.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
        assertThat(q.getAnswer()).isEqualTo("네 답변드립니다");
    }

    @Test
    @DisplayName("수정: 남의 문의 → INQUIRY_NOT_OWNER")
    void update_notOwner() {
        Inquiry other = inquiryBy(UUID.randomUUID());
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(other));
        assertErrorCode(() -> service.update(UUID.randomUUID(), new InquiryUpdateRequest("t", "c", false, null), user),
                ErrorCode.INQUIRY_NOT_OWNER);
    }

    @Test
    @DisplayName("수정: 답변 완료된 문의 → INQUIRY_ALREADY_ANSWERED")
    void update_answered() {
        Inquiry mine = inquiryBy(user.id());
        mine.answer("답변"); // ANSWERED 상태로
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(mine));
        assertErrorCode(() -> service.update(UUID.randomUUID(), new InquiryUpdateRequest("t", "c", false, null), user),
                ErrorCode.INQUIRY_ALREADY_ANSWERED);
    }

    @Test
    @DisplayName("수정: 본인·답변 전 → 반영")
    void update_ownerWaiting() {
        Inquiry mine = inquiryBy(user.id());
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(mine));
        service.update(UUID.randomUUID(), new InquiryUpdateRequest("새제목", "새내용", true, null), user);
        assertThat(mine.getTitle()).isEqualTo("새제목");
        assertThat(mine.isSecret()).isTrue();
    }

    @Test
    @DisplayName("작성: imageIds로 그룹 생성 후 그 그룹으로 저장")
    void create_withImages() {
        UUID newGroup = UUID.randomUUID();
        List<UUID> imageIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(imageService.createGroup(imageIds)).thenReturn(newGroup);
        when(inquiryRepository.save(any(Inquiry.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(UUID.randomUUID(),
                new InquiryCreateRequest("제목", "내용", false, imageIds), user);

        verify(imageService).createGroup(imageIds);
        verify(inquiryRepository).save(any(Inquiry.class));
    }

    @Test
    @DisplayName("수정: 이미지 그룹 교체 — createGroup 뒤에 옛 그룹 deleteGroup (순서 중요)")
    void update_replacesImageGroup() {
        UUID oldGroup = UUID.randomUUID();
        UUID newGroup = UUID.randomUUID();
        Inquiry mine = Inquiry.builder().productId(UUID.randomUUID()).type(InquiryType.PRODUCT).authorId(user.id())
                .author("nick").title("t").content("c").secret(false).imageGroupId(oldGroup).build();
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(mine));
        List<UUID> imageIds = List.of(UUID.randomUUID());
        when(imageService.createGroup(imageIds)).thenReturn(newGroup);

        service.update(UUID.randomUUID(), new InquiryUpdateRequest("t", "c", false, imageIds), user);

        assertThat(mine.getImageGroupId()).isEqualTo(newGroup);
        // 유지할 이미지를 새 그룹으로 재할당한 뒤라야 옛 그룹엔 뺀 이미지만 남는다 → createGroup이 먼저.
        InOrder order = inOrder(imageService);
        order.verify(imageService).createGroup(imageIds);
        order.verify(imageService).deleteGroup(oldGroup);
    }

    @Test
    @DisplayName("삭제: 관리자는 남의 문의도 삭제 가능 + 첨부 그룹도 정리")
    void delete_admin() {
        UUID group = UUID.randomUUID();
        Inquiry other = Inquiry.builder().productId(UUID.randomUUID()).type(InquiryType.PRODUCT).authorId(UUID.randomUUID())
                .author("nick").title("t").content("c").secret(false).imageGroupId(group).build();
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(other));
        service.delete(UUID.randomUUID(), admin);
        verify(inquiryRepository).delete(other);
        verify(imageService).deleteGroup(group);
    }

    // --- 답변 알림 이벤트 (B-15, 2026-07-31) ---

    @Test
    @DisplayName("답변: 첫 답변이면 InquiryAnsweredEvent 발행 — 작성자·상품·제목이 실린다")
    void answer_publishesEventOnFirstAnswer() {
        Inquiry q = inquiryBy(user.id());
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(q));

        service.answer(UUID.randomUUID(), new InquiryAnswerRequest("네 답변드립니다"), admin);

        List<InquiryAnsweredEvent> events = publishedEventsOf(InquiryAnsweredEvent.class);
        assertThat(events).hasSize(1);
        InquiryAnsweredEvent event = events.get(0);
        assertThat(event.authorId()).isEqualTo(user.id());        // 답변자(admin)가 아니라 **작성자**에게 간다
        assertThat(event.productId()).isEqualTo(q.getProductId());
        assertThat(event.inquiryTitle()).isEqualTo("t");          // 문구에 어느 문의인지가 있어야 쓸모가 있다
    }

    @Test
    @DisplayName("⚠ 답변 **수정**은 이벤트를 발행하지 않는다 — 오타 고칠 때마다 알림이 가면 안 된다")
    void answer_doesNotPublishOnEdit() {
        Inquiry q = inquiryBy(user.id());
        q.answer("첫 답변");                                        // 이미 ANSWERED
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(q));

        service.answer(UUID.randomUUID(), new InquiryAnswerRequest("고친 답변"), admin);

        assertThat(q.getAnswer()).isEqualTo("고친 답변");            // 수정 자체는 된다
        verify(eventPublisher, never()).publishEvent(any(InquiryAnsweredEvent.class));
        // 🔴 **알림은 안 가지만 원장에는 남는다**(2026-08-21, V56) — 그리고 그 줄이 스스로
        //    «알림은 안 갔다» 를 말한다. 둘이 갈리는 자리라 여기서 못 박아 둔다.
        List<AdminActionEvent> audits = publishedEventsOf(AdminActionEvent.class);
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).action()).isEqualTo(AuditAction.INQUIRY_ANSWER);
        assertThat(audits.get(0).detail()).endsWith("· 답변 수정");
    }

    @Test
    @DisplayName("🔴 답변: 원장의 대상은 답변자가 아니라 **질문자**다 — 숨김과 같은 target_id 로 묶인다")
    void answer_recordsAuditTargetingAsker() {
        Inquiry q = inquiryBy(user.id());
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(q));
        when(memberService.loginIdOf(user.id())).thenReturn("kim1");

        service.answer(UUID.randomUUID(), new InquiryAnswerRequest("네"), admin);

        List<AdminActionEvent> audits = publishedEventsOf(AdminActionEvent.class);
        assertThat(audits).hasSize(1);
        AdminActionEvent audit = audits.get(0);
        assertThat(audit.action()).isEqualTo(AuditAction.INQUIRY_ANSWER);
        assertThat(audit.actorId()).isEqualTo(admin.id());
        assertThat(audit.targetId()).isEqualTo(user.id());
        assertThat(audit.targetLogin()).isEqualTo("kim1");
        // 🔴 detail 이 «알림이 나갔나» 를 답한다 — 첫 답변에만 나가고 회수할 수 없다.
        assertThat(audit.detail()).isEqualTo("t · 첫 답변");
    }

    @Test
    @DisplayName("답변: 본인 문의라 거부되면 이벤트도 없다")
    void answer_selfAnswer_publishesNothing() {
        Inquiry mine = inquiryBy(admin.id());
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(mine));

        assertErrorCode(() -> service.answer(UUID.randomUUID(), new InquiryAnswerRequest("셀프"), admin),
                ErrorCode.INQUIRY_SELF_ANSWER);

        verify(eventPublisher, never()).publishEvent(any(InquiryAnsweredEvent.class));
        // ⚠ 원장도 마찬가지다 — 막힌 요청은 「일어난 일」이 아니다(2026-08-21, V56).
        verify(eventPublisher, never()).publishEvent(any(AdminActionEvent.class));
    }
}

package com.glassvue.domain.inquiry.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.inquiry.dto.InquiryAnswerRequest;
import com.glassvue.domain.inquiry.dto.InquiryUpdateRequest;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.security.AuthUser;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InquiryCommandServiceTest {

    @Mock InquiryRepository inquiryRepository;
    @Mock ProductQueryService productQueryService;
    @InjectMocks InquiryCommandService service;

    private final AuthUser user = new AuthUser(UUID.randomUUID(), Role.USER, "kim");
    private final AuthUser admin = new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin");

    private Inquiry inquiryBy(UUID authorId) {
        return Inquiry.builder().productId(UUID.randomUUID()).authorId(authorId)
                .author("nick").title("t").content("c").secret(false).build();
    }
    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
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
        assertErrorCode(() -> service.update(UUID.randomUUID(), new InquiryUpdateRequest("t", "c", false), user),
                ErrorCode.INQUIRY_NOT_OWNER);
    }

    @Test
    @DisplayName("수정: 답변 완료된 문의 → INQUIRY_ALREADY_ANSWERED")
    void update_answered() {
        Inquiry mine = inquiryBy(user.id());
        mine.answer("답변"); // ANSWERED 상태로
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(mine));
        assertErrorCode(() -> service.update(UUID.randomUUID(), new InquiryUpdateRequest("t", "c", false), user),
                ErrorCode.INQUIRY_ALREADY_ANSWERED);
    }

    @Test
    @DisplayName("수정: 본인·답변 전 → 반영")
    void update_ownerWaiting() {
        Inquiry mine = inquiryBy(user.id());
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(mine));
        service.update(UUID.randomUUID(), new InquiryUpdateRequest("새제목", "새내용", true), user);
        assertThat(mine.getTitle()).isEqualTo("새제목");
        assertThat(mine.isSecret()).isTrue();
    }

    @Test
    @DisplayName("삭제: 관리자는 남의 문의도 삭제 가능")
    void delete_admin() {
        Inquiry other = inquiryBy(UUID.randomUUID());
        when(inquiryRepository.findById(any())).thenReturn(Optional.of(other));
        service.delete(UUID.randomUUID(), admin);
        verify(inquiryRepository).delete(other);
    }
}

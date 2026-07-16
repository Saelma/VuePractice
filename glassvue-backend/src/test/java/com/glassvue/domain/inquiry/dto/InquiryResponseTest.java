package com.glassvue.domain.inquiry.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.global.security.AuthUser;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 비밀글 마스킹 규칙(작성자·ADMIN만 열람)의 순수 단위 테스트. */
class InquiryResponseTest {

    private static final String BODY = "주소 변경돼요?";
    private final UUID ownerId = UUID.randomUUID();

    private Inquiry secretInquiry() {
        return Inquiry.builder().productId(UUID.randomUUID()).authorId(ownerId)
                .author("nick").title("비밀 배송문의").content(BODY).secret(true).build();
    }
    private Inquiry publicInquiry() {
        return Inquiry.builder().productId(UUID.randomUUID()).authorId(ownerId)
                .author("nick").title("공개문의").content(BODY).secret(false).build();
    }

    @Test
    @DisplayName("비밀글 + 비로그인(null) → 마스킹")
    void secret_anonymous_masked() {
        InquiryResponse r = InquiryResponse.from(secretInquiry(), null);
        assertThat(r.masked()).isTrue();
        assertThat(r.content()).isNotEqualTo(BODY);
    }

    @Test
    @DisplayName("비밀글 + 작성자 → 열람 가능")
    void secret_owner_visible() {
        InquiryResponse r = InquiryResponse.from(secretInquiry(), new AuthUser(ownerId, Role.USER, "me"));
        assertThat(r.masked()).isFalse();
        assertThat(r.content()).isEqualTo(BODY);
    }

    @Test
    @DisplayName("비밀글 + 관리자(타인) → 열람 가능")
    void secret_admin_visible() {
        InquiryResponse r = InquiryResponse.from(secretInquiry(), new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin"));
        assertThat(r.masked()).isFalse();
        assertThat(r.content()).isEqualTo(BODY);
    }

    @Test
    @DisplayName("비밀글 + 타인(일반 사용자) → 마스킹")
    void secret_otherUser_masked() {
        InquiryResponse r = InquiryResponse.from(secretInquiry(), new AuthUser(UUID.randomUUID(), Role.USER, "other"));
        assertThat(r.masked()).isTrue();
        assertThat(r.content()).isNotEqualTo(BODY);
    }

    @Test
    @DisplayName("공개글 + 비로그인 → 그대로 열람")
    void public_anonymous_visible() {
        InquiryResponse r = InquiryResponse.from(publicInquiry(), null);
        assertThat(r.masked()).isFalse();
        assertThat(r.content()).isEqualTo(BODY);
    }
}

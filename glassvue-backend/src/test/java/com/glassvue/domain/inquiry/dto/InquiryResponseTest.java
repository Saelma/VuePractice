package com.glassvue.domain.inquiry.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.image.dto.ImageResponse;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryType;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.global.security.AuthUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 비밀글 마스킹 규칙(작성자·ADMIN만 열람)의 순수 단위 테스트. 마스킹 시 첨부 이미지도 가려야 한다. */
class InquiryResponseTest {

    private static final String BODY = "주소 변경돼요?";
    private static final List<ImageResponse> IMAGES =
            List.of(new ImageResponse(UUID.randomUUID(),
                    "/uploads/inq.png", "/uploads/inq_m.webp", "/uploads/inq_t.webp"));
    private final UUID ownerId = UUID.randomUUID();

    private Inquiry secretInquiry() {
        return Inquiry.builder().productId(UUID.randomUUID()).type(InquiryType.PRODUCT).authorId(ownerId)
                .author("nick").title("비밀 배송문의").content(BODY).secret(true).build();
    }
    private Inquiry publicInquiry() {
        return Inquiry.builder().productId(UUID.randomUUID()).type(InquiryType.PRODUCT).authorId(ownerId)
                .author("nick").title("공개문의").content(BODY).secret(false).build();
    }

    @Test
    @DisplayName("비밀글 + 비로그인(null) → 마스킹 + 이미지 숨김")
    void secret_anonymous_masked() {
        InquiryResponse r = InquiryResponse.from(secretInquiry(), null, IMAGES);
        assertThat(r.masked()).isTrue();
        assertThat(r.content()).isNotEqualTo(BODY);
        assertThat(r.images()).isEmpty(); // 비밀글 사진이 새어 나가면 안 된다
    }

    @Test
    @DisplayName("비밀글 + 작성자 → 열람 가능 + 이미지 노출")
    void secret_owner_visible() {
        InquiryResponse r = InquiryResponse.from(secretInquiry(), new AuthUser(ownerId, Role.USER, "me"), IMAGES);
        assertThat(r.masked()).isFalse();
        assertThat(r.content()).isEqualTo(BODY);
        assertThat(r.images()).isEqualTo(IMAGES);
    }

    @Test
    @DisplayName("비밀글 + 관리자(타인) → 열람 가능")
    void secret_admin_visible() {
        InquiryResponse r = InquiryResponse.from(
                secretInquiry(), new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin"), IMAGES);
        assertThat(r.masked()).isFalse();
        assertThat(r.content()).isEqualTo(BODY);
        assertThat(r.images()).isEqualTo(IMAGES);
    }

    @Test
    @DisplayName("비밀글 + 타인(일반 사용자) → 마스킹 + 이미지 숨김")
    void secret_otherUser_masked() {
        InquiryResponse r = InquiryResponse.from(
                secretInquiry(), new AuthUser(UUID.randomUUID(), Role.USER, "other"), IMAGES);
        assertThat(r.masked()).isTrue();
        assertThat(r.content()).isNotEqualTo(BODY);
        assertThat(r.images()).isEmpty();
    }

    @Test
    @DisplayName("공개글 + 비로그인 → 그대로 열람 + 이미지 노출")
    void public_anonymous_visible() {
        InquiryResponse r = InquiryResponse.from(publicInquiry(), null, IMAGES);
        assertThat(r.masked()).isFalse();
        assertThat(r.content()).isEqualTo(BODY);
        assertThat(r.images()).isEqualTo(IMAGES);
    }
}

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

/**
 * 비밀글 마스킹 규칙(작성자·ADMIN만 열람)의 순수 단위 테스트. 마스킹 시 첨부 이미지도 가려야 한다.
 *
 * <p>🔴 <b>제목도 가린다</b>(2026-09-10, R 축). 화면 체크박스가 «비밀글 (작성자·판매자만 열람)» 이라고
 * <b>약속</b>하는데 본문만 가리면 제목이 그 약속을 깬다 — 실측한 비밀글의 제목은 «이거 자바아닌데?» 로,
 * <b>제목이 곧 질문 전체</b>였다.
 *
 * <p>🔴 <b>{@code authorId} 대신 {@code mine} 을 싣는다.</b> 화면이 그 값으로 하던 일은
 * «내 글인가» 하나뿐인데, 그걸 하려고 <b>남의 회원 UUID 를 공개 목록 전체에</b> 실어야 했다.
 */
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
    @DisplayName("비밀글 + 비로그인 → 제목도 가린다")
    void secret_anonymous_titleMasked() {
        InquiryResponse masked = InquiryResponse.from(secretInquiry(), null, IMAGES);
        assertThat(masked.title()).isNotEqualTo("비밀 배송문의");
        // 대조군 — 공개글은 같은 호출에서 제목이 그대로다(가리는 쪽이 secret 이지 «비로그인» 이 아니다).
        assertThat(InquiryResponse.from(publicInquiry(), null, IMAGES).title()).isEqualTo("공개문의");
    }

    @Test
    @DisplayName("비밀글 + 관리자(타인) → 열람은 되지만 «내 글» 은 아니다")
    void admin_canView_butNotMine() {
        InquiryResponse r = InquiryResponse.from(
                secretInquiry(), new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin"), IMAGES);
        assertThat(r.masked()).isFalse();   // 볼 수 있고
        assertThat(r.mine()).isFalse();     // 내 글은 아니다 — 두 질문은 다르다
    }

    @Test
    @DisplayName("mine 은 작성자에게만 참이다 (비로그인·타인은 거짓)")
    void mine_onlyForOwner() {
        assertThat(InquiryResponse.from(publicInquiry(),
                new AuthUser(ownerId, Role.USER, "me"), IMAGES).mine()).isTrue();
        assertThat(InquiryResponse.from(publicInquiry(), null, IMAGES).mine()).isFalse();
        assertThat(InquiryResponse.from(publicInquiry(),
                new AuthUser(UUID.randomUUID(), Role.USER, "other"), IMAGES).mine()).isFalse();
    }

    @Test
    @DisplayName("작성자 이름은 공개·비밀 모두 비로그인·타인에게 가린다 (R-7)")
    void author_maskedForAnonymousAndOthers() {
        AuthUser other = new AuthUser(UUID.randomUUID(), Role.USER, "other");
        assertThat(InquiryResponse.from(publicInquiry(), null, IMAGES).author()).isEqualTo("n**");
        assertThat(InquiryResponse.from(publicInquiry(), other, IMAGES).author()).isEqualTo("n**");
        assertThat(InquiryResponse.from(secretInquiry(), null, IMAGES).author()).isEqualTo("n**");
    }

    @Test
    @DisplayName("작성자 이름은 본인·관리자에게 원문 (R-7)")
    void author_fullForOwnerAndAdmin() {
        assertThat(InquiryResponse.from(secretInquiry(),
                new AuthUser(ownerId, Role.USER, "me"), IMAGES).author()).isEqualTo("nick");
        assertThat(InquiryResponse.from(secretInquiry(),
                new AuthUser(UUID.randomUUID(), Role.ADMIN, "admin"), IMAGES).author()).isEqualTo("nick");
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

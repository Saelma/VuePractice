package com.glassvue.domain.member.dto;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 회원 목록·기본상세 응답 (B-11).
 *
 * <p>사용자용 {@code MemberResponse}(auth 패키지, 기본 배송지 중심)와 다르다 — 관리자는 배송지 대신
 * 이메일·역할·가입일을 본다. 적립금·등급·주문·반품은 각 도메인(point·order)의 admin 조회로 따로 붙인다
 * (도메인 격리 — member 는 order/point 를 직접 참조하지 않는다).
 */
public record AdminMemberResponse(
        UUID id,
        String loginId,
        String nickname,
        @Schema(description = "이메일(아직 수집 경로가 없어 대개 null)", nullable = true) String email,
        Role role,
        boolean suspended,
        Instant createdAt,

        /*
         * 가입 약관 동의 (B-21, 2026-08-03).
         *
         * ⚠ **조회할 수 없는 동의 기록은 근거 구실을 못 한다** — "동의를 받았다"를 나중에 확인할
         * 방법이 없으면 컬럼을 만든 의미가 절반 사라진다. 그래서 관리자 상세에 실어 준다.
         *
         * ⚠ **`null` 이 정상값이다**(V37 이전 가입자 = 동의 절차가 없던 시절). 화면은 이걸
         * "미동의"가 아니라 **「기록 없음」** 으로 읽어야 한다 — 그 사람들은 거부한 게 아니라
         * 물어본 적이 없는 것이다.
         */
        @Schema(description = "약관 동의 시각(필수 동의). null=동의 기록 없음(V37 이전 가입자)",
                nullable = true) Instant termsAgreedAt,
        @Schema(description = "마케팅 수신 동의 시각(선택). null=미동의", nullable = true)
        Instant marketingAgreedAt
) {
    public static AdminMemberResponse from(Member m) {
        return new AdminMemberResponse(
                m.getId(), m.getLoginId(), m.getNickname(), m.getEmail(), m.getRole(),
                m.isSuspended(), m.getCreatedAt(),
                m.getTermsAgreedAt(), m.getMarketingAgreedAt());
    }
}

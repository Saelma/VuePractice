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
        Instant createdAt
) {
    public static AdminMemberResponse from(Member m) {
        return new AdminMemberResponse(
                m.getId(), m.getLoginId(), m.getNickname(), m.getEmail(), m.getRole(), m.getCreatedAt());
    }
}

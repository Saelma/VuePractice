package com.glassvue.domain.member.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(nullable = false)
    private String password; // BCrypt 해시

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // 이메일은 아직 **수집 경로가 없어** nullable 이다(2026-07-28, V29). 비밀번호 재설정 링크 SMTP
    // 발송(BACKLOG D)을 붙일 때 가입/설정 폼에서 채운다. 계정 식별자라 유일(uk_member_email).
    @Column(unique = true, length = 255)
    private String email;

    // 회원 정지(B-11 후속, 2026-07-28, V30). 정지되면 로그인·토큰갱신·주문이 막힌다(관리자만 조작).
    // NUMBER(1) DEFAULT 0 — 기존 회원은 활성(false).
    @Column(nullable = false)
    private boolean suspended;

    // --- 기본 배송지는 여기 없다 (2026-07-24, V18) ---
    //
    // V11~V17 동안은 ship_recipient·ship_phone·ship_zipcode·ship_address1·ship_address2 5컬럼이
    // 여기 있었다(회원당 배송지 하나). 주소록(MemberAddress)으로 옮기면서 **매핑을 걷어냈다.**
    //
    // DB 컬럼은 아직 남아 있다 — 운영 구 jar 가 그 컬럼을 매핑하고 있어서 지금 DROP 하면 재기동이
    // 불가능해진다(V18 주석의 expand/contract 참조). 컬럼 DROP 은 V19 로 미뤘다.
    //
    // 그동안 "신 코드는 member.ship_* 에 쓰지 않는다" 를 규율이 아니라 **구조로** 보장한다 —
    // 필드가 없으면 쓸 방법이 없다. ddl-auto=validate 는 매핑되지 않은 여분 컬럼을 문제 삼지 않는다.

    @Builder
    private Member(String loginId, String password, String nickname, Role role) {
        this.loginId = loginId;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateEmail(String email) {
        this.email = email;
    }

    // --- 관리자 조작(B-11 후속) ---

    public void changeRole(Role role) {
        this.role = role;
    }

    public void suspend() {
        this.suspended = true;
    }

    public void unsuspend() {
        this.suspended = false;
    }
}

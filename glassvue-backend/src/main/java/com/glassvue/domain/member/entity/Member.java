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

    // --- 기본 배송지 (선택) ---
    // 주문서에 자동으로 채워 넣기 위한 "현재 값"일 뿐이다. 주문에는 이 값을 **복사(스냅샷)** 하므로,
    // 여기를 나중에 바꿔도 과거 주문의 배송지는 변하지 않는다.
    @Column(name = "ship_recipient", length = 50)
    private String shipRecipient;

    @Column(name = "ship_phone", length = 20)
    private String shipPhone;

    @Column(name = "ship_zipcode", length = 10)
    private String shipZipcode;

    @Column(name = "ship_address1", length = 200)
    private String shipAddress1;

    @Column(name = "ship_address2", length = 200)
    private String shipAddress2;

    @Builder
    private Member(String loginId, String password, String nickname, Role role) {
        this.loginId = loginId;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
    }

    /** 기본 배송지 저장(마이페이지). 전부 함께 바뀌는 값이라 한 번에 받는다. */
    public void updateShippingAddress(String recipient, String phone, String zipcode,
                                      String address1, String address2) {
        this.shipRecipient = recipient;
        this.shipPhone = phone;
        this.shipZipcode = zipcode;
        this.shipAddress1 = address1;
        this.shipAddress2 = address2;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}

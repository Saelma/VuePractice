package com.glassvue.domain.member.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 배송지 주소록 항목 (2026-07-24, V18).
 *
 * <p>V11 까지 배송지는 {@code member.ship_*} 5컬럼 = <b>회원당 하나</b>였다. 집/회사를 구분할 수 없어
 * 별칭을 붙인 여러 주소로 늘렸고, 그중 하나가 기본 배송지가 된다.
 *
 * <p>주문에는 이 값을 <b>복사(스냅샷)</b> 한다 — 여기를 나중에 고쳐도 과거 주문의 배송지는 변하지 않는다
 * (구매자 닉네임 V5 · 상품 이미지 V9 · 배송비 V14 · 정가 V16 · 쿠폰 V17 과 같은 원칙).
 *
 * <p>{@code memberId} 는 느슨한 UUID 지만 <b>DB 에는 진짜 FK 가 걸려 있다</b>(V18). member_coupon 과
 * 다른 점이다 — 그건 coupon 도메인이 member 를 가리키는 <b>도메인 간</b> 참조라 경계 때문에 느슨하게 뒀고,
 * 이건 <b>member 도메인 안</b>이라 MSA 로 쪼개도 member 와 함께 움직인다. FK 의 {@code ON DELETE CASCADE}
 * 가 회원 탈퇴 시 주소를 함께 지운다.
 */
@Entity
@Getter
@Table(name = "member_address")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAddress extends BaseTimeEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "member_id", columnDefinition = "RAW(16)", nullable = false, updatable = false)
    private UUID memberId;

    /** 별칭(집·회사) — 이 기능의 존재 이유다. 여러 주소를 사람이 구분하는 유일한 단서. */
    @Column(nullable = false, length = 30)
    private String alias;

    @Column(nullable = false, length = 50)
    private String recipient;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 10)
    private String zipcode;

    @Column(nullable = false, length = 200)
    private String address1;

    @Column(length = 200)
    private String address2;

    /**
     * 기본 배송지 여부. <b>회원당 최대 하나</b>이고, 그 보장은 앱이 아니라 DB 가 한다 —
     * V18 의 함수 기반 유니크 인덱스({@code uq_member_address_default})가 두 번째를 거부한다.
     */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    private MemberAddress(UUID memberId, String alias, String recipient, String phone,
                          String zipcode, String address1, String address2) {
        this.memberId = memberId;
        this.alias = alias;
        this.recipient = recipient;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
        this.isDefault = false;
    }

    public static MemberAddress of(UUID memberId, String alias, String recipient, String phone,
                                   String zipcode, String address1, String address2) {
        return new MemberAddress(memberId, alias, recipient, phone, zipcode, address1, address2);
    }

    /** 주소 수정. 전부 함께 바뀌는 값이라 한 번에 받는다(기본 배송지 여부는 별도 경로). */
    public void update(String alias, String recipient, String phone,
                       String zipcode, String address1, String address2) {
        this.alias = alias;
        this.recipient = recipient;
        this.phone = phone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public void unsetDefault() {
        this.isDefault = false;
    }

    public boolean isOwnedBy(UUID memberId) {
        return this.memberId.equals(memberId);
    }
}

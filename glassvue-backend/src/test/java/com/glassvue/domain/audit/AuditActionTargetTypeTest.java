package com.glassvue.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@code AuditAction} 이 자기 대상 종류를 답한다 (2026-08-20, V53).
 *
 * <p>🔴 <b>이 파일이 지키는 것은 «값이 맞나» 가 아니라 «값이 있나» 다.</b> 대상 종류를 이벤트로
 * 받지 않고 action 에서 파생하기로 한 이상, <b>새 action 을 더하면서 대상을 안 정하는 일</b>이
 * 없어야 한다 — 안 정하면 {@code targetType()} 이 {@code null} 을 주고, 그 행은 NOT NULL 에 걸려
 * <b>조작 자체를 롤백시킨다</b>(감사와 조작이 같은 트랜잭션이라서). 즉 <b>빠뜨림의 대가가 «감사가
 * 안 남는다» 가 아니라 «기능이 안 된다»</b> 이고, 그건 배포 후가 아니라 여기서 걸려야 한다.
 *
 * <p>⚠ DB CHECK 제약과의 대조는 <b>여기서 못 한다</b> — 이 파일은 DB 를 안 띄운다.
 * 그건 마이그레이션(V53)이 지키고, 어긋나면 기동이 통째로 막힌다(Oracle enum CHECK 트랩).
 */
class AuditActionTargetTypeTest {

    @DisplayName("모든 조작은 대상 종류를 갖는다 — 새 값을 더하며 빠뜨리면 여기서 걸린다")
    @ParameterizedTest
    @EnumSource(AuditAction.class)
    void everyActionHasTargetType(AuditAction action) {
        assertThat(action.targetType()).isNotNull();
    }

    @DisplayName("회원 아닌 대상 — 상품 조작과 할인 조작은 둘 다 PRODUCT 다")
    @Test
    void productActionsShareTargetType() {
        // 🔴 이 단언이 V53 의 설계 결정 그 자체다. 할인 조작의 대상을 «할인» 으로 잡았다면
        //    상품 이력과 안 묶여서, «이 상품에 무슨 일이 있었나» 를 한 번에 못 본다.
        assertThat(AuditAction.PRODUCT_CREATE.targetType()).isEqualTo(AuditTargetType.PRODUCT);
        assertThat(AuditAction.PRODUCT_UPDATE.targetType()).isEqualTo(AuditTargetType.PRODUCT);
        assertThat(AuditAction.PRODUCT_DELETE.targetType()).isEqualTo(AuditTargetType.PRODUCT);
        assertThat(AuditAction.PRODUCT_RESTORE.targetType()).isEqualTo(AuditTargetType.PRODUCT);
        assertThat(AuditAction.DISCOUNT_CREATE.targetType()).isEqualTo(AuditTargetType.PRODUCT);
        assertThat(AuditAction.DISCOUNT_UPDATE.targetType()).isEqualTo(AuditTargetType.PRODUCT);
        assertThat(AuditAction.DISCOUNT_DELETE.targetType()).isEqualTo(AuditTargetType.PRODUCT);
    }

    @DisplayName("쿠폰 셋 중 발급만 대상이 회원이다 — «누구에게 줬나» 가 요점이라서")
    @Test
    void couponIssueTargetsMember() {
        assertThat(AuditAction.COUPON_CREATE.targetType()).isEqualTo(AuditTargetType.COUPON);
        assertThat(AuditAction.COUPON_WELCOME_SET.targetType()).isEqualTo(AuditTargetType.COUPON);
        // 🔴 여기만 갈린다. 갈리는 것이 의도라서 못 박아 둔다 —
        //    셋을 한 덩어리로 보고 COUPON 으로 맞추면 «누구에게» 를 loginId 로 못 찾는다.
        assertThat(AuditAction.COUPON_ISSUE.targetType()).isEqualTo(AuditTargetType.MEMBER);
    }

    @DisplayName("주문·리뷰·문의 조작의 대상은 사람이다 — 주문자·작성자(V43·V44 의 판단)")
    @Test
    void contentActionsTargetMember() {
        assertThat(AuditAction.ORDER_CANCEL.targetType()).isEqualTo(AuditTargetType.MEMBER);
        assertThat(AuditAction.ORDER_SHIP.targetType()).isEqualTo(AuditTargetType.MEMBER);
        assertThat(AuditAction.REVIEW_HIDE.targetType()).isEqualTo(AuditTargetType.MEMBER);
        assertThat(AuditAction.INQUIRY_HIDE.targetType()).isEqualTo(AuditTargetType.MEMBER);
    }

    @DisplayName("⚠ action 이름은 20자를 넘을 수 없다 — DB 열이 VARCHAR2(20) 다")
    @ParameterizedTest
    @EnumSource(AuditAction.class)
    void actionNameFitsColumn(AuditAction action) {
        // ORDER_RETURN_APPROVE 가 정확히 20자라 여유가 없다. 넘치면 저장이 실패하는데,
        // 그 실패는 «조작이 안 된다» 로 나타나서 원인을 찾기 어렵다.
        assertThat(action.name().length()).isLessThanOrEqualTo(20);
    }
}

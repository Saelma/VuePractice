package com.glassvue.domain.point.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 적립금 계정 — 잔액과 등급이 바뀌는 **모든 경로**의 경계 (H-2, 2026-07-31).
 *
 * <p>여기가 잔액의 마지막 방어선이다. 서비스는 사용액·환불액을 계산해 넘길 뿐이고,
 * <b>"잔액이 음수가 되지 않는다"</b> 를 실제로 지키는 건 이 엔티티다. DB 에도 {@code balance >= 0}
 * CHECK 가 있지만 거기까지 가면 이미 트랜잭션이 깨진 뒤라, 여기서 막히는지를 직접 확인한다.
 *
 * <p>{@code addPurchase}/{@code subtractPurchase} 가 누적액과 등급을 <b>한 메서드에서 함께</b>
 * 바꾸는 것도 여기서만 보인다 — 둘이 따로 움직일 수 있으면 어긋난 상태가 생긴다.
 */
class PointAccountTest {

    private final UUID memberId = UUID.randomUUID();

    private PointAccount account() {
        return PointAccount.openFor(memberId);
    }

    /** 잔액을 원하는 값으로 만들어 시작한다 — earn 이 유일한 증가 경로라 그걸 쓴다. */
    private PointAccount accountWith(long balance) {
        PointAccount account = account();
        account.earn(balance);
        return account;
    }

    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    @Test
    @DisplayName("개설 직후 — 잔액 0 · 누적 0 · BRONZE")
    void openFor() {
        PointAccount account = account();
        assertThat(account.getMemberId()).isEqualTo(memberId);
        assertThat(account.getBalance()).isZero();
        assertThat(account.getTotalPurchase()).isZero();
        assertThat(account.getGrade()).isEqualTo(MemberGrade.BRONZE);
    }

    // --- 사용 ---

    @Test
    @DisplayName("사용: 잔액보다 1원 많으면 POINT_NOT_ENOUGH — 잔액은 그대로")
    void use_notEnough() {
        PointAccount account = accountWith(1_000);
        assertErrorCode(() -> account.use(1_001), ErrorCode.POINT_NOT_ENOUGH);
        assertThat(account.getBalance()).isEqualTo(1_000);   // 예외를 던지고 나서 깎지 않았는지
    }

    @Test
    @DisplayName("사용: 잔액과 정확히 같은 금액은 된다 → 0원 (경계 포함)")
    void use_exactBalance() {
        PointAccount account = accountWith(1_000);
        account.use(1_000);
        assertThat(account.getBalance()).isZero();
    }

    @Test
    @DisplayName("사용: 0원·음수는 POINT_INVALID_AMOUNT — 음수 사용은 곧 적립이다")
    void use_nonPositive() {
        PointAccount account = accountWith(1_000);
        assertErrorCode(() -> account.use(0), ErrorCode.POINT_INVALID_AMOUNT);
        // ⚠ 음수를 통과시키면 balance -= (-500) 이라 **쓰면 늘어난다.**
        assertErrorCode(() -> account.use(-500), ErrorCode.POINT_INVALID_AMOUNT);
        assertThat(account.getBalance()).isEqualTo(1_000);
    }

    // --- 적립 ---

    @Test
    @DisplayName("적립: 0원·음수는 POINT_INVALID_AMOUNT")
    void earn_nonPositive() {
        PointAccount account = accountWith(1_000);
        assertErrorCode(() -> account.earn(0), ErrorCode.POINT_INVALID_AMOUNT);
        assertErrorCode(() -> account.earn(-1), ErrorCode.POINT_INVALID_AMOUNT);
        assertThat(account.getBalance()).isEqualTo(1_000);
    }

    // --- 반품 환불(순변동) ---

    @Test
    @DisplayName("환불: 양수 순변동은 잔액에 더해진다")
    void refund_positive() {
        PointAccount account = accountWith(1_000);
        account.refund(8_500);
        assertThat(account.getBalance()).isEqualTo(9_500);
    }

    @Test
    @DisplayName("환불: 순변동이 음수여도 잔액은 줄지 않는다 (0으로 막힌다)")
    void refund_negativeIsClamped() {
        // 설계상 환불액 > 적립회수라 음수가 나올 수 없지만, 나오는 날엔 잔액이 깎여
        // DB CHECK 에 걸리며 반품 처리 전체가 실패한다. 그 자리를 못박아 둔다.
        PointAccount account = accountWith(1_000);
        account.refund(-9_999);
        assertThat(account.getBalance()).isEqualTo(1_000);
    }

    // --- 누적 구매액 · 등급 ---

    @Test
    @DisplayName("누적: 임계 직전까지는 승급 아님 → 1원 더하면 승급 (returns true)")
    void addPurchase_promotesAtBoundary() {
        PointAccount account = account();

        assertThat(account.addPurchase(99_999)).isFalse();           // BRONZE 유지
        assertThat(account.getGrade()).isEqualTo(MemberGrade.BRONZE);

        assertThat(account.addPurchase(1)).isTrue();                 // 100,000 도달
        assertThat(account.getGrade()).isEqualTo(MemberGrade.SILVER);
        assertThat(account.getTotalPurchase()).isEqualTo(100_000);
    }

    @Test
    @DisplayName("누적: 등급이 그대로면 false — 승급 알림이 매 주문마다 나가면 안 된다")
    void addPurchase_noPromotionReturnsFalse() {
        PointAccount account = account();
        account.addPurchase(200_000);                                 // SILVER
        assertThat(account.addPurchase(50_000)).isFalse();            // 여전히 SILVER
        assertThat(account.getGrade()).isEqualTo(MemberGrade.SILVER);
    }

    @Test
    @DisplayName("누적: 한 번에 두 단계를 뛰어넘어도 최종 등급으로 간다")
    void addPurchase_skipsGrades() {
        PointAccount account = account();
        assertThat(account.addPurchase(1_000_000)).isTrue();
        assertThat(account.getGrade()).isEqualTo(MemberGrade.VIP);    // BRONZE → VIP 직행
    }

    @Test
    @DisplayName("누적: 음수 금액은 0으로 취급 — 누적액을 더하기로 줄일 수 없다")
    void addPurchase_negativeIgnored() {
        PointAccount account = account();
        account.addPurchase(200_000);
        assertThat(account.addPurchase(-500_000)).isFalse();
        assertThat(account.getTotalPurchase()).isEqualTo(200_000);    // 줄지 않았다
        assertThat(account.getGrade()).isEqualTo(MemberGrade.SILVER);
    }

    @Test
    @DisplayName("반품: 누적액이 임계 아래로 내려가면 **강등**된다")
    void subtractPurchase_demotes() {
        PointAccount account = account();
        account.addPurchase(120_000);                                 // SILVER
        account.subtractPurchase(30_000);                             // 90,000 → 임계 미달
        assertThat(account.getTotalPurchase()).isEqualTo(90_000);
        assertThat(account.getGrade()).isEqualTo(MemberGrade.BRONZE); // "샀다가 반품하면 등급만 남는" 것 방지
    }

    @Test
    @DisplayName("반품: 임계값에 정확히 걸치면 등급 유지 (>= 이므로)")
    void subtractPurchase_exactlyAtThresholdKeepsGrade() {
        PointAccount account = account();
        account.addPurchase(150_000);
        account.subtractPurchase(50_000);                             // 정확히 100,000
        assertThat(account.getGrade()).isEqualTo(MemberGrade.SILVER);
    }

    @Test
    @DisplayName("반품: 누적액은 0 아래로 내려가지 않는다")
    void subtractPurchase_flooredAtZero() {
        PointAccount account = account();
        account.addPurchase(10_000);
        account.subtractPurchase(999_999);
        assertThat(account.getTotalPurchase()).isZero();
        assertThat(account.getGrade()).isEqualTo(MemberGrade.BRONZE);
    }

    @Test
    @DisplayName("반품: 음수 금액은 0으로 취급 — 빼기로 누적액을 늘릴 수 없다")
    void subtractPurchase_negativeIgnored() {
        PointAccount account = account();
        account.addPurchase(50_000);
        account.subtractPurchase(-1_000_000);
        assertThat(account.getTotalPurchase()).isEqualTo(50_000);     // 늘지 않았다
        assertThat(account.getGrade()).isEqualTo(MemberGrade.BRONZE);
    }
}

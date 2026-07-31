package com.glassvue.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.glassvue.domain.point.entity.MemberGrade;
import com.glassvue.domain.point.entity.PointAccount;
import com.glassvue.domain.point.entity.PointHistory;
import com.glassvue.domain.point.entity.PointType;
import com.glassvue.domain.point.repository.PointAccountRepository;
import com.glassvue.domain.point.repository.PointHistoryRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 적립금 서비스 단위 테스트 (H-2, 2026-07-31).
 *
 * <p>⚠ <b>여기까지 오기 전엔 이 로직을 실행하는 테스트가 사실상 하나뿐이었다.</b>
 * {@code PointFlowIntegrationTest}(통합) 한 갈래가 전부였고, {@code OrderServiceTest} 는
 * {@code PointService} 를 통째로 {@code @Mock} 으로 바꿔치기해 <b>한 줄도 실행하지 않는다.</b>
 *
 * <p>통합테스트가 못 보는 것을 노린다:
 * <ol>
 *   <li><b>이력에 실제로 무엇이 담기는가</b> — 종류·부호·{@code balanceAfter}. 통합은 잔액 합계만
 *       대조해서, 사용을 양수로 적어도 합계는 맞을 수 있다.</li>
 *   <li><b>적립이 "이번 주문으로 승급한 뒤" 등급으로 계산되는가</b> — 승급 경계에서만 갈린다.</li>
 *   <li><b>실패 경로에서 이력이 남지 않는가</b> — 예외가 났는데 원장에 줄이 생기면 안 된다.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock PointAccountRepository accountRepository;
    @Mock PointHistoryRepository historyRepository;
    @InjectMocks PointService service;

    private final UUID memberId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    /** 잔액·누적액을 원하는 상태로 만든 계정을 리포지토리가 돌려주게 한다. */
    private PointAccount given(long balance, long totalPurchase) {
        PointAccount account = PointAccount.openFor(memberId);
        if (balance > 0) {
            account.earn(balance);
        }
        if (totalPurchase > 0) {
            account.addPurchase(totalPurchase);
        }
        when(accountRepository.findByMemberId(memberId)).thenReturn(Optional.of(account));
        return account;
    }

    /** 계정이 아직 없는 회원 — accountOrOpen 이 그 자리에서 열어야 한다. */
    private void givenNoAccount() {
        when(accountRepository.findByMemberId(memberId)).thenReturn(Optional.empty());
        when(accountRepository.save(any(PointAccount.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private PointHistory savedHistory() {
        ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
        verify(historyRepository).save(captor.capture());
        return captor.getValue();
    }

    private static void assertErrorCode(Runnable r, ErrorCode expected) {
        assertThatThrownBy(r::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }

    // ------------------------------------------------------------------ use

    @Test
    @DisplayName("사용: 0원 이하는 계정을 읽기도 전에 거절 — POINT_INVALID_AMOUNT")
    void use_nonPositive() {
        assertErrorCode(() -> service.use(memberId, 0, 10_000, orderId), ErrorCode.POINT_INVALID_AMOUNT);
        assertErrorCode(() -> service.use(memberId, -1, 10_000, orderId), ErrorCode.POINT_INVALID_AMOUNT);
        verifyNoInteractions(accountRepository, historyRepository);
    }

    @Test
    @DisplayName("사용: 주문 한도(maxUsable)를 1원이라도 넘으면 POINT_EXCEEDS_ORDER")
    void use_exceedsOrder() {
        // ⚠ 잔액이 넉넉해도 거절돼야 한다 — 한도는 "낼 돈"의 상한이지 잔액의 상한이 아니다.
        //    통과시키면 결제금액이 음수가 되거나 배송비를 적립금으로 내는 상태가 된다.
        assertErrorCode(() -> service.use(memberId, 10_001, 10_000, orderId), ErrorCode.POINT_EXCEEDS_ORDER);
        verifyNoInteractions(accountRepository, historyRepository);
    }

    @Test
    @DisplayName("사용: 한도와 정확히 같은 금액은 통과한다 (경계 포함)")
    void use_exactlyMaxUsable() {
        given(50_000, 0);
        service.use(memberId, 10_000, 10_000, orderId);
        assertThat(savedHistory().getAmount()).isEqualTo(-10_000);
    }

    @Test
    @DisplayName("사용: 잔액이 모자라면 POINT_NOT_ENOUGH — **이력을 남기지 않는다**")
    void use_notEnough_leavesNoHistory() {
        PointAccount account = given(5_000, 0);
        assertErrorCode(() -> service.use(memberId, 6_000, 10_000, orderId), ErrorCode.POINT_NOT_ENOUGH);
        assertThat(account.getBalance()).isEqualTo(5_000);
        verify(historyRepository, never()).save(any());   // 실패한 사용이 원장에 남으면 잔액과 합이 갈라진다
    }

    @Test
    @DisplayName("사용: 성공하면 잔액 차감 + 이력 USE는 **음수**, balanceAfter는 차감 후 값")
    void use_success() {
        PointAccount account = given(5_000, 0);

        service.use(memberId, 3_000, 10_000, orderId);

        assertThat(account.getBalance()).isEqualTo(2_000);
        PointHistory history = savedHistory();
        assertThat(history.getType()).isEqualTo(PointType.USE);
        assertThat(history.getAmount()).isEqualTo(-3_000);          // 부호를 엔티티가 붙인다
        assertThat(history.getBalanceAfter()).isEqualTo(2_000);     // 차감 **전** 값이면 이력만 보고 추적이 안 된다
        assertThat(history.getOrderId()).isEqualTo(orderId);
        assertThat(history.getMemberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("사용: 계정이 없으면 그 자리에서 열고, 잔액 0이라 POINT_NOT_ENOUGH")
    void use_opensAccountThenFails() {
        givenNoAccount();
        assertErrorCode(() -> service.use(memberId, 1_000, 10_000, orderId), ErrorCode.POINT_NOT_ENOUGH);
        verify(accountRepository).save(any(PointAccount.class));   // 404 로 주문을 막지 않는다(2026-07-24 사고)
        verify(historyRepository, never()).save(any());
    }

    // -------------------------------------------------------- earnOnDelivery

    @Test
    @DisplayName("적립: BRONZE 1% — 99원짜리는 적립 0, **이력도 남기지 않는다**")
    void earn_belowOneWon() {
        PointAccount account = given(0, 0);

        long earned = service.earnOnDelivery(memberId, 99, orderId);

        assertThat(earned).isZero();
        assertThat(account.getBalance()).isZero();
        // 0원 이력을 남기면 목록이 의미 없는 줄로 채워지고, earn(0) 은 엔티티가 예외로 막는다.
        verify(historyRepository, never()).save(any());
        // ⚠ 적립이 0이어도 **누적 구매액은 올라간다** — 등급은 적립과 별개로 쌓인다.
        assertThat(account.getTotalPurchase()).isEqualTo(99);
    }

    @Test
    @DisplayName("적립: 이번 주문으로 승급하면 **새 등급 요율**로 적립한다 (경계의 핵심)")
    void earn_usesGradeAfterPromotion() {
        // 누적 99,000 (BRONZE) 인 회원이 10,000 원을 구매확정 → 누적 109,000 → SILVER 승급.
        // 옛 등급(BRONZE 1%)이면 100P, 새 등급(SILVER 2%)이면 200P. 여기가 갈리는 자리다.
        PointAccount account = given(0, 99_000);

        long earned = service.earnOnDelivery(memberId, 10_000, orderId);

        assertThat(account.getGrade()).isEqualTo(MemberGrade.SILVER);
        assertThat(earned).isEqualTo(200);
        assertThat(account.getBalance()).isEqualTo(200);
        assertThat(savedHistory().getType()).isEqualTo(PointType.EARN);
    }

    @Test
    @DisplayName("적립: 이력에 등급·요율이 남고 balanceAfter는 적립 후 값")
    void earn_historyContents() {
        PointAccount account = given(1_000, 600_000);   // GOLD 3%

        long earned = service.earnOnDelivery(memberId, 10_000, orderId);

        assertThat(earned).isEqualTo(300);
        PointHistory history = savedHistory();
        assertThat(history.getAmount()).isEqualTo(300);              // 적립은 양수
        assertThat(history.getBalanceAfter()).isEqualTo(1_300);
        assertThat(history.getReason()).contains("GOLD").contains("3%");
        assertThat(account.getTotalPurchase()).isEqualTo(610_000);
    }

    @Test
    @DisplayName("적립: 결제금액 0원(전액 적립금·쿠폰 결제)이면 적립도 0 — 포인트가 포인트를 낳지 않는다")
    void earn_zeroPayable() {
        PointAccount account = given(0, 0);

        assertThat(service.earnOnDelivery(memberId, 0, orderId)).isZero();

        assertThat(account.getBalance()).isZero();
        assertThat(account.getTotalPurchase()).isZero();
        verify(historyRepository, never()).save(any());
    }

    @Test
    @DisplayName("적립: 계정이 없으면 열고 진행한다 — 적립은 주문의 부가 결과지 전제가 아니다")
    void earn_opensAccount() {
        givenNoAccount();

        long earned = service.earnOnDelivery(memberId, 10_000, orderId);

        assertThat(earned).isEqualTo(100);              // 새 계정은 BRONZE 1%
        verify(accountRepository).save(any(PointAccount.class));
        verify(historyRepository).save(any(PointHistory.class));
    }

    // --------------------------------------------------- refundReturnedOrder

    @Test
    @DisplayName("반품: 순변동 = 환불액 − 적립회수 — 한 줄로 남긴다")
    void refund_net() {
        PointAccount account = given(500, 50_000);

        long net = service.refundReturnedOrder(memberId, 30_000, 300, 30_000, orderId);

        assertThat(net).isEqualTo(29_700);
        assertThat(account.getBalance()).isEqualTo(30_200);          // 500 + 29,700
        PointHistory history = savedHistory();
        assertThat(history.getType()).isEqualTo(PointType.REFUND);
        assertThat(history.getAmount()).isEqualTo(29_700);
        assertThat(history.getBalanceAfter()).isEqualTo(30_200);
        assertThat(history.getReason()).contains("30000").contains("300");
    }

    @Test
    @DisplayName("반품: 등급 기준에서도 그 주문 몫을 빼 **강등**될 수 있다")
    void refund_demotes() {
        PointAccount account = given(0, 120_000);       // SILVER

        service.refundReturnedOrder(memberId, 30_000, 600, 30_000, orderId);

        assertThat(account.getTotalPurchase()).isEqualTo(90_000);
        assertThat(account.getGrade()).isEqualTo(MemberGrade.BRONZE);
    }

    @Test
    @DisplayName("반품: 회수액이 환불액보다 큰 이상 상황에서도 잔액은 줄지 않고 이력 금액은 0")
    void refund_negativeNetIsClamped() {
        // 설계상 환불액 > 적립회수라 일어나지 않지만, 일어나면 잔액이 깎여 DB CHECK 에 걸린다.
        // Math.max 가 **잔액 쪽과 이력 쪽 두 군데** 걸려 있는 게 여기서만 확인된다.
        PointAccount account = given(1_000, 50_000);

        long net = service.refundReturnedOrder(memberId, 100, 5_000, 0, orderId);

        assertThat(net).isEqualTo(-4_900);                           // 반환값은 계산 그대로(로그·참고용)
        assertThat(account.getBalance()).isEqualTo(1_000);           // 잔액은 그대로
        assertThat(savedHistory().getAmount()).isZero();             // 원장엔 음수가 아니라 0
    }

    @Test
    @DisplayName("반품: 적립이 없던 주문(회수 0)이면 환불액이 그대로 순변동")
    void refund_noEarnToReverse() {
        PointAccount account = given(0, 10_000);

        long net = service.refundReturnedOrder(memberId, 10_000, 0, 10_000, orderId);

        assertThat(net).isEqualTo(10_000);
        assertThat(account.getBalance()).isEqualTo(10_000);
        assertThat(account.getTotalPurchase()).isZero();
    }

    // ------------------------------------------------------ deleteAllForMember

    @Test
    @DisplayName("탈퇴 정리(F-1): 이력 → 계정 순서로 지운다")
    void deleteAllForMember_order() {
        // 반대로 지우면 계정 없는 이력이 순간 존재한다. 같은 트랜잭션이라 밖에선 안 보이지만
        // 순서를 고정해 의도를 남긴 자리라, 순서 자체를 테스트가 지킨다.
        when(historyRepository.deleteByMemberId(memberId)).thenReturn(3L);
        when(accountRepository.deleteByMemberId(memberId)).thenReturn(1L);

        service.deleteAllForMember(memberId);

        InOrder order = inOrder(historyRepository, accountRepository);
        order.verify(historyRepository).deleteByMemberId(memberId);
        order.verify(accountRepository).deleteByMemberId(memberId);
    }

    // ---------------------------------------------------------- 읽기 경로

    @Test
    @DisplayName("조회: 계정이 없어도 빈 계정을 보여주되 **저장하지 않는다** (읽기가 쓰기를 하지 않는다)")
    void myAccount_doesNotPersist() {
        when(accountRepository.findByMemberId(memberId)).thenReturn(Optional.empty());

        assertThat(service.myAccount(memberId).balance()).isZero();
        assertThat(service.balanceOf(memberId)).isZero();

        verify(accountRepository, never()).save(any());   // 동시 조회에서 유니크 충돌이 난다
    }
}

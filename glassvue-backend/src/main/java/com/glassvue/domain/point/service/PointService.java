package com.glassvue.domain.point.service;

import com.glassvue.domain.point.dto.PointAccountResponse;
import com.glassvue.domain.point.dto.PointHistoryResponse;
import com.glassvue.domain.point.entity.MemberGrade;
import com.glassvue.domain.point.entity.PointAccount;
import com.glassvue.domain.point.entity.PointHistory;
import com.glassvue.domain.point.repository.PointAccountRepository;
import com.glassvue.domain.point.repository.PointHistoryRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.policy.ShippingPolicy;
import com.glassvue.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적립금 · 회원 등급 (2026-07-24, 백로그 C-10).
 *
 * <p>다른 도메인은 <b>이 서비스로만</b> 적립금을 다룬다(엔티티·리포지토리를 직접 만지지 않는다) —
 * {@code CouponService} 와 같은 자리다.
 *
 * <h3>잔액과 이력은 항상 함께 움직인다</h3>
 * <p>{@code point_history} 가 <b>원장</b>이고 {@code point_account.balance} 는 그 합의 캐시다.
 * 그래서 잔액을 바꾸는 모든 경로가 이력을 함께 남긴다 — 한 메서드 안에서 둘 다 하도록 묶어
 * "잔액만 고치고 이력을 빠뜨리는" 코드를 쓸 수 없게 했다.
 * 통합테스트가 매번 {@code SUM(amount) == balance} 로 대조한다.
 *
 * <h3>적립 시점이 배송완료인 이유</h3>
 * <p>주문 취소는 {@code ORDERED}·{@code PAID} 에서만 된다. {@code DELIVERED} 에서 적립하면
 * <b>되돌릴 일이 원천적으로 없다</b> — 회수 로직도, 잔액이 음수가 되는 경우도 다룰 필요가 없다.
 * (사용자 결정, 2026-07-24)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PointService {

    private final PointAccountRepository accountRepository;
    private final PointHistoryRepository historyRepository;
    /**
     * 무료배송 <b>기본</b> 기준을 읽는 데만 쓴다 — 등급별 인하는 {@link MemberGrade#discountedThreshold}
     * 가 한다 (2026-08-28, BACKLOG G-6). ⚠ global 정책을 도메인이 읽는 것은 허용된다
     * (금지된 것은 <b>도메인끼리</b> 직접 참조하는 것이다).
     */
    private final ShippingPolicy shippingPolicy;

    /** 가입 시 계정을 연다 — 정상 경로. 없을 때의 대비는 {@link #accountOrOpen} 참조. */
    public void openAccount(UUID memberId) {
        if (accountRepository.findByMemberId(memberId).isPresent()) {
            return;
        }
        accountRepository.save(PointAccount.openFor(memberId));
        log.info("Point account opened: member={}", memberId);
    }

    /**
     * 내 적립금·등급 — 계정이 없으면 <b>빈 계정을 만들어 보여주되 저장하지는 않는다.</b>
     *
     * <p>읽기 경로에서 쓰기를 하면 안 되고(동시 조회에서 유니크 충돌), 그렇다고 404 를 주면
     * 화면이 "적립금 없음"과 "장애"를 구분할 수 없다. 표시용 기본값이 정답이다.
     */
    @Transactional(readOnly = true)
    public PointAccountResponse myAccount(UUID memberId) {
        return PointAccountResponse.from(accountRepository.findByMemberId(memberId)
                .orElseGet(() -> PointAccount.openFor(memberId)),    // save 하지 않는다
                shippingPolicy.getFreeThreshold());
    }

    /** 사용 가능한 잔액만 — 주문서가 한도를 계산할 때 쓴다. */
    @Transactional(readOnly = true)
    public long balanceOf(UUID memberId) {
        return accountRepository.findByMemberId(memberId).map(PointAccount::getBalance).orElse(0L);
    }

    /**
     * 등급만 — 장바구니·주문이 <b>무료배송 기준</b>을 정할 때 쓴다 (2026-08-28, BACKLOG G-6).
     *
     * <p>{@code balanceOf} 와 같은 성격의 <b>좁은 공개 API</b> 다. {@code myAccount} 를 부르면
     * «다음 등급까지 얼마» 같은 표시용 계산까지 딸려 오는데, 부르는 쪽은 등급 하나만 필요하다.
     *
     * <p>🔴 <b>계정이 없으면 {@code BRONZE}</b> — {@code myAccount} 가 «빈 계정을 보여주되 저장하지
     * 않는다» 로 정한 것과 같은 판단이다. 여기서 계정을 만들면 <b>장바구니를 여는 것만으로</b>
     * 적립금 계정이 생긴다.
     */
    @Transactional(readOnly = true)
    public MemberGrade gradeOf(UUID memberId) {
        return accountRepository.findByMemberId(memberId)
                .map(PointAccount::getGrade)
                .orElse(MemberGrade.BRONZE);
    }

    @Transactional(readOnly = true)
    public PageResponse<PointHistoryResponse> myHistory(UUID memberId, Pageable pageable) {
        return PageResponse.from(historyRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
                .map(PointHistoryResponse::from));
    }

    // --- 관리자용(B-11 회원 상세) — myAccount/myHistory 와 같은 조회지만 "남의 계정을 본다"는 의미를
    //     이름으로 분리한다. member 도메인이 아니라 point 가 자기 데이터를 소유해 admin 으로 노출한다. ---

    /** 관리자 — 특정 회원의 적립금·등급. */
    @Transactional(readOnly = true)
    public PointAccountResponse accountOf(UUID memberId) {
        return myAccount(memberId);
    }

    /** 관리자 — 특정 회원의 적립금 이력(페이징). */
    @Transactional(readOnly = true)
    public PageResponse<PointHistoryResponse> historyOf(UUID memberId, Pageable pageable) {
        return myHistory(memberId, pageable);
    }

    /**
     * 주문에서 적립금 사용 — <b>검증과 차감을 한 번에</b> 한다.
     *
     * <p>나누면 "검증했으니 이제 써도 되겠지" 사이에 같은 잔액이 <b>두 번 쓰일 틈</b>이 생긴다
     * ({@code CouponService.redeem} 과 같은 판단). 주문 트랜잭션 안에서 호출되므로
     * 주문이 롤백되면 차감과 이력도 함께 롤백된다.
     *
     * @param maxUsable 이 주문에서 쓸 수 있는 상한(상품합계 − 쿠폰할인). 넘으면 거절한다 —
     *                  결제금액이 음수가 되거나 배송비를 적립금으로 내는 이상한 상태를 막는다
     */
    public void use(UUID memberId, long amount, long maxUsable, UUID orderId) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.POINT_INVALID_AMOUNT);
        }
        if (amount > maxUsable) {
            throw new BusinessException(ErrorCode.POINT_EXCEEDS_ORDER);
        }
        PointAccount account = accountOrOpen(memberId);
        account.use(amount);   // 잔액 부족이면 POINT-400N
        historyRepository.save(PointHistory.used(memberId, amount, account.getBalance(), orderId, "주문 사용"));
        log.info("Point used: member={} amount={} order={} balance={}",
                memberId, amount, orderId, account.getBalance());
    }

    /**
     * 배송완료 적립 + 등급 갱신.
     *
     * <p>적립 기준액은 <b>실제로 낸 돈</b>이다 — 쿠폰 할인과 사용한 적립금을 뺀 금액.
     * 적립금으로 낸 부분에까지 적립을 주면 <b>포인트가 포인트를 낳는다.</b>
     * 배송비도 제외한다(상품 대금이 아니라 운임이다).
     *
     * <p>등급 기준(누적 구매액)도 <b>같은 금액</b>을 쓴다 — 적립과 등급이 다른 기준을 보면
     * "얼마 샀는데 왜 등급이 안 오르지"를 설명할 수 없다.
     *
     * @return 실제로 적립된 금액. 0이면 적립할 게 없었다는 뜻(이력도 남기지 않는다)
     */
    public long earnOnDelivery(UUID memberId, long payableAmount, UUID orderId) {
        PointAccount account = accountOrOpen(memberId);
        boolean promoted = account.addPurchase(payableAmount);

        // ⚠ 적립률은 **누적 반영 후 등급**으로 계산한다. 이번 주문으로 승급했으면 그 혜택을 바로 준다 —
        //    "이번 주문 때문에 올랐는데 적립은 옛 등급"이 고객에겐 설명하기 어렵다.
        long earned = account.getGrade().earn(payableAmount);
        if (earned <= 0) {
            log.info("Point earn skipped (0): member={} payable={} order={}", memberId, payableAmount, orderId);
            return 0L;
        }
        account.earn(earned);
        historyRepository.save(PointHistory.earned(memberId, earned, account.getBalance(), orderId,
                account.getGrade().name() + " 등급 " + account.getGrade().earnPercent() + "% 적립"));
        log.info("Point earned: member={} amount={} grade={} promoted={} order={} balance={}",
                memberId, earned, account.getGrade(), promoted, orderId, account.getBalance());
        return earned;
    }

    /**
     * 반품 환불 (2026-07-24, C-9) — 결제금액을 적립금으로 돌려주고 그 주문의 적립을 회수한다.
     *
     * <p>한 번의 <b>순변동</b>으로 처리한다:
     * <pre>순변동 = 환불액(상품합계−쿠폰) − 적립회수(배송완료 때 준 earned_point)</pre>
     * 환불액이 적립보다 항상 크므로 순변동 ≥ 0 — 잔액이 음수가 될 일이 없다(그래서 파밍이 불가능하다:
     * 사서 적립받고 반품해도 적립분은 회수돼 순이득이 없다).
     *
     * <p>등급 기준(누적 구매확정액)에서도 이 주문의 몫을 <b>빼고 재산정</b>한다 — 강등될 수 있다.
     * "샀다가 반품하면 등급만 남는" 것도 막는다. 적립·환불·등급이 한 트랜잭션에서 함께 움직인다.
     *
     * @param refundAmount   환불액(상품합계 − 쿠폰) = order.refundableAmount()
     * @param earnedToReverse 배송완료 때 준 적립 = order.earnedPoint()
     * @param purchaseToRemove 등급에 반영됐던 이 주문의 구매확정액 = order.rewardableAmount()
     * @return 실제 적립된 순변동(참고·로그용)
     */
    public long refundReturnedOrder(UUID memberId, long refundAmount, long earnedToReverse,
                                    long purchaseToRemove, UUID orderId) {
        PointAccount account = accountOrOpen(memberId);
        long net = refundAmount - Math.max(0L, earnedToReverse);
        account.refund(net);
        account.subtractPurchase(purchaseToRemove);
        historyRepository.save(PointHistory.refunded(memberId, Math.max(0L, net), account.getBalance(), orderId,
                "반품 환불 " + refundAmount + " (적립 " + earnedToReverse + " 회수)"));
        log.info("Point refunded (return): member={} refund={} earnedReversed={} net={} order={} balance={} grade={}",
                memberId, refundAmount, earnedToReverse, net, orderId, account.getBalance(), account.getGrade());
        return net;
    }

    /**
     * 주문 취소 환불 (2026-08-07) — <b>주문에 쓴 적립금만</b> 돌려준다.
     *
     * <p>⚠ <b>반품 환불과 모양이 다르다.</b> 반품은 «결제금액을 적립금으로» 돌려주는 것이라 순변동을
     * 계산해야 하지만, 취소는 <b>아직 아무것도 확정되지 않은 상태</b>라 되돌릴 것이 하나뿐이다:
     * <ul>
     *   <li><b>적립 회수 없음</b> — 적립은 배송완료에만 붙는데({@link #earnOnDelivery}) 취소는
     *       {@code ORDERED}·{@code PAID} 에서만 된다. 줄 일이 없었으니 뺄 것도 없다.</li>
     *   <li><b>등급 되돌림 없음</b> — 누적 구매확정액도 배송완료에만 오른다(같은 메서드의
     *       {@code addPurchase}). 여기서 {@code subtractPurchase} 를 부르면 <b>안 더한 것을 뺀다.</b></li>
     * </ul>
     * 그래서 반품 쪽 메서드를 재사용하지 않았다 — 인자 셋 중 둘이 항상 0 인 호출은 읽는 사람에게
     * "취소도 적립을 회수하는구나" 라는 <b>틀린 인상</b>을 준다.
     *
     * <p>⚠ 유형은 {@code REFUND} 를 그대로 쓴다. {@code CANCEL} 값을 새로 만들면 CHECK 제약
     * 마이그레이션이 따라오는데, <b>취소인지 반품인지는 이미 {@code order_id} 로 되짚어진다</b>
     * (주문 상태가 답을 갖고 있다). 같은 정보를 두 번 적지 않는다 — 재고 이력에 취소 사유를
     * 복사하지 않은 것과 같은 판단이다.
     *
     * @param usedPoint 이 주문에 쓴 적립금 = {@code order.getUsedPoint()}. 0 이면 아무것도 안 한다.
     */
    public void refundCancelledOrder(UUID memberId, long usedPoint, UUID orderId) {
        // 적립금을 안 쓴 주문이 대부분이다. 0원 이력을 남기면 원장이 «아무 일도 없었다» 는 줄로 채워진다.
        if (usedPoint <= 0) {
            return;
        }
        PointAccount account = accountOrOpen(memberId);
        account.refund(usedPoint);
        historyRepository.save(PointHistory.refunded(memberId, usedPoint, account.getBalance(), orderId,
                "주문 취소 환불 " + usedPoint));
        log.info("Point refunded (cancel): member={} amount={} order={} balance={}",
                memberId, usedPoint, orderId, account.getBalance());
    }

    /**
     * 계정을 꺼내고, <b>없으면 그 자리에서 연다</b>. <b>쓰기 트랜잭션에서만</b> 쓴다.
     *
     * <p>계정은 원래 가입 시({@link #openAccount}) 만들고 기존 회원은 V21 이 백필했다.
     * 그런데 그 둘을 안 거친 회원이 있을 수 있고(리포지토리로 직접 만든 계정, 마이그레이션 이후의 데이터 보정 등),
     * 그때 <b>404 를 던지면 배송완료 자체가 실패한다.</b>
     *
     * <p>⚠ <b>실제로 통합테스트가 이걸 잡았다</b>(2026-07-24) — 계정 없는 회원의 주문을 배송완료 처리하니
     * {@code POINT-404} 로 주문 흐름이 통째로 막혔다. <b>적립은 주문의 부가 결과지 전제 조건이 아니다.</b>
     *
     * <p>여기서 만드는 건 "조회가 쓰기를 한다"에 어긋나지 않는다 — 호출부({@code use}·{@code earnOnDelivery})가
     * 이미 쓰기 트랜잭션이기 때문이다. 읽기 경로({@link #myAccount}·{@link #balanceOf})는 저장하지 않는다.
     */
    private PointAccount accountOrOpen(UUID memberId) {
        return accountRepository.findByMemberId(memberId)
                .orElseGet(() -> accountRepository.save(PointAccount.openFor(memberId)));
    }

    /**
     * 회원 삭제 정리(F-1) — 적립금 계정과 이력을 지운다. 핸들러(진짜 주체)이고, 리스너는 위임만 한다.
     *
     * <p>⚠ 이력은 <b>원장</b>이라 평소엔 지우지 않는다(잔액은 그 캐시다). 그러나 주인이 사라지면
     * 남길 근거도 사라진다 — 회원 없는 이력은 아무 질문에도 답하지 못한다.
     * ⚠ <b>순서가 있다</b>: 이력 → 계정. 반대로 하면 계정 없는 이력이 순간 존재한다(같은 트랜잭션이라
     * 밖에서는 안 보이지만, 읽는 사람에게 의도를 남기려고 순서를 고정한다).
     */
    public void deleteAllForMember(UUID memberId) {
        long histories = historyRepository.deleteByMemberId(memberId);
        long accounts = accountRepository.deleteByMemberId(memberId);
        log.info("Point data deleted for member {}: histories={} accounts={}", memberId, histories, accounts);
    }
}

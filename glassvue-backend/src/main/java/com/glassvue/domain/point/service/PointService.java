package com.glassvue.domain.point.service;

import com.glassvue.domain.point.dto.PointAccountResponse;
import com.glassvue.domain.point.dto.PointHistoryResponse;
import com.glassvue.domain.point.entity.PointAccount;
import com.glassvue.domain.point.entity.PointHistory;
import com.glassvue.domain.point.repository.PointAccountRepository;
import com.glassvue.domain.point.repository.PointHistoryRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
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
                .orElseGet(() -> PointAccount.openFor(memberId)));   // save 하지 않는다
    }

    /** 사용 가능한 잔액만 — 주문서가 한도를 계산할 때 쓴다. */
    @Transactional(readOnly = true)
    public long balanceOf(UUID memberId) {
        return accountRepository.findByMemberId(memberId).map(PointAccount::getBalance).orElse(0L);
    }

    @Transactional(readOnly = true)
    public PageResponse<PointHistoryResponse> myHistory(UUID memberId, Pageable pageable) {
        return PageResponse.from(historyRepository
                .findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
                .map(PointHistoryResponse::from));
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
}

package com.glassvue.domain.coupon.service;

import com.glassvue.domain.coupon.dto.CouponCreateRequest;
import com.glassvue.domain.coupon.dto.CouponResponse;
import com.glassvue.domain.coupon.dto.MemberCouponResponse;
import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.MemberCoupon;
import com.glassvue.domain.coupon.repository.CouponRepository;
import com.glassvue.domain.coupon.repository.MemberCouponRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 도메인 서비스.
 *
 * <p>{@link #redeem} 이 <b>order 도메인이 쓰는 공개 API</b>다 — order 는 쿠폰 엔티티나 리포지토리를
 * 직접 만지지 않는다(도메인 경계). 검증·사용처리·할인액 계산이 이 한 번의 호출 안에서 끝난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;

    /** 쿠폰 생성(관리자). */
    @Transactional
    public UUID create(CouponCreateRequest req) {
        Coupon coupon = couponRepository.save(Coupon.builder()
                .name(req.name())
                .discountType(req.discountType())
                .discountValue(req.discountValue())
                .minOrderAmount(req.minOrderAmount() == null ? 0L : req.minOrderAmount())
                .maxDiscountAmount(req.maxDiscountAmount())
                .validFrom(req.validFrom())
                .validUntil(req.validUntil())
                .build());
        log.info("Coupon created: {} ({})", coupon.getId(), coupon.getName());
        return coupon.getId();
    }

    /** 쿠폰 정의 목록(관리자). 정렬 미지정 시 최신 생성순. */
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> listAll(Pageable pageable) {
        Pageable p = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(couponRepository.findAll(p).map(CouponResponse::from));
    }

    /**
     * 가입 시 자동 발급되는 쿠폰 — <b>비로그인도 볼 수 있는 공개 정보</b>다(G-2).
     *
     * <p>화면이 *"가입하면 5,000원 쿠폰"* 을 쓰려면 <b>그 쿠폰이 실제로 있는지</b>를 서버가 답해야 한다.
     * 지정된 게 없으면(기능 꺼짐) <b>비어 있는 값</b>을 주고, 화면은 그때 문구를 감춘다 —
     * 정책을 화면에 적으면 설정만 바꿨을 때 <b>안내가 거짓말이 된다</b>(`HomeView` 혜택 스트립의 원칙).
     *
     * <p>⚠ 지정은 <b>설정이 아니라 데이터</b>다(V36) — 예전엔 {@code .env} 의 쿠폰 id 였는데
     * 바꿀 때마다 재시작이 필요했고, 무엇이 가입 쿠폰인지 화면에서 안 보였다.
     */
    @Transactional(readOnly = true)
    public Optional<CouponResponse> welcomeCoupon() {
        return couponRepository.findByWelcomeTrue().map(CouponResponse::from);
    }

    /**
     * 가입 쿠폰으로 지정/해제(관리자, V36).
     *
     * <p>⚠ 지정할 때 <b>기존 지정을 먼저 해제</b>한다 — 같은 트랜잭션이라 중간 상태가 밖에서 안 보인다.
     * 그래도 두 관리자가 동시에 지정하면 서로의 미커밋 변경을 못 봐 둘 다 1 이 될 수 있는데,
     * 그건 <b>함수기반 유니크 인덱스가 DB 에서 막는다</b>(V36). 앱과 DB 가 같은 규칙을 이중으로 지킨다.
     */
    @Transactional
    public void setWelcome(UUID couponId, boolean welcome) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        if (welcome) {
            couponRepository.findByWelcomeTrue()
                    .filter(current -> !current.getId().equals(couponId))
                    .ifPresent(current -> current.markWelcome(false));
        }
        coupon.markWelcome(welcome);
        log.info("Welcome coupon {}: {} ({})", welcome ? "designated" : "cleared", couponId, coupon.getName());
    }

    /** 회원에게 발급(관리자). 같은 쿠폰을 여러 장 주는 것도 허용한다(이벤트 재발급 등). */
    @Transactional
    public UUID issue(UUID couponId, UUID memberId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        MemberCoupon issued = memberCouponRepository.save(MemberCoupon.issue(memberId, coupon));
        log.info("Coupon issued: {} to {}", couponId, memberId);
        return issued.getId();
    }

    /**
     * 내 쿠폰 목록(미사용). {@code itemsTotal} 기준으로 지금 얼마 깎이는지·쓸 수 있는지를 함께 준다
     * — 화면이 할인 규칙(정액/정률·상한·최소주문금액)을 알 필요가 없다.
     */
    @Transactional(readOnly = true)
    public List<MemberCouponResponse> myCoupons(UUID memberId, long itemsTotal) {
        Instant now = Instant.now();
        return memberCouponRepository.findUnusedByMember(memberId).stream()
                .map(mc -> MemberCouponResponse.of(mc, itemsTotal, now))
                .toList();
    }

    /**
     * 쿠폰을 사용 처리하고 할인액을 돌려준다 — <b>order 도메인의 진입점</b>.
     *
     * <p>검증과 사용처리를 한 곳에서 하는 이유: 호출부가 "검증했으니 이제 써도 되겠지"로 나뉘면
     * 그 사이에 같은 쿠폰이 두 번 쓰일 여지가 생긴다. 주문 트랜잭션 안에서 호출되므로
     * 주문이 롤백되면 사용처리도 함께 롤백된다.
     *
     * @param itemsTotal <b>할인 전</b> 상품합계. 배송비는 넘기지 않는다 —
     *                   무료배송 기준도 할인 전 금액으로 판단한다(2026-07-23 결정).
     */
    @Transactional
    public long redeem(UUID memberCouponId, UUID memberId, long itemsTotal) {
        MemberCoupon mc = memberCouponRepository.findById(memberCouponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        // 남의 쿠폰은 "없는 것"으로 답한다 — 존재 여부를 알려주지 않는다.
        if (!mc.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_FOUND);
        }
        if (mc.isUsed()) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
        }
        Coupon coupon = mc.getCoupon();
        if (!coupon.isValidAt(Instant.now())) {
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        if (!coupon.meetsMinOrder(itemsTotal)) {
            throw new BusinessException(ErrorCode.COUPON_MIN_ORDER_NOT_MET);
        }
        mc.use();
        long discount = coupon.discountFor(itemsTotal);
        log.info("Coupon redeemed: {} by {} (-{}원)", memberCouponId, memberId, discount);
        return discount;
    }

    /**
     * 사용한 쿠폰을 <b>되돌린다</b> — 주문 취소·반품 승인의 진입점 (2026-08-11, 08-10 §16-4 3번).
     *
     * <p>⚠ <b>이 자리가 통째로 비어 있었다.</b> 취소는 재고와 적립금을 되돌리면서 쿠폰은 그냥 뒀다 —
     * 고객이 5,000원짜리 쿠폰을 쓰고 취소하면 <b>주문도 없고 쿠폰도 없는</b> 상태가 됐다.
     * 적립금이 2026-08-07 에 정확히 같은 이유로 빠져 있었고(«반품만 고쳐진» 비대칭), 그때
     * 되돌리는 것들을 한 줄에 모으지 않아 <b>쿠폰이 또 빠졌다.</b>
     *
     * <p>⚠ <b>없는 쿠폰은 조용히 넘긴다</b>(예외를 던지지 않는다). 취소 트랜잭션 안에서 도는데
     * 여기서 던지면 <b>취소 자체가 롤백</b>돼, 고객은 «쿠폰이 안 돌아온» 게 아니라 «취소가 안 되는»
     * 상태가 된다 — 더 나쁘다. 발급쿠폰은 탈퇴 정리({@link #deleteAllForMember})로 사라질 수 있고,
     * 그때 그 회원은 이미 없으므로 되돌릴 대상도 의미가 없다.
     * ⚠ 대신 <b>로그로 남긴다</b> — 조용히 넘기는 것과 아무도 모르는 것은 다르다.
     *
     * @param memberCouponId 주문이 스냅샷한 발급쿠폰 id(V46). 쿠폰을 안 쓴 주문이면 null 이 온다.
     */
    @Transactional
    public void restore(UUID memberCouponId) {
        if (memberCouponId == null) {
            return; // 쿠폰을 안 쓴 주문 — 호출부가 갈라 두지 않아도 되게 여기서 받는다
        }
        memberCouponRepository.findById(memberCouponId).ifPresentOrElse(
                mc -> {
                    if (mc.restore()) {
                        log.info("Coupon restored: {} for member {}", memberCouponId, mc.getMemberId());
                    }
                },
                () -> log.warn("Coupon restore skipped — member_coupon not found: {} "
                        + "(탈퇴 정리로 지워졌거나 V46 이전 주문)", memberCouponId));
    }

    /** 주문에 스냅샷할 쿠폰명 — order 가 쿠폰 엔티티를 직접 보지 않게 한다. */
    @Transactional(readOnly = true)
    public String nameOf(UUID memberCouponId) {
        return memberCouponRepository.findById(memberCouponId)
                .map(mc -> mc.getCoupon().getName())
                .orElse(null);
    }

    /**
     * 회원 삭제 정리(F-1) — <b>발급된 쿠폰만</b> 지운다. 쿠폰 정의({@code coupon})는 다른 회원도 쓰므로 남는다.
     */
    @Transactional
    public void deleteAllForMember(UUID memberId) {
        long deleted = memberCouponRepository.deleteByMemberId(memberId);
        log.info("Member coupons deleted for member {}: {}", memberId, deleted);
    }
}

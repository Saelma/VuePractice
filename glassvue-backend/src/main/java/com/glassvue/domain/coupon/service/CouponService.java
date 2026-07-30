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

package com.glassvue.domain.coupon.service;

import com.glassvue.domain.coupon.dto.CouponCreateRequest;
import com.glassvue.domain.coupon.dto.CouponResponse;
import com.glassvue.domain.coupon.dto.EventCouponResponse;
import com.glassvue.domain.coupon.dto.MemberCouponResponse;
import com.glassvue.domain.coupon.dto.PromotionCalendarResponse;
import com.glassvue.domain.coupon.dto.PromotionSpanResponse;
import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.MemberCoupon;
import com.glassvue.domain.coupon.repository.CouponRepository;
import com.glassvue.domain.coupon.repository.MemberCouponRepository;
import com.glassvue.global.exception.BusinessException;
import com.glassvue.global.exception.ErrorCode;
import com.glassvue.global.response.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
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

    /** D-day 는 «날짜의 차» 라 경계를 정해야 센다 — 매출 통계가 쓰는 것과 같은 KST 다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 관리자에게 보여줄 날짜 — UTC 로 적으면 «8/14 쿠폰» 이 8/13 으로 보여 더 헷갈린다. */
    private static final DateTimeFormatter KST_DAY =
            DateTimeFormatter.ofPattern("M월 d일").withZone(KST);

    private final CouponRepository couponRepository;
    private final MemberCouponRepository memberCouponRepository;

    /**
     * 쿠폰 생성(관리자).
     *
     * <p>{@code issueUntil} 이 오면 <b>이벤트 쿠폰</b>이다(G-8, V49) — 그때만 아래 검증 셋이 돈다.
     * 비우고 만들면 지금까지와 똑같은 상시 쿠폰이다.
     */
    @Transactional
    public UUID create(CouponCreateRequest req) {
        if (req.issueUntil() != null) {
            validateEventWindow(req.validFrom(), req.issueUntil(), req.validUntil());
        }
        Coupon coupon = couponRepository.save(Coupon.builder()
                .name(req.name())
                .discountType(req.discountType())
                .discountValue(req.discountValue())
                .minOrderAmount(req.minOrderAmount() == null ? 0L : req.minOrderAmount())
                .maxDiscountAmount(req.maxDiscountAmount())
                .validFrom(req.validFrom())
                .validUntil(req.validUntil())
                .issueUntil(req.issueUntil())
                .build());
        log.info("Coupon created: {} ({}){}", coupon.getId(), coupon.getName(),
                coupon.isEventCoupon() ? " [event, 발급마감 " + coupon.getIssueUntil() + "]" : "");
        return coupon.getId();
    }

    /**
     * 이벤트 쿠폰 등록 검증 셋(G-8).
     *
     * <p>🔴 <b>앱이 유일한 방어다.</b> Oracle 유니크 인덱스로는 «기간 겹침» 을 못 막는다 —
     * {@code welcome} 처럼 «행이 하나» 를 막는 것이면 함수기반 인덱스로 됐겠지만, 여기서 막아야 하는
     * 것은 <b>미래 이벤트를 여러 개 등록해 두되 발급 창만 안 겹치게</b> 하는 것이다
     * (달력 예고를 하려면 미래 행이 여러 개 있어야 한다).
     * → DB 가 받쳐 주지 않으므로 «겹치는 둘을 등록» 을 <b>테스트로 못 박아 둔다.</b>
     */
    private void validateEventWindow(Instant validFrom, Instant issueUntil, Instant validUntil) {
        // 발급 창이 뒤집히면(마감이 시작보다 앞) 아무도 못 받는 이벤트가 조용히 등록된다.
        if (issueUntil.isBefore(validFrom)) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_WINDOW_INVALID);
        }
        // 받자마자 만료된 쿠폰을 내보내지 않는다 — 발급 창이 사용 기간 안에 들어 있어야 한다.
        if (validUntil.isBefore(issueUntil)) {
            throw new BusinessException(ErrorCode.COUPON_ISSUE_WINDOW_INVALID);
        }
        List<Coupon> conflicts = couponRepository.findEventsOverlapping(validFrom, issueUntil);
        if (!conflicts.isEmpty()) {
            // 🔴 «겹친다» 만 말하면 확인할 방법이 없다 — 관리자는 목록에서 발급 창을 눈으로 맞춰 봐야 하고,
            //    상시 쿠폰까지 섞여 있어 «없는데 왜 겹치냐» 가 된다(2026-08-13 검증에서 실제로 그랬다).
            //    → **무엇과 겹치는지 이름과 창을 함께** 준다. 화면은 서버 문구를 그대로 띄운다.
            String detail = conflicts.stream()
                    .map(c -> "%s (%s ~ %s)".formatted(c.getName(),
                            KST_DAY.format(c.getValidFrom()), KST_DAY.format(c.getIssueUntil())))
                    .collect(Collectors.joining(", "));
            throw new BusinessException(ErrorCode.COUPON_EVENT_OVERLAP,
                    "발급 기간이 겹칩니다 — " + detail);
        }
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

    /**
     * 회원에게 발급(관리자).
     *
     * <p>⚠ <b>2026-08-13 에 규칙이 바뀌었다</b>(G-8, V49): 예전엔 *"같은 쿠폰을 여러 장 주는 것도
     * 허용한다(이벤트 재발급 등)"* 였는데, 유니크 인덱스 {@code ux_member_coupon_once} 가 생기면서
     * <b>회원당 같은 쿠폰 1장</b>이 전역 규칙이 됐다. 여기서 미리 확인하지 않으면 관리자에게
     * <b>제약 위반 500</b> 이 나간다 — 뜻이 있는 4xx 로 답한다.
     */
    @Transactional
    public UUID issue(UUID couponId, UUID memberId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        if (memberCouponRepository.existsByMemberIdAndCouponId(memberId, couponId)) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }
        MemberCoupon issued = memberCouponRepository.save(MemberCoupon.issue(memberId, coupon));
        log.info("Coupon issued: {} to {}", couponId, memberId);
        return issued.getId();
    }

    /**
     * 오늘 그릴 이벤트 배너 — <b>비로그인도 부를 수 있는 공개 정보</b>다(G-8).
     *
     * <p>답은 셋 중 하나다: 오늘 진행 중({@code open}) · 앞으로 있음(예고) · <b>비어 있음</b>.
     * ⚠ 마지막이면 화면은 <b>배너를 안 그린다</b> — *"예정된 이벤트가 없습니다"* 는 자리만 먹는다.
     *
     * <p>⚠ {@code memberId} 가 null(비로그인)이면 «이미 받았나» 를 <b>묻지 않는다</b> — 쿼리 한 방이
     * 줄기도 하지만, 그보다 비로그인 화면은 그 값을 쓰지 않기 때문이다(예고도 안 띄운다).
     *
     * <p>🔴 <b>D-day 는 KST 로 센다.</b> «며칠 남았나» 는 시각의 차가 아니라 <b>날짜의 차</b>다 —
     * 오늘 23:00 에서 내일 09:00 은 10시간 뒤지만 D-1 이다. 초 단위로 나누면 D-0 이 되어 틀린다.
     */
    @Transactional(readOnly = true)
    public Optional<EventCouponResponse> eventBanner(UUID memberId) {
        Instant now = Instant.now();
        Optional<Coupon> openNow = couponRepository.findIssuableAt(now);
        if (openNow.isPresent()) {
            Coupon coupon = openNow.get();
            boolean claimed = memberId != null
                    && memberCouponRepository.existsByMemberIdAndCouponId(memberId, coupon.getId());
            return Optional.of(EventCouponResponse.open(coupon, claimed));
        }
        return couponRepository.findUpcomingEvents(now, Limit.of(1)).stream()
                .findFirst()
                .map(coupon -> EventCouponResponse.upcoming(coupon, daysUntilKst(now, coupon.getValidFrom())));
    }

    private static int daysUntilKst(Instant now, Instant start) {
        return (int) ChronoUnit.DAYS.between(LocalDate.ofInstant(now, KST), LocalDate.ofInstant(start, KST));
    }

    /**
     * 프로모션 달력 한 달치(B-27, 관리자).
     *
     * <p>🔴 <b>착수 조건이 G-8 로 채워졌다</b> — 그전까지 쿠폰 5개가 <b>전부 상시</b>라 달력에 그려도
     * 가로줄 다섯 개일 뿐 목록보다 나은 게 없었다. <b>겹침이 정보가 되려면 기간이 갈려야</b> 하고,
     * 이벤트 쿠폰이 그 갈림을 만든다.
     *
     * <p>⚠ 이벤트 쿠폰은 막대를 <b>둘</b> 낸다(발급 창 · 사용 기간). 겹치면 안 되는 것은 앞엣것뿐이라
     * 화면이 갈라 그린다 — 한 색으로 그리면 <b>정상인 사용 기간 겹침을 사고로 읽는다.</b>
     *
     * <p>⚠ 지금 규모(쿠폰 한 자리 수)에선 한 달치를 통째로 읽어 화면에 넘긴다. 페이징도 캐시도 두지
     * 않는다 — <b>미리 만들지 않는다.</b>
     */
    @Transactional(readOnly = true)
    public PromotionCalendarResponse promotionCalendar(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        // 경계는 KST 로 만든다 — 「8월」은 UTC 의 8월이 아니라 한국의 8월이다.
        Instant from = first.atStartOfDay(KST).toInstant();
        Instant to = last.plusDays(1).atStartOfDay(KST).toInstant().minusNanos(1);

        List<PromotionSpanResponse> spans = new ArrayList<>();
        for (Coupon c : couponRepository.findAliveBetween(from, to)) {
            spans.add(PromotionSpanResponse.use(c, first, last, KST));
            // 발급 창이 이 달에 안 걸치면(지난달에 끝난 이벤트 등) 막대를 만들지 않는다.
            if (c.isEventCoupon() && !c.getIssueUntil().isBefore(from) && !c.getValidFrom().isAfter(to)) {
                spans.add(PromotionSpanResponse.issue(c, first, last, KST));
            }
        }
        return new PromotionCalendarResponse(
                month.toString(), month.lengthOfMonth(), first.getDayOfWeek().getValue(), spans);
    }

    /**
     * 이벤트 쿠폰 「받기」(G-8) — 이 기능의 본체.
     *
     * <p>🔴 <b>자동 발급이 아니라 버튼이다</b>(2026-08-12 결정). 누른 사람만 발급 로직을 타고,
     * 누른 사람은 <b>자기가 받은 것을 안다</b> — «모르는 채 받는 것» 보다 «알고 받는 것» 이
     * 쿠폰의 목적(다시 오게 하는 것)에 맞는다.
     *
     * <p>⚠ <b>중복 방어가 두 층이다.</b> 아래 {@code exists} 는 흔한 경우(이미 받은 사람이 다시 누름)에
     * 뜻이 있는 답을 주기 위한 것이고, <b>동시에 누른 두 요청은 이게 못 막는다</b> — 둘 다 «없다» 를
     * 읽기 때문이다. 그 자리는 유니크 인덱스가 막고, 여기서는 그 예외를 <b>같은 뜻의 4xx 로 번역</b>한다.
     * 🔴 그래서 {@code saveAndFlush} 다 — 커밋까지 미루면 예외가 이 메서드 밖에서 터져
     * <b>번역할 자리를 놓친다</b>(핸들러가 500 으로 답한다).
     *
     * @return 발급된 member_coupon id
     */
    @Transactional
    public UUID claimEventCoupon(UUID memberId) {
        Coupon coupon = couponRepository.findIssuableAt(Instant.now())
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_CLOSED));
        if (memberCouponRepository.existsByMemberIdAndCouponId(memberId, coupon.getId())) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }
        try {
            MemberCoupon issued = memberCouponRepository.saveAndFlush(MemberCoupon.issue(memberId, coupon));
            log.info("Event coupon claimed: {} ({}) by {}", coupon.getId(), coupon.getName(), memberId);
            return issued.getId();
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 둘이 같은 순간 «없다» 를 읽은 경우. 한 장은 나갔으니 실패가 아니라 «이미 받음» 이다.
            log.info("Event coupon claim raced — unique index held: {} by {}", coupon.getId(), memberId);
            throw new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED);
        }
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

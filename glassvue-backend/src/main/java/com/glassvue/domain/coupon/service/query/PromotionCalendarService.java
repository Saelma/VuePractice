package com.glassvue.domain.coupon.service.query;

import com.glassvue.domain.catalog.dto.ProductSaleResponse;
import com.glassvue.domain.catalog.service.query.ProductQueryService;
import com.glassvue.domain.coupon.dto.PromotionCalendarResponse;
import com.glassvue.domain.coupon.dto.PromotionSpanResponse;
import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.repository.CouponRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로모션 달력(B-27, 관리자 전용) — <b>여러 도메인의 기간을 한 격자에 얹는 조립물</b>.
 *
 * <p>🔴 <b>목록으로는 안 보이고 달력으로만 보이는 질문 하나</b>에 답한다:
 * *"이 날 무엇이 <b>동시에</b> 돌고 있나"*. 할인이 겹치면 마진이 겹쳐서 깎인다.
 *
 * <p>🔴 <b>2026-08-19 에 {@code CouponService} 에서 떼어냈다.</b> 그전엔 쿠폰만 그렸으니 거기 있어도
 * 됐지만, G-5(상품 기간 할인)가 들어오면서 <b>«쿠폰 도메인 서비스가 상품 세일을 조립한다»</b> 가 됐다.
 * 조립은 조립하는 자리에서 한다 — 이름과 하는 일이 어긋나면 다음 사람이 여기 발송·기획전을 얹을 때
 * <b>또 쿠폰 서비스를 열게 된다.</b>
 *
 * <p>⚠ <b>그래도 coupon 패키지 안이다</b>(catalog 도 아니고 새 도메인도 아니다).
 * 달력의 뿌리는 쿠폰이고 URL·화면도 쿠폰 쪽에 있다. 상품은 <b>공개 API 로만</b> 읽는다
 * ({@link ProductQueryService#salesBetween}) — 장바구니·찜이 catalog 를 읽는 것과 같은 방식이라
 * catalog 를 폴더째 들어내도 여기만 고치면 된다(CLAUDE.md — 도메인 간 직접 참조 금지).
 * ⚠ 발송·기획전까지 여기 모이면 그때는 <b>promotion 도메인을 세울 때</b>다.
 *
 * <p>⚠ 화면이 달력 격자를 그리려면 «그 달 1일이 무슨 요일인가» 를 알아야 하는데, 그건 화면이
 * 계산해도 시간대에 안 흔들린다(순수 달력 산수). <b>흔들리는 것은 막대의 날짜</b>라 그쪽만 여기서 자른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionCalendarService {

    /** 「8월」은 UTC 의 8월이 아니라 <b>한국의 8월</b>이다. 경계를 만드는 곳이 여기 하나여야 한다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CouponRepository couponRepository;
    private final ProductQueryService productQueryService;

    public PromotionCalendarResponse calendar(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
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

        // 🔴 **상품 세일**(G-5, 2026-08-19). 이게 얹히면서 이 화면이 원래 답하려던 질문
        //    («다음 주에 뭐가 겹치나»)에 비로소 답한다 — 그전엔 겹칠 것이 쿠폰뿐이었다.
        // ⚠ 삭제 대기 상품의 세일은 애초에 안 온다(catalog 가 걸러 준다, F-7).
        for (ProductSaleResponse sale : productQueryService.salesBetween(from, to)) {
            spans.add(PromotionSpanResponse.sale(sale, first, last, KST));
        }

        return new PromotionCalendarResponse(
                month.toString(), month.lengthOfMonth(), first.getDayOfWeek().getValue(), spans);
    }
}

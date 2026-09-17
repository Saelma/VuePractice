package com.glassvue.domain.audit;

import static com.glassvue.domain.audit.entity.AuditAction.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.audit.entity.AuditAction;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 쓰기 엔드포인트의 <b>감사 덮개</b> (2026-09-11, BACKLOG O-6 · WA §2-12).
 *
 * <p>🔴 <b>관리자 조작이 원장에 안 남아도 아무것도 안 터진다.</b> 09-11 에 전부 대조하니 셋이 빠져 있었다 —
 * 마케팅 발송(되돌릴 수 없는 방송), 관리자가 남의 리뷰·문의를 삭제. 뒤의 둘은 <b>작성자와 같은 경로</b>라
 * «{@code /api/admin/**} 만 보면 된다» 는 눈에 안 걸렸다. 그래서 범위를 <b>쓰기 전체</b>로 잡았다(사용자 결정).
 *
 * <p>→ 모든 쓰기 엔드포인트는 {@link #AUDITED}(어떤 조작을 남기나) 또는 {@link #NOT_AUDITED}(왜 안 남기나)
 * 중 <b>정확히 하나</b>에 있어야 한다. 새 쓰기 API 를 만들면 여기서 빨개지고, <b>둘 중 하나를 고르게 된다.</b>
 * 목록은 핸들러 매핑(<b>실물</b>)과 대조한다 — 소스를 긁지 않는다(WA §3-6).
 *
 * <p>⚠ <b>이 테스트는 «발행한다» 를 증명하지 않는다</b> — 그건 조작별 통합·단위 테스트의 몫이다
 * ({@code ContentAuditIntegrationTest} 등). 여기가 지키는 것은 <b>«결정을 했다»</b> 와
 * <b>«감사 값마다 그걸 내는 엔드포인트가 있다»</b> 둘이다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class WriteEndpointAuditCoverageTest {

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    private static final Set<RequestMethod> WRITES =
            EnumSet.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    /**
     * 원장에 남기는 쓰기. ⚠ <b>공용 경로</b>(작성자·관리자가 같이 쓰는 리뷰·문의 삭제)는 <b>관리자가 남의 것을
     * 지울 때만</b> 남는다 — 본인 삭제는 «고객 본인의 조작» 이다({@code AuditAction} javadoc).
     */
    private static final Map<String, Set<AuditAction>> AUDITED = Map.ofEntries(
            // 회원
            Map.entry("POST /api/admin/members/{memberId}/suspend", Set.of(MEMBER_SUSPEND)),
            Map.entry("POST /api/admin/members/{memberId}/unsuspend", Set.of(MEMBER_UNSUSPEND)),
            Map.entry("PATCH /api/admin/members/{memberId}/role", Set.of(MEMBER_ROLE_CHANGE)),
            Map.entry("DELETE /api/admin/members/{memberId}", Set.of(MEMBER_DELETE)),
            // 주문 — 관리자 진행·대행
            Map.entry("POST /api/orders/{id}/ship", Set.of(ORDER_SHIP)),
            Map.entry("POST /api/orders/{id}/deliver", Set.of(ORDER_DELIVER)),
            Map.entry("POST /api/orders/{id}/admin-cancel", Set.of(ORDER_CANCEL)),
            Map.entry("POST /api/orders/{id}/admin-cancel-item", Set.of(ORDER_ITEM_CANCEL)),
            Map.entry("POST /api/orders/{id}/admin-return-request", Set.of(ORDER_RETURN_REQUEST)),
            Map.entry("POST /api/orders/{id}/return-approve", Set.of(ORDER_RETURN_APPROVE)),
            Map.entry("POST /api/orders/{id}/return-reject", Set.of(ORDER_RETURN_REJECT)),
            // 콘텐츠
            Map.entry("POST /api/admin/reviews/{id}/hide", Set.of(REVIEW_HIDE)),
            Map.entry("POST /api/admin/reviews/{id}/unhide", Set.of(REVIEW_UNHIDE)),
            Map.entry("POST /api/admin/inquiries/{id}/hide", Set.of(INQUIRY_HIDE)),
            Map.entry("POST /api/admin/inquiries/{id}/unhide", Set.of(INQUIRY_UNHIDE)),
            Map.entry("POST /api/inquiries/{id}/answer", Set.of(INQUIRY_ANSWER)),
            Map.entry("DELETE /api/reviews/{id}", Set.of(REVIEW_DELETE)),       // 관리자가 남의 것일 때만
            Map.entry("DELETE /api/inquiries/{id}", Set.of(INQUIRY_DELETE)),    // 관리자가 남의 것일 때만
            // 상품·할인·카테고리
            Map.entry("POST /api/products", Set.of(PRODUCT_CREATE)),
            Map.entry("PUT /api/products/{id}", Set.of(PRODUCT_UPDATE)),
            Map.entry("DELETE /api/products/{id}", Set.of(PRODUCT_DELETE)),
            Map.entry("POST /api/admin/products/{id}/restore", Set.of(PRODUCT_RESTORE)),
            Map.entry("POST /api/admin/products/{productId}/discounts", Set.of(DISCOUNT_CREATE)),
            Map.entry("PUT /api/admin/products/{productId}/discounts/{discountId}", Set.of(DISCOUNT_UPDATE)),
            Map.entry("DELETE /api/admin/products/{productId}/discounts/{discountId}", Set.of(DISCOUNT_DELETE)),
            Map.entry("POST /api/categories", Set.of(CATEGORY_CREATE)),
            Map.entry("DELETE /api/categories/{id}", Set.of(CATEGORY_DELETE)),
            // 쿠폰
            Map.entry("POST /api/admin/coupons", Set.of(COUPON_CREATE)),
            Map.entry("POST /api/admin/coupons/{couponId}/issue", Set.of(COUPON_ISSUE)),
            Map.entry("POST /api/admin/coupons/{couponId}/welcome", Set.of(COUPON_WELCOME_SET)),
            Map.entry("DELETE /api/admin/coupons/{couponId}/welcome", Set.of(COUPON_WELCOME_SET)),
            Map.entry("DELETE /api/admin/coupons/{couponId}/issued/{memberCouponId}", Set.of(COUPON_REVOKE)),
            Map.entry("DELETE /api/admin/coupons/{couponId}", Set.of(COUPON_DELETE)),
            // 공지·마케팅
            Map.entry("POST /api/notices", Set.of(NOTICE_CREATE)),
            Map.entry("PUT /api/notices/{id}", Set.of(NOTICE_UPDATE)),
            Map.entry("DELETE /api/notices/{id}", Set.of(NOTICE_DELETE)),
            Map.entry("POST /api/admin/notifications/marketing", Set.of(MARKETING_SEND)));

    private static final String SELF = "고객 본인의 조작 — 사실은 그 행(주문·장바구니·회원 자신)이 갖는다";
    private static final String AUTH = "인증 흐름 — 관리자 조작이 아니다";

    /** 원장에 <b>안</b> 남기는 쓰기 — 이유와 함께. ⚠ «빠진 것» 이 아니라 «안 남기기로 한 것» 만 여기 둔다. */
    private static final Map<String, String> NOT_AUDITED = Map.ofEntries(
            Map.entry("POST /api/auth/signup", AUTH),
            Map.entry("POST /api/auth/login", AUTH),
            Map.entry("POST /api/auth/logout", AUTH),
            Map.entry("POST /api/auth/refresh", AUTH),
            Map.entry("POST /api/auth/find-id", AUTH),
            Map.entry("POST /api/auth/password-reset/request", AUTH),
            Map.entry("POST /api/auth/password-reset/confirm", AUTH),

            Map.entry("DELETE /api/members/me", SELF + " (탈퇴 — MemberWithdrawnEvent 가 정리한다)"),
            Map.entry("PATCH /api/members/me/email", SELF),
            Map.entry("PATCH /api/members/me/nickname", SELF),
            Map.entry("PATCH /api/members/me/password", SELF),
            Map.entry("PATCH /api/members/me/shipping-address", SELF),
            Map.entry("POST /api/members/me/email/verification", SELF),
            Map.entry("POST /api/members/me/email/verification/confirm", SELF),
            Map.entry("POST /api/members/me/addresses", SELF),
            Map.entry("PUT /api/members/me/addresses/{addressId}", SELF),
            Map.entry("PATCH /api/members/me/addresses/{addressId}/default", SELF),
            Map.entry("DELETE /api/members/me/addresses/{addressId}", SELF),

            Map.entry("POST /api/cart/items", SELF),
            Map.entry("PATCH /api/cart/items/{variantId}", SELF),
            Map.entry("DELETE /api/cart/items/{variantId}", SELF),
            Map.entry("DELETE /api/cart", SELF),
            Map.entry("POST /api/wishlist/{productId}", SELF),
            Map.entry("DELETE /api/wishlist/{productId}", SELF),
            Map.entry("POST /api/restock/{productId}", SELF),
            Map.entry("DELETE /api/restock/{productId}", SELF),
            Map.entry("POST /api/coupons/event/claim", SELF + " (이벤트 쿠폰 「받기」)"),

            Map.entry("POST /api/orders", SELF),
            Map.entry("POST /api/orders/{id}/pay", SELF),
            Map.entry("POST /api/orders/{id}/cancel", SELF + " — 소유 기준(findByIdAndMemberId)이라 관리자도 남의 것은 못 부른다"),
            Map.entry("POST /api/orders/{id}/cancel-item", SELF + " — 위와 같다"),
            Map.entry("POST /api/orders/{id}/return-request", SELF),

            Map.entry("POST /api/products/{productId}/reviews", SELF),
            Map.entry("PUT /api/reviews/{id}", SELF + " — 🔴 작성자만 고친다(관리자도 못 고친다, O-6)"),
            Map.entry("POST /api/products/{productId}/inquiries", SELF),
            Map.entry("POST /api/inquiries", SELF + " (일반 문의)"),
            Map.entry("PUT /api/inquiries/{id}", SELF + " — 작성자만 고친다"),

            Map.entry("POST /api/notifications/{id}/read", SELF),
            Map.entry("POST /api/notifications/read-all", SELF),
            Map.entry("PUT /api/notifications/settings", SELF),

            Map.entry("POST /api/notices/{id}/views", "조회 — 고객이 읽은 것이고 Redis 누적이라 트랜잭션도 없다"),
            Map.entry("POST /api/images", "업로드 자체는 아무것도 안 바꾼다 — 붙이는 조작(상품 등록·수정)이 남는다"),
            Map.entry("POST /api/admin/images/derivatives", "멱등 유지보수 도구 — 빈 파생본 URL 을 채울 뿐 업무 데이터의 뜻을 안 바꾼다"));

    private Set<String> writeEndpoints() {
        Set<String> keys = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                info.getMethodsCondition().getMethods().stream()
                        .filter(WRITES::contains)
                        .forEach(m -> keys.add(m.name() + " " + pattern));
            }
        }
        return keys;
    }

    @Test
    @DisplayName("🔴 모든 쓰기 엔드포인트는 «남긴다(무엇을)» 또는 «안 남긴다(이유)» 중 정확히 하나로 정해져 있다")
    void everyWriteEndpointIsDeliberatelyClassified() {
        Set<String> seen = writeEndpoints();
        assertThat(seen).as("쓰기 엔드포인트를 하나도 못 읽었다 — 핸들러 매핑이 헛돈다(WA §3-6)").isNotEmpty();

        Set<String> both = new HashSet<>(AUDITED.keySet());
        both.retainAll(NOT_AUDITED.keySet());
        assertThat(both).as("양쪽에 다 적혀 있다 — 하나만 고른다").isEmpty();

        List<String> undecided = seen.stream()
                .filter(k -> !AUDITED.containsKey(k) && !NOT_AUDITED.containsKey(k)).toList();
        assertThat(undecided)
                .as("""
                        감사 여부가 정해지지 않은 쓰기 엔드포인트다.
                        관리자가 누르는 것이면 AuditAction 을 더해 발행하고 AUDITED 에, 아니면 NOT_AUDITED 에 이유와 함께.
                        (값을 더하면 CHECK 제약·프론트 라벨이 따라온다 — AuditAction javadoc)""")
                .isEmpty();

        Set<String> stale = new TreeSet<>(AUDITED.keySet());
        stale.addAll(NOT_AUDITED.keySet());
        stale.removeAll(seen);
        assertThat(stale).as("목록에 있는데 엔드포인트가 없다 — 목록이 낡았다").isEmpty();

        // «다 셌는가» — 목록과 실물이 같은 크기여야 한다(위 넷이 성립하면 따라오지만, 숫자로 한 번 더 못박는다).
        assertThat(seen).hasSize(AUDITED.size() + NOT_AUDITED.size());
    }

    @Test
    @DisplayName("감사 값마다 그걸 내는 엔드포인트가 목록에 있다 — 엔드포인트 없이 값만 늘거나, 엔드포인트가 사라진 값이 없다")
    void everyAuditActionHasAnEndpoint() {
        Set<AuditAction> mapped = AUDITED.values().stream().flatMap(Set::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AuditAction.class)));
        assertThat(EnumSet.complementOf(EnumSet.copyOf(mapped)))
                .as("어느 엔드포인트도 이 조작을 낸다고 적혀 있지 않다")
                .isEmpty();
    }
}

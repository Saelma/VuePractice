package com.glassvue.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.repository.AdminAuditLogRepository;
import com.glassvue.domain.coupon.entity.Coupon;
import com.glassvue.domain.coupon.entity.DiscountType;
import com.glassvue.domain.coupon.entity.MemberCoupon;
import com.glassvue.domain.coupon.repository.CouponRepository;
import com.glassvue.domain.coupon.repository.MemberCouponRepository;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryType;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.MemberAddress;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberAddressRepository;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.notification.entity.Notification;
import com.glassvue.domain.notification.entity.NotificationPref;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationPrefRepository;
import com.glassvue.domain.notification.repository.NotificationRepository;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.repository.OrderRepository;
import com.glassvue.domain.point.entity.PointAccount;
import com.glassvue.domain.point.entity.PointHistory;
import com.glassvue.domain.point.repository.PointAccountRepository;
import com.glassvue.domain.point.repository.PointHistoryRepository;
import com.glassvue.domain.restock.entity.RestockSubscription;
import com.glassvue.domain.restock.repository.RestockSubscriptionRepository;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.domain.review.repository.ReviewRepository;
import com.glassvue.domain.wishlist.entity.Wishlist;
import com.glassvue.domain.wishlist.repository.WishlistRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 삭제 정리(F-1) + 관리자 강제 삭제(B-24) — <b>지운 것과 남긴 것을 함께</b> 본다.
 *
 * <p>왜 필요했나: 도메인 간 FK 가 없어서 회원 행을 지워도 **다른 도메인 데이터는 그대로 남았다.**
 * 실측(2026-07-30) 고아 {@code point_account} 9행, 그 중 가장 뾰족한 것은 수령인·전화번호·주소가 든
 * {@code member_address} 였다(탈퇴는 "내 정보를 지워 달라"는 뜻인데 개인정보가 남는다).
 *
 * <p>⚠ <b>"다 지워졌다"만 보면 안 된다.</b> 정리가 너무 넓으면 주문·리뷰·공지가 함께 사라지는데,
 * 그건 원래 문제보다 큰 사고다(매출 근거와 다른 고객이 보는 콘텐츠가 날아간다). 그래서 이 테스트는
 * <b>남아야 하는 것</b>을 같은 무게로 단언한다.
 *
 * <p>DB_HOST 있을 때만 실행, {@code @Transactional} 롤백으로 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberPurgeIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberAddressRepository addressRepository;
    @Autowired WishlistRepository wishlistRepository;
    @Autowired PointAccountRepository pointAccountRepository;
    @Autowired PointHistoryRepository pointHistoryRepository;
    @Autowired CouponRepository couponRepository;
    @Autowired MemberCouponRepository memberCouponRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationPrefRepository notificationPrefRepository;
    @Autowired RestockSubscriptionRepository restockRepository;
    @Autowired InquiryRepository inquiryRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;
    // 장바구니는 Redis 다(2026-08-11) — 리포지토리가 없어 여기서만 모양이 다르다.
    @Autowired com.glassvue.domain.cart.CartStore cartStore;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;

    private static final String PW = "password123";

    private String targetLoginId;
    private String superLoginId;
    private String adminLoginId;
    private UUID targetId;
    private UUID productId;
    private UUID variantId;
    private UUID reviewId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        targetLoginId = "zzpurge_" + suffix();
        superLoginId = "zzpsuper_" + suffix();
        adminLoginId = "zzpadmin_" + suffix();
        targetId = member(targetLoginId, "ZZ정리대상" + suffix(), Role.USER);
        member(superLoginId, "ZZ정리최상위" + suffix(), Role.SUPER_ADMIN);
        member(adminLoginId, "ZZ정리관리자" + suffix(), Role.ADMIN);
        productId = UUID.randomUUID(); // 상품 실체는 필요 없다 — 연결만 있으면 정리 여부가 드러난다
        variantId = UUID.randomUUID(); // 장바구니 필드 키. 실체가 없어도 «남았나» 는 드러난다.
        seedEverything();
    }

    /**
     * ⚠ <b>{@code @Transactional} 은 DB 만 롤백한다 — Redis 는 그대로 남는다</b>(WA §3, 업로드 파일과 같은 자리).
     * 이 테스트는 장바구니 키를 심고, 정리를 안 밟는 케이스(권한 테스트 등)도 있어서
     * <b>스스로 치우지 않으면 매 실행마다 잔재가 쌓인다</b> — 하필 이 파일이 «남는 것» 을 고치는 자리라
     * 더더욱 그렇다.
     * ⚠ 테스트는 {@code spring.data.redis.database=1} 로 <b>격리</b>돼 있어(build.gradle) 운영 db0 은
     * 안 건드린다. 격리가 먼저고 정리는 그다음이다 — 정리는 «내가 만든 것» 만 되돌리지만
     * 격리는 «남이 건드리는 것» 도 막는다(2026-07-29 조회수 사고의 결론).
     */
    @org.junit.jupiter.api.AfterEach
    void cleanUpRedis() {
        cartStore.clear(targetId);
        redis.delete("auth:revoked-before:" + targetId);
        redis.delete("auth:refresh:" + targetId);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    /**
     * 회원 id 를 들고 있는 <b>모든</b> 자리에 한 행씩 심는다(코드에서 전수로 뽑은 목록).
     *
     * <p>🔴 <b>2026-08-11 정정</b>: 이 주석이 «모든» 이라고 적혀 있었는데 <b>장바구니가 빠져 있었다</b>
     * (08-10 §16-4 6번). 나머지 일곱은 전부 DB 테이블인데 장바구니만 <b>Redis</b> 라,
     * 「지울 테이블」을 세면서 「지울 저장소」를 안 센 것이다 — <b>목록의 단위가 틀리면 항목이 빠진다.</b>
     * 그래서 아래 {@code assertNoRedisResidue} 로 <b>저장소 쪽을 통째로</b> 훑는다.
     */
    private void seedEverything() {
        // ⚠ 장바구니(Redis). 다른 것과 달리 리포지토리가 아니라 CartStore 라 여기서만 모양이 다르다 —
        //    그 «모양이 다름» 자체가 이 항목이 빠졌던 이유다.
        cartStore.set(targetId, variantId, 2);
        addressRepository.save(MemberAddress.of(targetId, "집", "ZZ수령인", "010-1234-5678",
                "06134", "서울시 강남구 테헤란로 1", "3층"));
        wishlistRepository.save(Wishlist.of(targetId, productId));
        pointAccountRepository.save(PointAccount.openFor(targetId));
        pointHistoryRepository.save(PointHistory.earned(targetId, 100, 100, null, "ZZ적립"));
        Coupon coupon = couponRepository.save(Coupon.builder()
                .name("ZZ정리쿠폰").discountType(DiscountType.FIXED).discountValue(1_000)
                .minOrderAmount(0).validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS)).build());
        memberCouponRepository.save(MemberCoupon.issue(targetId, coupon));
        notificationRepository.save(Notification.of(targetId, NotificationType.ORDER,
                "ZZ알림", "본문", "/orders"));
        notificationPrefRepository.save(NotificationPref.of(targetId, NotificationType.ORDER, false));
        restockRepository.save(RestockSubscription.of(targetId, productId));
        inquiryRepository.save(Inquiry.builder()
                .productId(productId).type(InquiryType.PRODUCT).authorId(targetId).author("ZZ정리대상")
                .title("ZZ문의").content("내용").secret(false).build());
        // ---- 남아야 하는 것들 ----
        reviewId = reviewRepository.save(Review.builder()
                .productId(productId).authorId(targetId).author("ZZ정리대상")
                .rating(5).content("ZZ리뷰 — 회원이 사라져도 남아야 한다").build()).getId();
        orderId = orderRepository.save(Order.create(targetId, "ZZ정리대상",
                List.of(OrderItem.of(UUID.randomUUID(), productId, null, "ZZ상품", null, 10_000, 10_000L, null, 1)),
                "ZZ수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                3_000, "20260101-" + suffix(), null, 0L, null, 0L)).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 심어 둔 것이 실제로 다 있는지 — 이게 없으면 "0건"이 정리 때문인지 애초에 없어서인지 모른다. */
    private void assertSeeded() {
        assertThat(cartStore.items(targetId)).hasSize(1);
        assertThat(addressRepository.countByMemberId(targetId)).isEqualTo(1);
        assertThat(pointAccountRepository.findByMemberId(targetId)).isPresent();
        assertThat(inquiryRepository.findByAuthorId(targetId)).hasSize(1);
    }

    /**
     * 🔴 <b>Redis 에 그 회원의 흔적이 남지 않는다</b> (2026-08-11, 08-10 §16-4 6번).
     *
     * <p>위 {@code assertMemberDataGone} 은 <b>손으로 적은 목록</b>이라, 새 저장소가 생기면
     * 조용히 뒤처진다 — 실제로 장바구니가 그렇게 빠졌다. 여기서는 <b>키를 훑어</b> 대조한다:
     * 회원 id 가 들어간 키가 남아 있으면 그게 무엇이든 걸린다.
     *
     * <p>⚠ <b>예외가 하나 있고, 그건 의도된 것이다</b>: {@code auth:revoked-before:{memberId}} 는
     * {@code purge} 가 <b>일부러 써 넣는</b> 값이다(E-2). 회원 행이 사라져도 남의 기기에 있던 access
     * 토큰은 만료까지 통하므로, 그 컷오프가 남아 있어야 끊긴다. 지우면 방어가 사라진다.
     * → 예외를 <b>이름으로 못 박는다.</b> 「남아도 되는 것」이 늘어나면 여기에 근거와 함께 적는다.
     */
    private void assertNoRedisResidue() {
        java.util.Set<String> leftovers = redis.keys("*" + targetId + "*");
        assertThat(leftovers).isNotNull();
        assertThat(leftovers.stream().filter(k -> !k.startsWith("auth:revoked-before:")).toList())
                .as("""
                        탈퇴한 회원의 Redis 키가 남았다 — 지우는 리스너가 없는 저장소가 있다는 뜻이다.
                        ⚠ 2026-08-11 에 장바구니(cart:{memberId})가 정확히 이 상태였다:
                           다른 일곱은 DB 테이블이라 정리 목록에 들었는데 Redis 만 빠져 있었다.
                        → 그 도메인에 *MemberWithdrawnListener 를 만든다.""")
                .isEmpty();
        // ⚠ 예외가 실제로 있는지도 확인한다 — 없으면 위 필터는 «아무것도 안 거른» 것이고,
        //    그러면 이 테스트가 무엇을 봐주고 있는지 알 수 없다(WA §3-3 의 「0」과 같은 자리).
        assertThat(leftovers)
                .as("purge 는 토큰 컷오프를 **써 넣어야** 한다(E-2) — 없으면 그 방어가 사라진 것이다")
                .anyMatch(k -> k.startsWith("auth:revoked-before:"));
    }

    private void assertMemberDataGone() {
        assertThat(cartStore.items(targetId)).isEmpty();
        assertNoRedisResidue();
        assertThat(addressRepository.countByMemberId(targetId)).isZero();
        assertThat(wishlistRepository.findProductIdsByMemberId(targetId)).isEmpty();
        assertThat(pointAccountRepository.findByMemberId(targetId)).isEmpty();
        assertThat(pointHistoryRepository.sumAmountByMemberId(targetId)).isZero();
        assertThat(memberCouponRepository.findUnusedByMember(targetId)).isEmpty();
        assertThat(notificationRepository.countByMemberIdAndReadFalse(targetId)).isZero();
        assertThat(notificationPrefRepository.findByMemberId(targetId)).isEmpty();
        assertThat(restockRepository.findProductIdsByMemberId(targetId)).isEmpty();
        assertThat(inquiryRepository.findByAuthorId(targetId)).isEmpty();
        assertThat(memberRepository.findById(targetId)).isEmpty();
    }

    /** ⚠ 정리가 너무 넓으면 이쪽이 깨진다 — 원래 문제보다 큰 사고다. */
    private void assertKeptSurvives() {
        assertThat(reviewRepository.findById(reviewId))
                .as("리뷰는 남는다 — 다른 고객의 판단 근거 + 별점 집계의 근거")
                .isPresent();
        assertThat(reviewRepository.findById(reviewId).orElseThrow().getAuthor())
                .as("작성자 표시는 스냅샷이라 회원이 없어도 읽힌다")
                .isEqualTo("ZZ정리대상");
        assertThat(orderRepository.findById(orderId))
                .as("주문은 남는다 — 매출 집계의 근거")
                .isPresent();
        assertThat(orderRepository.findById(orderId).orElseThrow().getBuyerNickname())
                .isEqualTo("ZZ정리대상");
    }

    // ---------- 본인 탈퇴 ----------

    @Test
    @DisplayName("본인 탈퇴: 회원 데이터는 전부 지워지고, 주문·리뷰는 남는다")
    void withdraw_purgesRelatedData() throws Exception {
        assertSeeded();

        mockMvc.perform(delete("/api/members/me").header("Authorization", login(targetLoginId)))
                .andExpect(status().isOk());

        assertMemberDataGone();
        assertKeptSurvives();
    }

    // ---------- 관리자 강제 삭제 (B-24) ----------

    @Test
    @DisplayName("강제 삭제 권한: 비로그인 401 / USER 403 / 일반 ADMIN 403(MEMBER-403A) / SUPER 200")
    void adminDelete_permission() throws Exception {
        mockMvc.perform(delete("/api/admin/members/" + targetId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/members/" + targetId)
                        .header("Authorization", login(targetLoginId)))
                .andExpect(status().isForbidden());
        // ⚠ 삭제는 되돌릴 수 없어 역할변경과 같은 급 — 대상이 USER 여도 일반 ADMIN 은 못 지운다
        mockMvc.perform(delete("/api/admin/members/" + targetId)
                        .header("Authorization", login(adminLoginId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER-403A"));
        mockMvc.perform(delete("/api/admin/members/" + targetId)
                        .header("Authorization", login(superLoginId)))
                .andExpect(status().isOk());
        assertThat(memberRepository.findById(targetId)).isEmpty();
    }

    @Test
    @DisplayName("강제 삭제는 본인 탈퇴와 같은 정리 경로를 탄다 + 감사 이력이 남는다")
    void adminDelete_purgesAndAudits() throws Exception {
        assertSeeded();

        mockMvc.perform(delete("/api/admin/members/" + targetId)
                        .header("Authorization", login(superLoginId)))
                .andExpect(status().isOk());

        assertMemberDataGone();
        assertKeptSurvives();

        // 감사 이력: 대상 loginId 스냅샷이 남아 **지워진 회원도 누가 지웠는지** 되짚을 수 있다
        var rows = auditLogRepository.search(AuditAction.MEMBER_DELETE, null, targetLoginId, PageRequest.of(0, 10))
                .getContent();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getTargetLogin()).isEqualTo(targetLoginId);
        assertThat(rows.getFirst().getActorName()).startsWith("ZZ정리최상위");
    }

    @Test
    @DisplayName("자기 자신은 지울 수 없다 → 400(MEMBER-400S, 락아웃 방지)")
    void adminDelete_self_rejected() throws Exception {
        String token = login(superLoginId);
        UUID superId = memberRepository.findByLoginId(superLoginId).orElseThrow().getId();
        mockMvc.perform(delete("/api/admin/members/" + superId).header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEMBER-400S"));
        assertThat(memberRepository.findById(superId)).isPresent();
    }

    @Test
    @DisplayName("삭제된 회원의 access 토큰은 즉시 죽는다 (E-2 와 같은 경로)")
    void adminDelete_killsTokens() throws Exception {
        String victimToken = login(targetLoginId);
        mockMvc.perform(get("/api/auth/me").header("Authorization", victimToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/members/" + targetId)
                        .header("Authorization", login(superLoginId)))
                .andExpect(status().isOk());

        // 회원 행이 사라졌으니 어차피 조회는 실패하지만, **토큰 자체가 무효**여야 한다
        // (컷오프가 없으면 필터는 통과하고 그 뒤 경로에서 500 이 날 수도 있다)
        mockMvc.perform(get("/api/auth/me").header("Authorization", victimToken))
                .andExpect(status().isUnauthorized());
    }
}

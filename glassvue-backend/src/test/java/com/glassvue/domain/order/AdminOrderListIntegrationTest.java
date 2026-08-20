package com.glassvue.domain.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.order.entity.DeliveryCarrier;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.repository.OrderRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 주문 목록(`GET /api/admin/orders`) — 권한 · 필터 · 스코프 검증.
 *
 * <p><b>권한 테스트를 반드시 두는 이유</b>: 2026-07-20에 이미지 업로드가 ADMIN 전용으로 남아 있어
 * 포토 리뷰가 통째로 막힌 사고가 있었다. 권한 규칙은 서비스 단위 테스트로는 절대 안 잡히고
 * 실제 요청을 보내야만 드러난다. 새 관리자 엔드포인트는 같은 실수가 재발하기 쉬운 자리다.
 *
 * <p>DB_HOST 있을 때만 실행(= .env 소싱). @Transactional 롤백 → 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminOrderListIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private static final String PW = "password123";
    private static final String MARK = "ZZADMLIST"; // 이 테스트가 넣은 주문만 걸러내는 표식

    private String adminLoginId;
    private String userLoginId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        adminLoginId = "zzadm_" + UUID.randomUUID().toString().substring(0, 8);
        userLoginId = "zzusr_" + UUID.randomUUID().toString().substring(0, 8);
        member(adminLoginId, MARK + "-관리자", Role.ADMIN);
        userId = member(userLoginId, MARK + "-구매자", Role.USER);

        // 상태가 다른 주문 3건 — 필터가 실제로 거르는지 보려면 상태가 섞여 있어야 한다.
        save(o -> {});                       // ORDERED
        save(Order::pay);                    // PAID
        save(o -> { o.pay(); o.ship(DeliveryCarrier.CJ, "123"); });   // SHIPPED
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    private void save(java.util.function.Consumer<Order> transition) {
        Order order = Order.create(userId, MARK + "-구매자",
                List.of(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, MARK + "-상품", null, 10_000, 10_000L, null, 1)), "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 3_000, uniqueOrderNo(), null, 0L, null, 0L);
        transition.accept(order);
        orderRepository.save(order);
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    // ---------- 권한 ----------

    @Test
    @DisplayName("비로그인 → 401")
    void anonymous_unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자(USER) → 403 (남의 주문을 볼 수 없다)")
    void user_forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/orders").header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 → 200, 구매자 닉네임이 내려온다")
    void admin_ok() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", login(adminLoginId))
                        .param("buyer", MARK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].buyerNickname").value(MARK + "-구매자"))
                .andExpect(jsonPath("$.data.content[0].summary").value(MARK + "-상품"));
    }

    // ---------- 상태별 건수 요약 ----------

    @Test
    @DisplayName("건수 요약: 비로그인 → 401 / USER → 403")
    void counts_permission() throws Exception {
        mockMvc.perform(get("/api/admin/orders/counts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/orders/counts").header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("건수 요약: 관리자 → 200, 모든 상태 키가 있고 이 테스트가 만든 주문이 반영된다")
    void counts_admin() throws Exception {
        mockMvc.perform(get("/api/admin/orders/counts").header("Authorization", login(adminLoginId)))
                .andExpect(status().isOk())
                // 건수가 0인 상태도 키는 항상 있어야 한다(탭이 사라지면 오히려 헷갈린다)
                .andExpect(jsonPath("$.data.ORDERED").exists())
                .andExpect(jsonPath("$.data.PAID").exists())
                .andExpect(jsonPath("$.data.SHIPPED").exists())
                .andExpect(jsonPath("$.data.CANCELLED").exists())
                // setUp이 ORDERED/PAID/SHIPPED를 한 건씩 넣었으므로 최소 1 이상
                .andExpect(jsonPath("$.data.PAID").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    // ---------- 필터 · 페이징 ----------

    @Test
    @DisplayName("상태 필터 — PAID만 (발송 대기 주문 찾기가 이 화면의 핵심 용도)")
    void filterByStatus() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", login(adminLoginId))
                        .param("buyer", MARK).param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("PAID"));
    }

    @Test
    @DisplayName("페이징 — size=2면 첫 페이지에 2건, 전체는 3건")
    void paging() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", login(adminLoginId))
                        .param("buyer", MARK).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    // ---------- 사용자용 목록의 스코프 ----------

    @Test
    @DisplayName("내 주문 목록은 본인 것만 — memberId 파라미터를 넘겨도 남의 주문을 못 본다")
    void myOrders_scopeCannotBeOverridden() throws Exception {
        // 구매자에겐 이 테스트가 만든 주문 3건이 보인다(대조군).
        mockMvc.perform(get("/api/orders").header("Authorization", login(userLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        // 관리자가 구매자의 memberId를 쿼리로 넘겨도 서버가 스코프를 본인으로 덮어쓴다.
        // 이 관리자는 주문한 적이 없으므로 0건이어야 한다 — 넘긴 값이 먹혔다면 3건이 나온다.
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", login(adminLoginId))
                        .param("memberId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /**
     * 테스트용 주문번호. {@code orders.order_no} 에 유니크 제약이 있어(V15)
     * 여러 건을 만드는 테스트가 같은 값을 쓰면 충돌한다 — 매번 다른 값을 준다.
     * (운영 채번은 시퀀스가 하지만 여기선 엔티티를 직접 만들어 저장하므로 서비스를 안 탄다.)
     */
    private static String uniqueOrderNo() {
        return "20260101-" + UUID.randomUUID().toString().substring(0, 8);
    }

}

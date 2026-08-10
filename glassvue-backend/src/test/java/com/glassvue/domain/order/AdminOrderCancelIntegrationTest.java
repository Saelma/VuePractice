package com.glassvue.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.repository.AdminAuditLogRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.order.entity.DeliveryCarrier;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.domain.order.repository.OrderRepository;
import com.glassvue.domain.point.entity.PointHistory;
import com.glassvue.domain.point.repository.PointHistoryRepository;
import com.glassvue.domain.point.service.PointService;
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
 * 관리자 대행 주문 취소(`POST /api/orders/{id}/admin-cancel`) — B-25, 2026-08-10.
 *
 * <p><b>무엇을 고정하는가</b>: 이 기능은 «본인 취소와 같아야 하는 것» 과 «달라야 하는 것» 이 섞여 있다.
 * 같아야 하는 쪽(재고 복원·적립금 환불·허용 상태)이 조용히 갈리는 것이 가장 비싼 실패라
 * — 2026-08-07 에 취소만 적립금을 안 돌려주던 것이 정확히 그 모양이었다 — 여기서 함께 고정한다.
 *
 * <p>DB_HOST 있을 때만 실행(= .env 소싱). {@code @Transactional} 롤백 → 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminOrderCancelIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PointHistoryRepository pointHistoryRepository;
    @Autowired PointService pointService;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private static final String PW = "password123";
    private static final String MARK = "ZZADMCANCEL";
    private static final String BODY = "{\"reason\":\"고객 요청 (CS 대행)\"}";

    private String adminLoginId;
    private String userLoginId;
    private String otherAdminLoginId;
    private UUID userId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        adminLoginId = "zzadm_" + UUID.randomUUID().toString().substring(0, 8);
        otherAdminLoginId = "zzad2_" + UUID.randomUUID().toString().substring(0, 8);
        userLoginId = "zzusr_" + UUID.randomUUID().toString().substring(0, 8);
        adminId = member(adminLoginId, MARK + "-관리자", Role.ADMIN);
        member(otherAdminLoginId, MARK + "-관리자2", Role.ADMIN);
        userId = member(userLoginId, MARK + "-구매자", Role.USER);
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    /** @param usedPoint 이 주문에 쓴 적립금. 0 이면 적립금을 안 쓴 주문. */
    private Order save(long usedPoint, java.util.function.Consumer<Order> transition) {
        Order order = Order.create(userId, MARK + "-구매자",
                List.of(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, MARK + "-상품", null, 10_000, null, 1)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 3_000,
                uniqueOrderNo(), null, 0L, usedPoint);
        transition.accept(order);
        return orderRepository.save(order);
    }

    private String uniqueOrderNo() {
        return "ZZ" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private org.springframework.test.web.servlet.ResultActions cancel(UUID orderId, String auth, String body)
            throws Exception {
        var req = post("/api/orders/" + orderId + "/admin-cancel").contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (auth != null) {
            req = req.header("Authorization", auth);
        }
        return mockMvc.perform(req);
    }

    // ---------- 권한 (WA §2-4) ----------

    @Test
    @DisplayName("비로그인 → 401")
    void anonymous_unauthorized() throws Exception {
        cancel(save(0, o -> {}).getId(), null, BODY).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("일반 사용자(USER) → 403 — 자기 주문이어도 이 경로는 못 쓴다")
    void user_forbidden() throws Exception {
        // ⚠ 주문자 본인이라는 점이 중요하다. 「남의 주문이라 막혔다」가 아니라 「경로 자체가 관리자용」임을
        //    보여야 한다 — 본인 취소는 /cancel 로 여전히 되고, 그쪽은 취소자 기록이 안 남는다.
        cancel(save(0, o -> {}).getId(), login(userLoginId), BODY).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 → 200, 상태가 CANCELLED 가 된다")
    void admin_ok() throws Exception {
        Order order = save(0, o -> {});
        cancel(order.getId(), login(adminLoginId), BODY).andExpect(status().isOk());
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    // ---------- 사유 (본인 취소와 다른 유일한 입력 규칙) ----------

    @Test
    @DisplayName("사유가 비면 400 — 관리자 취소는 사유가 필수다")
    void blank_reason_rejected() throws Exception {
        UUID id = save(0, o -> {}).getId();
        cancel(id, login(adminLoginId), "{\"reason\":\"   \"}").andExpect(status().isBadRequest());
        cancel(id, login(adminLoginId), "{}").andExpect(status().isBadRequest());
        // ⚠ 거절됐으면 주문은 그대로여야 한다 — 400 을 받고도 취소돼 있으면 최악이다.
        assertThat(orderRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OrderStatus.ORDERED);
    }

    // ---------- 취소자 기록 (이 기능이 새로 만드는 값) ----------

    @Test
    @DisplayName("취소자·사유가 주문에 남고, 관리자 목록으로 읽힌다")
    void records_actor() throws Exception {
        Order order = save(0, o -> {});
        cancel(order.getId(), login(adminLoginId), BODY).andExpect(status().isOk());

        Order after = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(after.getCancelledBy()).isEqualTo(adminId);
        assertThat(after.getCancelledByName()).isEqualTo(MARK + "-관리자");
        assertThat(after.getCancelReason()).isEqualTo("고객 요청 (CS 대행)");

        // 일반 ADMIN 이 «누가 취소했나» 를 볼 유일한 경로가 이 응답이다(감사는 SUPER 전용).
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", login(otherAdminLoginId))
                        .param("buyer", MARK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].cancelledByName").value(MARK + "-관리자"))
                .andExpect(jsonPath("$.data.content[0].cancelReason").value("고객 요청 (CS 대행)"));
    }

    @Test
    @DisplayName("본인 취소는 취소자가 NULL 로 남는다 — NULL 이 「본인」의 뜻이다")
    void self_cancel_leaves_actor_null() throws Exception {
        Order order = save(0, o -> {});
        mockMvc.perform(post("/api/orders/" + order.getId() + "/cancel")
                        .header("Authorization", login(userLoginId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"변심\"}"))
                .andExpect(status().isOk());

        Order after = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // ⚠ 이 단언이 없으면 「관리자 취소가 본인 취소에도 값을 흘리는」 회귀를 못 잡는다.
        assertThat(after.getCancelledBy()).isNull();
        assertThat(after.getCancelledByName()).isNull();
    }

    @Test
    @DisplayName("감사 원장에도 남는다 — 대상은 주문자, 주문번호는 detail 에")
    void writes_audit_log() throws Exception {
        Order order = save(0, o -> {});
        long before = auditLogRepository.count();
        cancel(order.getId(), login(adminLoginId), BODY).andExpect(status().isOk());

        assertThat(auditLogRepository.count()).isEqualTo(before + 1);
        var log = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.ORDER_CANCEL && adminId.equals(l.getActorId()))
                .findFirst().orElseThrow();
        assertThat(log.getTargetId()).isEqualTo(userId);
        assertThat(log.getTargetLogin()).isEqualTo(userLoginId);   // ⚠ 닉네임이 아니라 loginId 여야 한다
        assertThat(log.getDetail()).contains(order.getOrderNo()).contains("고객 요청");
    }

    // ---------- 본인 취소와 «같아야» 하는 것 ----------

    @Test
    @DisplayName("쓴 적립금을 돌려준다 — 본인 취소와 같은 경로를 탄다")
    void refunds_used_point() throws Exception {
        // ⚠ 잔액을 미리 심지 않는다 — 환불은 «있던 잔액에 더한다» 라 0 에서 시작해도 성립하고,
        //    심는 순간 그 적립 경로까지 이 테스트가 의존하게 된다(계정이 없으면 balanceOf 는 0 이다).
        long before = pointService.balanceOf(userId);

        Order order = save(500, Order::pay);
        cancel(order.getId(), login(adminLoginId), BODY).andExpect(status().isOk());

        assertThat(pointService.balanceOf(userId)).isEqualTo(before + 500);
        assertThat(pointHistoryRepository.findAll().stream()
                .filter(h -> order.getId().equals(h.getOrderId()))
                .map(PointHistory::getReason))
                .anyMatch(r -> r != null && r.startsWith("주문 취소 환불"));
    }

    @Test
    @DisplayName("적립금을 안 쓴 주문은 0원 이력을 안 남긴다")
    void no_zero_point_row() throws Exception {
        Order order = save(0, Order::pay);
        cancel(order.getId(), login(adminLoginId), BODY).andExpect(status().isOk());
        // ⚠ 「0행」이 근거가 되려면 경로가 돌았다는 양성 증거가 함께 있어야 한다(WA §3-3) —
        //    위 상태 단언이 그 역할을 한다.
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(pointHistoryRepository.findAll().stream()
                .filter(h -> order.getId().equals(h.getOrderId())).toList()).isEmpty();
    }

    @Test
    @DisplayName("발송된 주문은 관리자도 못 취소한다(ORDER-400C) — 허용 상태·에러코드가 본인과 같다")
    void shipped_not_cancellable() throws Exception {
        Order order = save(0, o -> { o.pay(); o.ship(DeliveryCarrier.CJ, "123"); });
        // ⚠ 새 에러코드를 만들지 않았다 — 막히는 이유가 «발송됐다» 로 본인 취소와 완전히 같다.
        //   갈라 두면 화면이 같은 상황에 다른 문구를 두 벌 갖게 된다.
        cancel(order.getId(), login(adminLoginId), BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORDER-400C"));
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("없는 주문 → 404")
    void unknown_order() throws Exception {
        cancel(UUID.randomUUID(), login(adminLoginId), BODY).andExpect(status().isNotFound());
    }

    /**
     * 🔴 <b>탈퇴 회원의 주문도 취소할 수 있어야 한다</b> (2026-08-10 발견, V45).
     *
     * <p>F-1 은 탈퇴 시 <b>주문을 남긴다</b>(매출). 그래서 {@code memberId} 가 <b>이미 없는 회원</b>을
     * 가리키는 주문이 운영에 존재한다. 감사가 대상 loginId 를 조회하는데 {@code MEMBER_NOT_FOUND} 를
     * 던지면 <b>조작 자체가 404</b> 로 막혔다 — <b>정확히 그 조작이 필요한 상황</b>인데.
     *
     * <p>⚠ 이 결함은 <b>B-25 를 배포한 뒤에</b> 리뷰 숨김 쪽에서 드러났다. 여기(주문)에는 그때
     * 테스트가 없어서 <b>같은 구멍이 조용히 나가 있었다</b> — 그래서 되찾아 와 고정한다.
     * ⚠ 감사 원장에는 {@code targetLogin} 이 <b>null</b> 로 남는다. 지어내지 않는 것이 규칙이다.
     */
    @Test
    @DisplayName("🔴 탈퇴 회원의 주문도 관리자가 취소할 수 있다 — 감사 targetLogin 은 null")
    void cancels_order_of_withdrawn_member() throws Exception {
        // 주문만 남기고 회원은 없는 상태 = 탈퇴 후의 주문(F-1)
        UUID goneMemberId = UUID.randomUUID();
        Order order = orderRepository.save(Order.create(goneMemberId, MARK + "-탈퇴자",
                List.of(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, MARK + "-상품", null, 10_000, null, 1)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 3_000,
                uniqueOrderNo(), null, 0L, 0L));

        cancel(order.getId(), login(adminLoginId), BODY).andExpect(status().isOk());

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
        var log = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.ORDER_CANCEL && goneMemberId.equals(l.getTargetId()))
                .findFirst().orElseThrow();
        assertThat(log.getTargetLogin()).isNull();   // ⚠ 「없다」를 지어내지 않는다
        assertThat(log.getDetail()).contains(order.getOrderNo());
    }
}

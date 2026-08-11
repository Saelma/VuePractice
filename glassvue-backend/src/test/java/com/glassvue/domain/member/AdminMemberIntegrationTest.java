package com.glassvue.domain.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 회원 관리(B-11) — 권한 + 조회. member/order/point 세 도메인의 admin 엔드포인트를 다 건드린다:
 * {@code /api/admin/members}, {@code /api/admin/orders/by-member/*}, {@code /api/admin/points/*}.
 *
 * <p>권한 테스트를 반드시 두는 이유는 AdminOrderListIntegrationTest 주석 참조(권한 사고는 실제 요청으로만 드러난다).
 * DB_HOST 있을 때만 실행, @Transactional 롤백으로 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMemberIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PW = "password123";
    private static final String MARK = "ZZMEMADM"; // 이 테스트가 넣은 회원만 걸러내는 표식

    private String adminLoginId;
    private String userLoginId;
    private String superLoginId;
    private UUID adminId;
    private UUID userId;
    private UUID superId;

    @BeforeEach
    void setUp() {
        adminLoginId = "zzadm_" + UUID.randomUUID().toString().substring(0, 8);
        userLoginId = MARK.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
        superLoginId = "zzsuper_" + UUID.randomUUID().toString().substring(0, 8);
        adminId = member(adminLoginId, "ZZ회원관리자" + suffix(), Role.ADMIN);
        userId = member(userLoginId, MARK + "-대상회원", Role.USER);
        superId = member(superLoginId, "ZZ최상위" + suffix(), Role.SUPER_ADMIN);
        // 대상 회원의 주문 2건(by-member 조회가 그 회원 것만 집는지 확인)
        saveOrder();
        saveOrder();
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    private void saveOrder() {
        Order order = Order.create(userId, MARK + "-대상회원",
                List.of(OrderItem.of(UUID.randomUUID(), UUID.randomUUID(), null, MARK + "-상품", null, 10_000, null, 1)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null,
                3_000, "20260101-" + UUID.randomUUID().toString().substring(0, 8), null, 0L, null, 0L);
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
    @DisplayName("회원 목록: 비로그인 → 401, USER → 403")
    void list_permission() throws Exception {
        mockMvc.perform(get("/api/admin/members")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/members").header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("회원 목록: 관리자 → 200, keyword 로 대상 회원을 찾는다")
    void list_admin_search() throws Exception {
        mockMvc.perform(get("/api/admin/members")
                        .header("Authorization", login(adminLoginId))
                        .param("keyword", userLoginId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].loginId").value(userLoginId))
                .andExpect(jsonPath("$.data.content[0].nickname").value(MARK + "-대상회원"));
    }

    @Test
    @DisplayName("회원 상세: 관리자 → 200 기본정보 / 없는 id → 404")
    void detail_admin() throws Exception {
        String admin = login(adminLoginId);
        mockMvc.perform(get("/api/admin/members/" + userId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value(userLoginId))
                .andExpect(jsonPath("$.data.role").value("USER"));
        mockMvc.perform(get("/api/admin/members/" + UUID.randomUUID()).header("Authorization", admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMBER-404"));
    }

    // ---------- 회원별 주문(order 도메인 admin) ----------

    @Test
    @DisplayName("회원별 주문: USER → 403, 관리자 → 그 회원 주문 2건")
    void ordersByMember() throws Exception {
        mockMvc.perform(get("/api/admin/orders/by-member/" + userId)
                        .header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders/by-member/" + userId)
                        .header("Authorization", login(adminLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].buyerNickname").value(MARK + "-대상회원"));
    }

    // ---------- 회원별 적립금(point 도메인 admin) ----------

    @Test
    @DisplayName("회원별 적립금: USER → 403, 관리자 → 200(기본 등급/잔액)")
    void pointAccountByMember() throws Exception {
        mockMvc.perform(get("/api/admin/points/" + userId + "/account")
                        .header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/points/" + userId + "/account")
                        .header("Authorization", login(adminLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").exists())
                .andExpect(jsonPath("$.data.balance").value(0));
        mockMvc.perform(get("/api/admin/points/" + userId + "/history")
                        .header("Authorization", login(adminLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ---------- 정지 / 역할 변경 (B-11 후속) ----------

    @Test
    @DisplayName("정지: USER → 403 / 관리자 → 200 정지됨 → 정지된 계정 로그인 403(AUTH-403S) → 해제 후 로그인 복구")
    void suspend_blocksLogin_thenUnsuspend() throws Exception {
        // USER는 남을 정지 못 함
        mockMvc.perform(post("/api/admin/members/" + userId + "/suspend")
                        .header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
        // 관리자가 정지
        mockMvc.perform(post("/api/admin/members/" + userId + "/suspend")
                        .header("Authorization", login(adminLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspended").value(true));
        // 정지된 계정은 비번이 맞아도 로그인 불가
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + userLoginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH-403S"));
        // 해제하면 다시 로그인 가능
        mockMvc.perform(post("/api/admin/members/" + userId + "/unsuspend")
                        .header("Authorization", login(adminLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspended").value(false));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + userLoginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("자기 자신 정지 → 400 (MEMBER-400S, 락아웃 방지)")
    void suspend_self_rejected() throws Exception {
        mockMvc.perform(post("/api/admin/members/" + adminId + "/suspend")
                        .header("Authorization", login(adminLoginId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEMBER-400S"));
    }

    @Test
    @DisplayName("역할 변경은 SUPER_ADMIN 전용: 일반관리자 → 403(MEMBER-403A) / SUPER → 200 / SUPER 자기자신 → 400")
    void changeRole_superOnly() throws Exception {
        // 일반 관리자는 역할 변경 불가
        mockMvc.perform(patch("/api/admin/members/" + userId + "/role")
                        .header("Authorization", login(adminLoginId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER-403A"));
        // 최상위 관리자는 USER→ADMIN 가능
        mockMvc.perform(patch("/api/admin/members/" + userId + "/role")
                        .header("Authorization", login(superLoginId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
        // 자기 자신은 SUPER 여도 불가
        mockMvc.perform(patch("/api/admin/members/" + superId + "/role")
                        .header("Authorization", login(superLoginId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEMBER-400S"));
    }

    @Test
    @DisplayName("SUPER_ADMIN 부여는 API로 불가 → 400(MEMBER-400A)")
    void changeRole_grantSuper_rejected() throws Exception {
        mockMvc.perform(patch("/api/admin/members/" + userId + "/role")
                        .header("Authorization", login(superLoginId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEMBER-400A"));
    }

    @Test
    @DisplayName("관리자 정지는 SUPER_ADMIN 전용: 일반관리자가 ADMIN 정지 → 403(MEMBER-403A) / SUPER → 200")
    void suspend_admin_superOnly() throws Exception {
        UUID targetAdmin = member("zzadm2_" + UUID.randomUUID().toString().substring(0, 8), "ZZ대상관리자" + suffix(), Role.ADMIN);
        mockMvc.perform(post("/api/admin/members/" + targetAdmin + "/suspend")
                        .header("Authorization", login(adminLoginId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER-403A"));
        mockMvc.perform(post("/api/admin/members/" + targetAdmin + "/suspend")
                        .header("Authorization", login(superLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspended").value(true));
    }

    @Test
    @DisplayName("SUPER_ADMIN 대상은 아무도 못 건드림 → 403(MEMBER-403S)")
    void suspend_superTarget_forbidden() throws Exception {
        // 일반 관리자든 다른 최상위든 SUPER 대상은 불가(여기선 일반관리자가 시도)
        mockMvc.perform(post("/api/admin/members/" + superId + "/suspend")
                        .header("Authorization", login(adminLoginId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER-403S"));
    }
}

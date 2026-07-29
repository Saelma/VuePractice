package com.glassvue.domain.coupon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 생성 → 발급 → 내 목록 조회 관통 + 권한.
 *
 * <p>권한 규칙은 서비스 단위 테스트로 절대 안 잡히고 실제 요청을 보내야만 드러난다(§2-4).
 * 특히 {@code /api/coupons/**} 는 SecurityConfig 의 기본이 {@code permitAll} 이라
 * 매처를 빠뜨리면 <b>남의 쿠폰까지 조용히 열린다</b> — 그래서 401 을 계약으로 고정한다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CouponFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String userLoginId;
    private String adminLoginId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userLoginId = "cuser_" + UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "cadmin_" + UUID.randomUUID().toString().substring(0, 8);
        userId = member(userLoginId, "ZZ쿠폰유저", Role.USER);
        member(adminLoginId, "ZZ쿠폰관리자", Role.ADMIN);
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW)).nickname(nickname).role(role).build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    @DisplayName("쿠폰 생성(관리자) → 발급 → 내 목록에 보이고 할인 미리보기가 계산된다")
    void createIssueAndList() throws Exception {
        String admin = login(adminLoginId);
        String user = login(userLoginId);

        String body = mockMvc.perform(post("/api/admin/coupons").header("Authorization", admin)
                        .contentType(JSON)
                        .content("{\"name\":\"ZZ 5천원\",\"discountType\":\"FIXED\",\"discountValue\":5000,"
                               + "\"minOrderAmount\":30000,"
                               + "\"validFrom\":\"2026-01-01T00:00:00Z\",\"validUntil\":\"2027-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String couponId = JsonPath.read(body, "$.data");

        mockMvc.perform(post("/api/admin/coupons/" + couponId + "/issue?memberId=" + userId)
                        .header("Authorization", admin))
                .andExpect(status().isOk());

        // 최소 주문금액(30,000)을 채운 경우 — 사용 가능 + 할인액 미리보기
        mockMvc.perform(get("/api/coupons/me?itemsTotal=30000").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("ZZ 5천원"))
                .andExpect(jsonPath("$.data[0].usable").value(true))
                .andExpect(jsonPath("$.data[0].discountPreview").value(5000));

        // 못 채운 경우 — 쓸 수 없고 이유가 함께 온다(화면이 규칙을 몰라도 되게)
        mockMvc.perform(get("/api/coupons/me?itemsTotal=29999").header("Authorization", user))
                .andExpect(jsonPath("$.data[0].usable").value(false))
                .andExpect(jsonPath("$.data[0].discountPreview").value(0))
                .andExpect(jsonPath("$.data[0].reason").isNotEmpty());
    }

    @Test
    @DisplayName("권한 — 내 쿠폰은 비로그인 401 / 쿠폰 생성·발급은 일반 사용자 403")
    void permissions() throws Exception {
        String user = login(userLoginId);

        // ⚠ SecurityConfig 기본이 permitAll이라 매처가 빠지면 여기서 200이 난다.
        mockMvc.perform(get("/api/coupons/me")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/coupons").header("Authorization", user)
                        .contentType(JSON).content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/coupons/" + UUID.randomUUID() + "/issue?memberId=" + userId)
                        .header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    /**
     * 쿠폰 정의 목록(관리자 쿠폰 관리 화면) — 401/403/200.
     *
     * <p>⚠ 생성·발급(위 {@code permissions})만 덮고 <b>목록(GET)이 빠져 있었다</b>(2026-07-28 → 07-29 이월).
     * {@code /api/admin/**} 블랭킷 규칙으로 보호되기는 하나 그게 테스트 면제 사유는 아니다(§2-4) —
     * 규칙이 나중에 좁혀지거나 이 경로가 밖으로 나가면 <b>쿠폰 정의가 통째로 열린다</b>.
     */
    @Test
    @DisplayName("쿠폰 목록(관리자) — 비로그인 401 / USER 403 / ADMIN 200 + 방금 만든 쿠폰이 최신순 첫 줄")
    void adminList_permissionAndContent() throws Exception {
        mockMvc.perform(get("/api/admin/coupons")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/coupons").header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());

        String admin = login(adminLoginId);
        String name = "ZZ목록확인 " + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/admin/coupons").header("Authorization", admin)
                        .contentType(JSON)
                        .content("{\"name\":\"" + name + "\",\"discountType\":\"PERCENT\",\"discountValue\":10,"
                               + "\"minOrderAmount\":10000,\"maxDiscountAmount\":3000,"
                               + "\"validFrom\":\"2026-01-01T00:00:00Z\",\"validUntil\":\"2027-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk());

        // 200 을 상태코드로만 보지 않고 "정의가 실제로 실려 오는지" 까지 본다(기본 정렬 createdAt DESC).
        mockMvc.perform(get("/api/admin/coupons").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value(name))
                .andExpect(jsonPath("$.data.content[0].discountType").value("PERCENT"))
                .andExpect(jsonPath("$.data.content[0].discountValue").value(10))
                .andExpect(jsonPath("$.data.content[0].maxDiscountAmount").value(3000));
    }
}

package com.glassvue.domain.coupon;

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
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * 쿠폰 발급분 회수 · 정의 삭제 (2026-09-17, BACKLOG Q-7).
 *
 * <p>🔴 지키는 것: ①<b>쓰인 발급분은 안 지워진다</b>(주문이 가리킨다, V46) ②회수·삭제는 <b>되돌릴 수 없어</b>
 * 감사 한 줄이 유일한 흔적이다 ③정의는 발급분이 0 일 때만, 가입 쿠폰이면 지정부터 풀게 한다.
 * ⚠ 매번 <b>대조군</b>을 둔다 — 거부만 보면 «전부 거절» 과 구별이 안 된다(WA §2-4-2).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CouponRevokeIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CouponRepository couponRepository;
    @Autowired MemberCouponRepository memberCouponRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;

    private static final String PW = "password123";

    private String admin;
    private String user;
    private UUID adminId;
    private UUID userId;
    private String userLoginId;
    private UUID couponId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userLoginId = "rvuser_" + suffix;
        String adminLoginId = "rvadmin_" + suffix;
        userId = member(userLoginId, "ZZ회수유저", Role.USER);
        adminId = member(adminLoginId, "ZZ회수관리자", Role.ADMIN);
        admin = login(adminLoginId);
        user = login(userLoginId);
        couponId = couponRepository.save(Coupon.builder()
                .name("ZZ-회수시험").discountType(DiscountType.FIXED).discountValue(3000).minOrderAmount(0)
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validUntil(Instant.now().plus(30, ChronoUnit.DAYS)).build()).getId();
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW)).nickname(nickname).role(role).build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private UUID issue(UUID memberId) {
        return memberCouponRepository.saveAndFlush(
                MemberCoupon.issue(memberId, couponRepository.findById(couponId).orElseThrow())).getId();
    }

    private UUID issueUsed(UUID memberId) {
        MemberCoupon mc = MemberCoupon.issue(memberId, couponRepository.findById(couponId).orElseThrow());
        mc.use();
        return memberCouponRepository.saveAndFlush(mc).getId();
    }

    // ── 보유자 목록 ──────────────────────────────

    @Test
    @DisplayName("보유자 목록은 쓰인 것까지 loginId 와 함께 준다")
    void issuedListsUsedToo() throws Exception {
        issue(userId);
        issueUsed(adminId);

        mockMvc.perform(get("/api/admin/coupons/{id}/issued", couponId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.memberId=='" + userId + "')].loginId").value(userLoginId))
                .andExpect(jsonPath("$.data[?(@.memberId=='" + userId + "')].usedAt").value((Object) null))
                .andExpect(jsonPath("$.data[?(@.memberId=='" + adminId + "')].usedAt").isNotEmpty());
    }

    // ── 회수 ──────────────────────────────────

    @Test
    @DisplayName("미사용 발급분을 회수하면 행이 사라지고 고객 쿠폰함에서도 빠지며, 감사에 회원 대상으로 한 줄 남는다")
    void revokeUnused() throws Exception {
        UUID mcId = issue(userId);
        long before = auditLogRepository.count();

        mockMvc.perform(delete("/api/admin/coupons/{c}/issued/{mc}", couponId, mcId).header("Authorization", admin))
                .andExpect(status().isOk());

        assertThat(memberCouponRepository.existsById(mcId)).isFalse();
        mockMvc.perform(get("/api/coupons/me").header("Authorization", user))
                .andExpect(jsonPath("$.data[?(@.id=='" + mcId + "')]").isEmpty());

        assertThat(auditLogRepository.count()).isEqualTo(before + 1);
        var log = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.COUPON_REVOKE && adminId.equals(l.getActorId()))
                .findFirst().orElseThrow();
        assertThat(log.getTargetId()).isEqualTo(userId);
        assertThat(log.getTargetLogin()).isEqualTo(userLoginId);
        assertThat(log.getDetail()).isEqualTo("ZZ-회수시험 · 3000원");
    }

    @Test
    @DisplayName("🔴 쓰인 발급분은 409 로 거절되고 행이 남으며 감사도 안 남는다")
    void usedIsNotRevoked() throws Exception {
        UUID used = issueUsed(userId);
        long before = auditLogRepository.count();

        mockMvc.perform(delete("/api/admin/coupons/{c}/issued/{mc}", couponId, used).header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COUPON-409U"));

        assertThat(memberCouponRepository.existsById(used)).isTrue();
        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("경로의 쿠폰과 발급분의 쿠폰이 다르면 404 — 다른 쿠폰의 발급분을 지우지 않는다")
    void revokeRejectsMismatchedCoupon() throws Exception {
        UUID mcId = issue(userId);
        UUID other = couponRepository.save(Coupon.builder()
                .name("ZZ-다른쿠폰").discountType(DiscountType.FIXED).discountValue(1000).minOrderAmount(0)
                .validFrom(Instant.now()).validUntil(Instant.now().plus(1, ChronoUnit.DAYS)).build()).getId();

        mockMvc.perform(delete("/api/admin/coupons/{c}/issued/{mc}", other, mcId).header("Authorization", admin))
                .andExpect(status().isNotFound());
        assertThat(memberCouponRepository.existsById(mcId)).isTrue();
    }

    @Test
    @DisplayName("회수한 회원에게는 같은 쿠폰을 다시 발급할 수 있다 — 행을 지우는 방식의 약속")
    void reissueAfterRevoke() throws Exception {
        UUID mcId = issue(userId);
        mockMvc.perform(delete("/api/admin/coupons/{c}/issued/{mc}", couponId, mcId).header("Authorization", admin))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/coupons/{c}/issue", couponId).param("memberId", userId.toString())
                        .header("Authorization", admin))
                .andExpect(status().isOk());
    }

    // ── 정의 삭제 ──────────────────────────────

    @Test
    @DisplayName("발급분이 남아 있으면 삭제 409, 미사용을 회수한 뒤에는 지워지고 감사에 쿠폰 대상으로 남는다")
    void deleteNeedsNoIssued() throws Exception {
        UUID mcId = issue(userId);

        mockMvc.perform(delete("/api/admin/coupons/{c}", couponId).header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COUPON-409D"));
        assertThat(couponRepository.existsById(couponId)).isTrue();

        mockMvc.perform(delete("/api/admin/coupons/{c}/issued/{mc}", couponId, mcId).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/coupons/{c}", couponId).header("Authorization", admin))
                .andExpect(status().isOk());

        assertThat(couponRepository.existsById(couponId)).isFalse();
        var log = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.COUPON_DELETE && couponId.equals(l.getTargetId()))
                .findFirst().orElseThrow();
        assertThat(log.getActorId()).isEqualTo(adminId);
        assertThat(log.getDetail()).isEqualTo("ZZ-회수시험 · 3000원");
    }

    @Test
    @DisplayName("🔴 쓰인 발급분이 있으면 정의는 영영 못 지운다")
    void usedBlocksDeleteForever() throws Exception {
        issueUsed(userId);

        mockMvc.perform(delete("/api/admin/coupons/{c}", couponId).header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COUPON-409D"));
        assertThat(couponRepository.existsById(couponId)).isTrue();
    }

    @Test
    @DisplayName("가입 쿠폰으로 지정된 쿠폰은 발급분이 없어도 409W — 지정부터 풀어야 지워진다")
    void welcomeMustBeClearedFirst() throws Exception {
        mockMvc.perform(post("/api/admin/coupons/{c}/welcome", couponId).header("Authorization", admin))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/coupons/{c}", couponId).header("Authorization", admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COUPON-409W"));

        mockMvc.perform(delete("/api/admin/coupons/{c}/welcome", couponId).header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/coupons/{c}", couponId).header("Authorization", admin))
                .andExpect(status().isOk());
    }

    // ── 권한 ──────────────────────────────────

    @Test
    @DisplayName("일반 회원은 보유자 조회·회수·삭제 전부 403, 비로그인은 401 — 그리고 행은 그대로다")
    void adminOnly() throws Exception {
        UUID mcId = issue(userId);

        mockMvc.perform(get("/api/admin/coupons/{c}/issued", couponId).header("Authorization", user))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/coupons/{c}/issued/{mc}", couponId, mcId).header("Authorization", user))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/coupons/{c}", couponId).header("Authorization", user))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/coupons/{c}/issued/{mc}", couponId, mcId))
                .andExpect(status().isUnauthorized());

        assertThat(memberCouponRepository.existsById(mcId)).isTrue();
        assertThat(couponRepository.existsById(couponId)).isTrue();
    }
}

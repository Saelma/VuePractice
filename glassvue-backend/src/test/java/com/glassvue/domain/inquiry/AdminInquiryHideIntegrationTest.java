package com.glassvue.domain.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.repository.AdminAuditLogRepository;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryType;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
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
 * 관리자 문의 숨김 (2026-08-10, 백로그 B-18 잔여).
 *
 * <p>🔴 <b>리뷰 숨김과 「닿는 자리」가 다르다.</b> 리뷰는 셋(목록·별점 집계·1회 제한) 중
 * <b>하나만 반대</b>였는데, 문의는 집계도 개수 제한도 없어 <b>관리자 목록 하나만 반대</b>다:
 * <ol>
 *   <li><b>상품 문의 목록</b> — 빠진다.</li>
 *   <li><b>내 문의</b> — 빠진다. ⚠ <b>작성자 본인에게도</b>(2026-08-10 결정, 리뷰와 같은 규칙).</li>
 *   <li>🔴 <b>관리자 목록</b> — <b>남는다.</b> 안 남으면 <b>숨긴 것을 되돌릴 방법이 없다.</b></li>
 * </ol>
 *
 * <p>문의는 <b>리포지토리로 직접</b> 만든다 — API 로 만들어도 되지만 여기서 볼 것은 숨김이라
 * 작성 경로의 검증(상품 존재·유형 정합)을 통째로 태울 이유가 없다(리뷰 쪽과 같은 판단).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminInquiryHideIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired InquiryRepository inquiryRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired EntityManager entityManager;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String MARK = "ZZINQHIDE";

    private String admin;
    private String user;
    private UUID adminId;
    private UUID authorId;
    private String authorLoginId;
    private UUID productId;
    private UUID inquiryId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminLoginId = "iqadm_" + suffix;
        authorLoginId = "iqusr_" + suffix;
        adminId = member(adminLoginId, MARK + "-관리자" + suffix, Role.ADMIN);
        authorId = member(authorLoginId, MARK + "-작성자" + suffix, Role.USER);
        admin = login(adminLoginId);
        user = login(authorLoginId);

        Category cat = categoryRepository.save(Category.builder().name("ZZC-문의" + suffix).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-문의상품" + suffix).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();

        inquiryId = inquiry("숨길 문의");
        inquiry("남을 문의");
        entityManager.flush();
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    private UUID inquiry(String title) {
        return inquiryRepository.save(Inquiry.builder()
                .productId(productId).type(InquiryType.PRODUCT)
                .authorId(authorId).author(MARK + "-작성자")
                .title(title).content("내용").secret(false).imageGroupId(null)
                .build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private void hide(UUID id) throws Exception {
        mockMvc.perform(post("/api/admin/inquiries/" + id + "/hide").header("Authorization", admin))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
    }

    // ── 권한 (WA §2-4) ──────────────────────────────────

    @Test
    @DisplayName("비로그인 → 401 / 일반 사용자 → 403 (작성자 본인이어도)")
    void permission() throws Exception {
        mockMvc.perform(post("/api/admin/inquiries/" + inquiryId + "/hide"))
                .andExpect(status().isUnauthorized());
        // ⚠ 작성자 본인이라는 점이 중요하다 — 「남의 것이라 막혔다」가 아니라 「관리자 전용 경로」임을 본다.
        mockMvc.perform(post("/api/admin/inquiries/" + inquiryId + "/hide").header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    // ── 규칙 ① 상품 문의 목록에서 빠진다 ──────────────────────────────────

    @Test
    @DisplayName("숨기면 상품 문의 목록에서 빠진다 — 총건수도 함께 줄어든다")
    void hidden_removedFromProductList() throws Exception {
        mockMvc.perform(get("/api/products/" + productId + "/inquiries").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        hide(inquiryId);

        // ⚠ 목록 길이만 보면 안 된다 — 카운트 쿼리가 조건에서 빠지는 사고가 실제로 있었다(8/06 §4-5).
        mockMvc.perform(get("/api/products/" + productId + "/inquiries").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("남을 문의"));
    }

    // ── 규칙 ② 내 문의에서도 빠진다 (작성자 본인에게도) ──────────────────────────────────

    @Test
    @DisplayName("⚠ 숨긴 문의는 **작성자 본인의 「내 문의」에서도** 빠진다 (2026-08-10 결정)")
    void hidden_removedFromMyList() throws Exception {
        mockMvc.perform(get("/api/inquiries/me").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        hide(inquiryId);

        mockMvc.perform(get("/api/inquiries/me").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("남을 문의"));
    }

    // ── 규칙 ③ 관리자 목록에는 남는다 (여기만 반대) ──────────────────────────────────

    @Test
    @DisplayName("🔴 관리자 목록에는 **숨긴 것도 함께** 보인다 — 안 보이면 되돌릴 수가 없다")
    void adminList_keepsHidden() throws Exception {
        hide(inquiryId);

        mockMvc.perform(get("/api/admin/inquiries").header("Authorization", admin)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.title == '숨길 문의')].hidden").value(true));
    }

    @Test
    @DisplayName("⚠ `hidden` 은 **세 가지 상태**다 — 안 보내면 전체, false 면 보이는 것만")
    void adminList_hiddenFilterHasThreeStates() throws Exception {
        hide(inquiryId);

        // false → 숨긴 것은 안 나온다
        mockMvc.perform(get("/api/admin/inquiries").header("Authorization", admin)
                        .param("hidden", "false").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.title == '숨길 문의')]").isEmpty())
                .andExpect(jsonPath("$.data.content[?(@.title == '남을 문의')]").isNotEmpty());

        // true → 숨긴 것만 나온다
        mockMvc.perform(get("/api/admin/inquiries").header("Authorization", admin)
                        .param("hidden", "true").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.title == '숨길 문의')]").isNotEmpty())
                .andExpect(jsonPath("$.data.content[?(@.title == '남을 문의')]").isEmpty());
    }

    // ── 되돌릴 수 있다 · 멱등 ──────────────────────────────────

    @Test
    @DisplayName("숨김을 해제하면 다시 보인다 — 삭제가 아니라 숨김인 이유")
    void unhide_restores() throws Exception {
        hide(inquiryId);
        mockMvc.perform(post("/api/admin/inquiries/" + inquiryId + "/unhide").header("Authorization", admin))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/products/" + productId + "/inquiries").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("이미 숨긴 문의를 또 숨겨도 200 이고 **감사는 안 늘어난다** (일어나지 않은 조작을 적지 않는다)")
    void repeatedHide_isIdempotentAndDoesNotAudit() throws Exception {
        hide(inquiryId);
        long after1st = auditLogRepository.count();

        hide(inquiryId); // 두 번째 — 상태는 그대로다

        assertThat(auditLogRepository.count()).isEqualTo(after1st);
        assertThat(inquiryRepository.findById(inquiryId).orElseThrow().isHidden()).isTrue();
    }

    // ── 감사 ──────────────────────────────────

    @Test
    @DisplayName("감사 원장에 남는다 — 대상은 **작성자**, 제목은 detail 에")
    void writesAuditLog() throws Exception {
        long before = auditLogRepository.count();
        hide(inquiryId);

        assertThat(auditLogRepository.count()).isEqualTo(before + 1);
        var log = auditLogRepository.findAll().stream()
                .filter(l -> l.getAction() == AuditAction.INQUIRY_HIDE && adminId.equals(l.getActorId()))
                .findFirst().orElseThrow();
        assertThat(log.getTargetId()).isEqualTo(authorId);
        assertThat(log.getTargetLogin()).isEqualTo(authorLoginId);  // ⚠ 닉네임이 아니라 loginId
        assertThat(log.getDetail()).isEqualTo("숨길 문의");
    }

    @Test
    @DisplayName("해제도 감사에 남는다 — 숨김만 남기면 「지금 왜 보이나」를 되짚을 수 없다")
    void unhideIsAudited() throws Exception {
        hide(inquiryId);
        mockMvc.perform(post("/api/admin/inquiries/" + inquiryId + "/unhide").header("Authorization", admin))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(l -> l.getAction() == AuditAction.INQUIRY_UNHIDE && adminId.equals(l.getActorId())))
                .isTrue();
    }

    @Test
    @DisplayName("없는 문의를 숨기면 404")
    void unknownInquiry() throws Exception {
        mockMvc.perform(post("/api/admin/inquiries/" + UUID.randomUUID() + "/hide")
                        .header("Authorization", admin))
                .andExpect(status().isNotFound());
    }
}

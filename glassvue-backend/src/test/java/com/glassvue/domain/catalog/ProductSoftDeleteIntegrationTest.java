package com.glassvue.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.List;
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
 * 상품 삭제 유예 (2026-08-12, BACKLOG F-7).
 *
 * <p>🔴 <b>이 기능의 위험은 전부 «조회 갈래 하나가 빠지는 것» 에 있다.</b> 컬럼을 더하는 일은 쉽고,
 * 어려운 것은 <b>상품을 읽는 자리가 여럿</b>이라는 사실이다 — 한 곳이 {@code deleted_at is null} 을
 * 빠뜨리면 <b>이미 지운 상품이 그 화면에서만 계속 팔린다.</b> 그리고 화면은 멀쩡해 보인다.
 * ⚠ 2026-08-11 §13 ⓪ 이 「손으로 적은 목록은 어긋난다」를 하루에 일곱 번 보여준 그 성질이다.
 *
 * <p>그래서 <b>갈래마다 테스트를 따로</b> 둔다. 그리고 <b>전부 «빼라» 가 아니다</b> — 자리마다 답이
 * 다르고, 그 «다름» 자체를 여기서 못 박는다:
 * <ol>
 *   <li>목록·검색 — <b>빠진다</b>(안 그러면 계속 팔린다)</li>
 *   <li>상세 — <b>404</b>(URL 을 직접 치는 경로가 있다)</li>
 *   <li>새 리뷰·문의 — <b>못 단다</b>(곧 사라질 상품에 답변자 없는 글이 생긴다)</li>
 *   <li>🔴 장바구니 — <b>줄이 남는다</b>(구매만 막힌다). 지우면 복구해도 안 돌아온다</li>
 *   <li>🔴 관리자 복구 목록 — <b>보인다</b>(안 보이면 되돌릴 수가 없다)</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductSoftDeleteIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String admin;
    private String user;
    private UUID categoryId;
    private String productName;
    private UUID productId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        admin = login(member("delp_" + suffix, "ZZ삭제관리자" + suffix, Role.ADMIN));
        user = login(member("delu_" + suffix, "ZZ삭제일반" + suffix, Role.USER));
        categoryId = categoryRepository.save(Category.builder().name("ZZC-삭제" + suffix).build()).getId();
        productName = "ZZP-삭제대상" + suffix;
        productId = UUID.fromString(create(productName));
    }

    private String member(String loginId, String nickname, Role role) {
        memberRepository.save(Member.builder().loginId(loginId)
                .password(passwordEncoder.encode(PW)).nickname(nickname).role(role).build());
        return loginId;
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private String create(String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"설명\",\"price\":10000,"
                + "\"status\":\"SELLING\",\"categoryId\":\"" + categoryId + "\","
                + "\"variants\":[{\"name\":\"기본\",\"priceDelta\":0,\"stock\":5}]}";
        String res = mockMvc.perform(post("/api/products").header("Authorization", admin)
                        .contentType(JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data");
    }

    private void deleteProduct() throws Exception {
        mockMvc.perform(delete("/api/products/" + productId).header("Authorization", admin))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
    }

    // ── ⓪ 행이 남는다 (이 기능의 전제) ─────────────────────────

    @Test
    @DisplayName("🔴 삭제해도 **행이 남는다** — 여기가 깨지면 나머지 전부가 의미 없다")
    void delete_keepsRow() throws Exception {
        deleteProduct();

        Product product = productRepository.findById(productId).orElse(null);
        assertThat(product).as("하드 삭제로 돌아갔다면 복구할 대상 자체가 없다").isNotNull();
        assertThat(product.isDeleted()).isTrue();
        assertThat(product.getDeletedByName())
                .as("복구 화면이 «누가 지웠나» 를 보여준다 — 이름이 안 실리면 그 칸이 빈다")
                .isNotNull();
    }

    @Test
    @DisplayName("⚠ 두 번 지워도 시각이 갱신되지 않는다 — 갱신되면 유예가 영원히 안 끝난다")
    void delete_isIdempotent() throws Exception {
        deleteProduct();
        var first = productRepository.findById(productId).orElseThrow().getDeletedAt();

        deleteProduct();

        assertThat(productRepository.findById(productId).orElseThrow().getDeletedAt())
                .as("누를 때마다 D-7 로 되돌아가면 그 상품은 영영 안 지워진다")
                .isEqualTo(first);
    }

    // ── ① 목록·검색에서 빠진다 ────────────────────────────────

    @Test
    @DisplayName("🔴 삭제 대기 상품은 **목록에서 빠진다** — 안 빠지면 지운 상품이 계속 팔린다")
    void deleted_disappearsFromList() throws Exception {
        mockMvc.perform(get("/api/products").param("name", productName))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        deleteProduct();

        mockMvc.perform(get("/api/products").param("name", productName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    // ── ② 상세는 404 ─────────────────────────────────────────

    @Test
    @DisplayName("🔴 상세는 **404** — 목록에서 뺐어도 URL 을 직접 치면 열린다(알림·북마크가 그 경로다)")
    void deleted_detailIs404() throws Exception {
        mockMvc.perform(get("/api/products/" + productId)).andExpect(status().isOk());

        deleteProduct();

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── ③ 새 글을 못 단다 ────────────────────────────────────

    @Test
    @DisplayName("삭제 대기 상품에는 **새 문의를 못 단다** — 곧 사라질 상품에 답변자 없는 글이 생긴다")
    void deleted_blocksNewInquiry() throws Exception {
        // 상품 문의는 경로에 상품이 들어간다(일반 문의는 /api/inquiries 로 따로 있다).
        String url = "/api/products/" + productId + "/inquiries";
        String body = "{\"title\":\"ZZ문의\",\"content\":\"내용\",\"secret\":false}";
        mockMvc.perform(post(url).header("Authorization", user).contentType(JSON).content(body))
                .andExpect(status().isCreated());

        deleteProduct();

        mockMvc.perform(post(url).header("Authorization", user).contentType(JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // ── ④ 🔴 장바구니는 줄이 남는다 ──────────────────────────

    @Test
    @DisplayName("🔴 장바구니는 **줄이 남고 구매만 막힌다** — 지우면 상품을 복구해도 장바구니는 안 돌아온다")
    void deleted_keepsCartLineButBlocksPurchase() throws Exception {
        UUID variantId = variantId();
        addToCart(variantId);

        mockMvc.perform(get("/api/cart").header("Authorization", user))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].available").value(true));

        deleteProduct();

        mockMvc.perform(get("/api/cart").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()")
                        .value(1)) // 🔴 줄이 사라지면 복구의 절반이 무의미해진다
                .andExpect(jsonPath("$.data.items[0].available").value(false));
    }

    @Test
    @DisplayName("🔴 대조군: **복구하면 장바구니가 되살아난다** — 이것이 줄을 남긴 이유다")
    void restore_revivesCartLine() throws Exception {
        addToCart(variantId());
        deleteProduct();

        mockMvc.perform(post("/api/admin/products/" + productId + "/restore")
                        .header("Authorization", admin))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/cart").header("Authorization", user))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].available").value(true));
    }

    // ── ⑤ 관리자 복구 목록 ───────────────────────────────────

    @Test
    @DisplayName("🔴 관리자 목록에는 **보인다** — 안 보이면 되돌릴 수가 없다(리뷰 관리와 같은 규칙)")
    void deleted_visibleToAdmin() throws Exception {
        deleteProduct();

        String body = mockMvc.perform(get("/api/admin/products/deleted").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.data[*].id");
        assertThat(ids).contains(productId.toString());
    }

    @Test
    @DisplayName("⚠ 목록이 **언제 사라지는지**(purgeAt)를 준다 — 화면이 날짜를 직접 더하면 설정과 어긋난다")
    void deletedList_carriesPurgeAt() throws Exception {
        deleteProduct();

        String body = mockMvc.perform(get("/api/admin/products/deleted").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();

        String row = "$.data[?(@.id=='" + productId + "')]";
        assertThat(JsonPath.<List<String>>read(body, row + ".purgeAt")).isNotEmpty();
        assertThat(JsonPath.<List<String>>read(body, row + ".deletedBy"))
                .as("«누가 지웠나» 가 이 화면의 세 질문 중 하나다").isNotEmpty();
    }

    @Test
    @DisplayName("복구하면 목록·상세에 다시 나오고 복구 목록에서는 빠진다")
    void restore_bringsItBack() throws Exception {
        deleteProduct();

        mockMvc.perform(post("/api/admin/products/" + productId + "/restore")
                        .header("Authorization", admin))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/products/" + productId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/products").param("name", productName))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        String body = mockMvc.perform(get("/api/admin/products/deleted").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$.data[*].id")).doesNotContain(productId.toString());
    }

    @Test
    @DisplayName("⚠ 대기 중이 아닌 상품을 복구해도 **200** 이다(멱등) — 두 번 눌러도 «실패» 로 보이면 안 된다")
    void restore_isIdempotent() throws Exception {
        mockMvc.perform(post("/api/admin/products/" + productId + "/restore")
                        .header("Authorization", admin))
                .andExpect(status().isOk());
    }

    // ── ⑤ 감사 원장 (2026-08-14) ──────────────────────────────
    //
    // 🔴 **여기는 단위 테스트로 대신할 수 없다.** 새 enum 값이 실제로 들어가려면 Oracle 의
    //    CHECK 제약(V50)이 그 값을 알아야 하는데, 목(mock)은 제약을 모른다 —
    //    제약을 안 넓혔으면 **여기서만** ORA-02290 으로 터진다(Oracle enum CHECK 트랩).

    @Test
    @DisplayName("🔴 삭제가 **감사 원장에 남는다** — 대상은 상품 id, 이름은 detail 에 스냅샷")
    void delete_isAudited() throws Exception {
        deleteProduct();

        List<AdminAuditLog> logs = auditOf(productId);
        assertThat(logs).hasSize(1);
        AdminAuditLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo(AuditAction.PRODUCT_DELETE);
        assertThat(log.getActorName()).isNotBlank();
        assertThat(log.getDetail())
                .as("상품은 유예가 지나면 진짜로 사라진다 — 이름이 없으면 «무엇을 지웠는지» 를 영영 못 읽는다")
                .isEqualTo(productName);
        assertThat(log.getTargetLogin())
                .as("대상이 회원이 아니다 — '(탈퇴)' 같은 것으로 메우면 없는 계정을 찾게 된다(V45 의 판단)")
                .isNull();
    }

    @Test
    @DisplayName("⚠ 두 번 지워도 감사는 **한 줄**이다 — 조작이 없었으면 기록도 없다")
    void delete_idempotent_isAuditedOnce() throws Exception {
        deleteProduct();
        deleteProduct();

        assertThat(auditOf(productId)).hasSize(1);
    }

    @Test
    @DisplayName("복구도 남는다 — 삭제와 **짝**이라야 «지웠다 되살렸다» 가 읽힌다")
    void restore_isAudited() throws Exception {
        deleteProduct();
        mockMvc.perform(post("/api/admin/products/" + productId + "/restore")
                        .header("Authorization", admin))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        assertThat(auditOf(productId)).extracting(AdminAuditLog::getAction)
                .containsExactly(AuditAction.PRODUCT_DELETE, AuditAction.PRODUCT_RESTORE);
    }

    /** 이 상품을 대상으로 한 감사 이력(오래된 것부터). ⚠ 공유 DB 라 대상 id 로 좁힌다 — 상품은 매번 새로 만든다. */
    private List<AdminAuditLog> auditOf(UUID targetId) {
        return entityManager.createQuery(
                        "select a from AdminAuditLog a where a.targetId = :id order by a.createdAt",
                        AdminAuditLog.class)
                .setParameter("id", targetId)
                .getResultList();
    }

    // ── 권한 ─────────────────────────────────────────────────

    @Test
    @DisplayName("권한: 일반 회원 403 · 비로그인 401 · 관리자 200")
    void requiresAdmin() throws Exception {
        String url = "/api/admin/products/deleted";
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).header("Authorization", user)).andExpect(status().isForbidden());
        // ⚠ 401·403 은 엔드포인트가 있다는 증거가 아니다(WA §3) — 관리자에게 200 인지 함께 본다.
        mockMvc.perform(get(url).header("Authorization", admin)).andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/products/" + productId + "/restore")
                        .header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    // ── 도우미 ───────────────────────────────────────────────

    private UUID variantId() throws Exception {
        String body = mockMvc.perform(get("/api/products/" + productId))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.variants[0].id"));
    }

    private void addToCart(UUID variantId) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", user).contentType(JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"quantity\":1}"))
                .andExpect(status().isOk());
    }
}

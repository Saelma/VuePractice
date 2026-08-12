package com.glassvue.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.review.entity.Review;
import com.glassvue.domain.review.event.ReviewRatingChangedEvent;
import com.glassvue.domain.review.repository.ReviewRepository;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 리뷰 숨김 (2026-08-04, 백로그 B-18).
 *
 * <p>🔴 <b>이 기능의 위험은 전부 «같은 컬럼을 세 곳이 다르게 다룬다» 에 있다.</b> 셋 중 하나만 틀려도
 * 겉으로는 멀쩡해 보이는데 조용히 어긋난다 — 그래서 <b>규칙마다 테스트를 따로</b> 둔다:
 * <ol>
 *   <li><b>목록에서 빠진다</b> — 작성자 본인에게도.</li>
 *   <li><b>별점 집계에서 빠진다</b> — 안 빼면 보이지도 않는 리뷰가 별점을 끌어내린다.
 *       ⚠ 여기가 제일 안 보이는 자리다: 목록만 고치고 집계를 놓쳐도 <b>화면은 멀쩡하다.</b></li>
 *   <li>🔴 <b>상품당 1회 제한에는 그대로 센다</b> — 빼면 숨기자마자 새 리뷰를 쓸 수 있어
 *       <b>숨김이 무의미</b>해진다. 셋 중 유일하게 «빼지 않는» 자리다.</li>
 * </ol>
 *
 * <p>리뷰는 <b>리포지토리로 직접</b> 만든다 — API 로 만들려면 "구매한 사람만" 규칙 때문에 주문·결제를
 * 통째로 태워야 하는데, 여기서 볼 것은 숨김이라 그 비용을 질 이유가 없다
 * ({@code ReviewSortFilterIntegrationTest} 와 같은 판단).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents // 이벤트 발행 여부를 보려면 필요하다(WA §3, H-6)
class AdminReviewHideIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired EntityManager entityManager;
    @Autowired ApplicationEvents events;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String admin;
    private String user;
    private UUID productId;
    private UUID authorId;
    private UUID reviewId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        admin = login(member("rvadmin_" + suffix, "ZZ리뷰관리자" + suffix, Role.ADMIN));
        UUID userId = member("rvuser_" + suffix, "ZZ리뷰일반" + suffix, Role.USER);
        user = login(userId);

        Category cat = categoryRepository.save(Category.builder().name("ZZC-숨김" + suffix).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-숨김상품" + suffix).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();

        // 별점 5 와 1 — 하나를 숨기면 평균이 눈에 띄게 움직인다(3.0 ↔ 5.0/1.0).
        authorId = UUID.randomUUID();
        reviewId = review(authorId, 1);
        review(UUID.randomUUID(), 5);
        entityManager.flush();
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    private String login(UUID memberId) throws Exception {
        String loginId = memberRepository.findById(memberId).orElseThrow().getLoginId();
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private UUID review(UUID author, int rating) {
        return reviewRepository.save(Review.builder()
                .productId(productId).authorId(author).author("ZZ리뷰어")
                .rating(rating).content("내용").imageGroupId(null).build()).getId();
    }

    private void hide(UUID id) throws Exception {
        mockMvc.perform(post("/api/admin/reviews/" + id + "/hide").header("Authorization", admin))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
    }

    // ── 규칙 ① 목록에서 빠진다 ──────────────────────────────────

    @Test
    @DisplayName("숨기면 상품 리뷰 목록에서 빠진다")
    void hidden_disappearsFromProductList() throws Exception {
        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.data.page.totalElements").value(2));

        hide(reviewId);

        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.totalElements").value(1))
                .andExpect(jsonPath("$.data.page.content[0].rating").value(5));
    }

    @Test
    @DisplayName("⚠ 숨긴 리뷰는 **작성자 본인에게도** 안 보인다(2026-08-04 결정)")
    void hidden_invisibleEvenToAuthor() throws Exception {
        hide(reviewId);

        // 본인 토큰으로 봐도 목록이 갈리지 않는다 — 조회 조건이 한 줄이라 갈릴 자리가 없다.
        mockMvc.perform(get("/api/products/" + productId + "/reviews").header("Authorization", user))
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    // ── 규칙 ② 별점 집계에서 빠진다 ────────────────────────────

    @Test
    @DisplayName("🔴 숨기면 **평균 별점·리뷰 수에서도 빠진다**(목록만 고치면 화면은 멀쩡한데 별점이 틀린다)")
    void hidden_excludedFromStats() throws Exception {
        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.data.averageRating").value(3.0))
                .andExpect(jsonPath("$.data.reviewCount").value(2));

        hide(reviewId); // 별점 1 짜리를 숨긴다

        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.data.averageRating").value(5.0))
                .andExpect(jsonPath("$.data.reviewCount").value(1));
    }

    @Test
    @DisplayName("숨김을 해제하면 집계에 다시 들어온다(되돌릴 수 있어야 한다)")
    void unhide_restoresStats() throws Exception {
        hide(reviewId);

        mockMvc.perform(post("/api/admin/reviews/" + reviewId + "/unhide").header("Authorization", admin))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.data.averageRating").value(3.0))
                .andExpect(jsonPath("$.data.reviewCount").value(2));
    }

    // ── 규칙 ③ 상품당 1회 제한에는 그대로 센다 ─────────────────

    @Test
    @DisplayName("🔴 숨겨도 **상품당 1회 제한은 그대로** — 안 그러면 숨기자마자 새로 써서 숨김이 무의미해진다")
    void hidden_stillCountsForDuplicateCheck() throws Exception {
        hide(reviewId);

        assertThat(reviewRepository.existsByProductIdAndAuthorId(productId, authorId))
                .as("목록·집계에서는 빠지지만 여기서는 빠지면 안 된다")
                .isTrue();
    }

    // ── 관리자 목록 ───────────────────────────────────────────

    @Test
    @DisplayName("관리자 목록은 **숨긴 것도 함께** 보여준다(안 보이면 되돌릴 수가 없다)")
    void adminList_includesHidden() throws Exception {
        hide(reviewId);

        String body = mockMvc.perform(get("/api/admin/reviews?size=50").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        java.util.List<String> ids = JsonPath.read(body, "$.data.content[*].id");
        assertThat(ids).contains(reviewId.toString());
    }

    @Test
    @DisplayName("⚠ `hidden` 은 **세 가지 상태**다 — 안 보내면 전체, false 면 보이는 것만")
    void adminList_hiddenFilterHasThreeStates() throws Exception {
        hide(reviewId);
        String url = "/api/admin/reviews?size=50";

        // false 를 **보냈을 때**와 **안 보냈을 때**가 달라야 한다.
        // 클라이언트가 falsy 를 걸러 버리면 둘이 같아지는데, 그러면 필터가 조용히 죽는다.
        java.util.List<String> visible = JsonPath.read(
                mockMvc.perform(get(url + "&hidden=false").header("Authorization", admin))
                        .andReturn().getResponse().getContentAsString(), "$.data.content[*].id");
        java.util.List<String> onlyHidden = JsonPath.read(
                mockMvc.perform(get(url + "&hidden=true").header("Authorization", admin))
                        .andReturn().getResponse().getContentAsString(), "$.data.content[*].id");

        assertThat(visible).doesNotContain(reviewId.toString());
        assertThat(onlyHidden).contains(reviewId.toString());
    }

    @Test
    @DisplayName("관리자 목록에 **상품명**이 실린다(여러 상품을 가로지르므로 무엇에 달린 리뷰인지 알아야 한다)")
    void adminList_carriesProductName() throws Exception {
        String body = mockMvc.perform(get("/api/admin/reviews?hidden=false&size=50")
                        .header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        java.util.List<String> names = JsonPath.read(body, "$.data.content[*].productName");
        assertThat(names).contains(productRepository.findById(productId).orElseThrow().getName());
    }

    // ── 상품이 사라진 리뷰 (2026-08-12, Fable 감사 5번) ────────
    //
    // 🔴 리뷰에는 상품 FK 가 없어 **상품을 지워도 리뷰는 남는다**(ARCHITECTURE 「느슨한 UUID 참조」).
    // 리포지토리로 직접 지운다 — 여기서 볼 것은 **읽는 쪽**이고, 상품 삭제 API 를 태우면
    // 이미지 그룹·옵션까지 끌고 들어와 이 테스트가 무엇 때문에 깨졌는지 흐려진다.

    @Test
    @DisplayName("🔴 상품을 지워도 그 리뷰는 관리자 목록에 **남는다** — 안 보이면 고칠 대상이 있다는 것조차 모른다")
    void adminList_keepsReviewOfDeletedProduct() throws Exception {
        productRepository.deleteById(productId);
        entityManager.flush();
        entityManager.clear();

        String body = mockMvc.perform(get("/api/admin/reviews?size=50").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        java.util.List<String> ids = JsonPath.read(body, "$.data.content[*].id");
        assertThat(ids).contains(reviewId.toString());
    }

    @Test
    @DisplayName("🔴 상품이 지워진 줄은 `productDeleted=true` 로 내려간다(빈칸이면 «데이터가 잘못됐다»로 읽힌다)")
    void adminList_flagsDeletedProduct() throws Exception {
        productRepository.deleteById(productId);
        entityManager.flush();
        entityManager.clear();

        String body = mockMvc.perform(get("/api/admin/reviews?size=50").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String row = "$.data.content[?(@.id=='" + reviewId + "')]";
        assertThat(JsonPath.<java.util.List<Boolean>>read(body, row + ".productDeleted"))
                .as("판정은 서버 한 곳에서 한다 — 화면이 «이름이 비었다»로 다시 판정하면 두 곳이 갈린다")
                .containsExactly(true);
        assertThat(JsonPath.<java.util.List<String>>read(body, row + ".productName"))
                .as("이름은 비어야 한다 — 없는 상품의 이름을 지어내지 않는다")
                .containsExactly((String) null);
    }

    @Test
    @DisplayName("⚠ 대조군: 상품이 **살아 있으면** `productDeleted=false` — 플래그가 늘 참이면 표기가 무의미하다")
    void adminList_liveProduct_notFlagged() throws Exception {
        String body = mockMvc.perform(get("/api/admin/reviews?size=50").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String row = "$.data.content[?(@.id=='" + reviewId + "')]";
        assertThat(JsonPath.<java.util.List<Boolean>>read(body, row + ".productDeleted"))
                .containsExactly(false);
        assertThat(JsonPath.<java.util.List<String>>read(body, row + ".productName"))
                .containsExactly(productRepository.findById(productId).orElseThrow().getName());
    }

    @Test
    @DisplayName("⚠ **숨김** 상품은 삭제가 아니다 — 이름이 그대로 실리고 플래그는 false 다")
    void adminList_hiddenProduct_notFlagged() throws Exception {
        Product p = productRepository.findById(productId).orElseThrow();
        p.update(p.getName(), p.getTagline(), p.getDescription(), p.getPrice(), p.getListPrice(),
                ProductStatus.HIDDEN, p.getImageGroupId(), p.getCategory());
        productRepository.save(p);
        entityManager.flush();
        entityManager.clear();

        String body = mockMvc.perform(get("/api/admin/reviews?size=50").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String row = "$.data.content[?(@.id=='" + reviewId + "')]";
        assertThat(JsonPath.<java.util.List<Boolean>>read(body, row + ".productDeleted"))
                .as("조회가 findAllById 라 상태로 거르지 않는다 — 여기가 true 가 되면 라벨이 거짓말을 한다")
                .containsExactly(false);
    }

    @Test
    @DisplayName("이미 숨긴 리뷰를 또 숨겨도 200 이고 상태는 그대로다(멱등)")
    void hide_isIdempotent() throws Exception {
        hide(reviewId);
        hide(reviewId);

        assertThat(reviewRepository.findById(reviewId).orElseThrow().isHidden()).isTrue();
    }

    @Test
    @DisplayName("⚠ 상태가 **안 바뀌면 집계 이벤트를 발행하지 않는다** — 헛된 캐시 무효화를 막는 가드")
    void hide_unchanged_publishesNoEvent() throws Exception {
        hide(reviewId);          // 여기서 한 번 발행된다
        events.clear();          // 그다음부터를 센다

        hide(reviewId);          // 이미 숨겨져 있다 → 아무 일도 없어야 한다

        assertThat(events.stream(ReviewRatingChangedEvent.class).toList())
                .as("안 바뀐 요청에 이벤트를 또 내면 상품 목록 캐시가 헛되이 비워진다")
                .isEmpty();
    }

    @Test
    @DisplayName("상태가 **바뀌면** 집계 이벤트를 발행한다(위 가드가 진짜 변경까지 막으면 안 된다)")
    void hide_changed_publishesEvent() throws Exception {
        events.clear();

        hide(reviewId);

        assertThat(events.stream(ReviewRatingChangedEvent.class).toList())
                .as("숨기면 상품의 평균 별점이 달라지므로 catalog 가 알아야 한다")
                .hasSize(1);
    }

    @Test
    @DisplayName("권한: 일반 회원 403 · 비로그인 401 · 관리자 200")
    void requiresAdmin() throws Exception {
        String url = "/api/admin/reviews";
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).header("Authorization", user)).andExpect(status().isForbidden());
        // ⚠ 401·403 은 엔드포인트가 있다는 증거가 아니다(WA §3) — 관리자에게 200 인지 함께 본다.
        mockMvc.perform(get(url).header("Authorization", admin)).andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/reviews/" + reviewId + "/hide").header("Authorization", user))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("없는 리뷰를 숨기면 404")
    void hide_unknownReview_404() throws Exception {
        mockMvc.perform(post("/api/admin/reviews/" + UUID.randomUUID() + "/hide")
                        .header("Authorization", admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

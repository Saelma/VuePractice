package com.glassvue.domain.inquiry;

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
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryType;
import com.glassvue.domain.inquiry.entity.InquiryStatus;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
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
 * 관리자 문의 목록 (2026-08-06, 백로그 G-3 1단계).
 *
 * <p>🔴 <b>이 기능이 푸는 것은 «답할 경로가 없다» 하나다.</b> 그전까지 관리자가 문의를 보는 유일한 길이
 * 상품 상세의 문의 탭이라, «어느 상품이든 답을 기다리는 문의» 를 볼 자리가 없었다.
 * 그래서 여기서 볼 것도 셋으로 좁다:
 * <ol>
 *   <li><b>상품을 가로지른다</b> — 두 상품의 문의가 한 목록에 함께 나온다.</li>
 *   <li><b>{@code status} 는 세 가지 상태다</b> — 안 보내면 전체. 기본값을 서버가 박으면
 *       「전체」를 볼 방법이 사라진다.</li>
 *   <li>🔴 <b>비밀글도 본문이 실린다</b> — 고객 응답은 마스킹하지만 관리자는 열람 대상이다.
 *       ⚠ 여기가 제일 조용히 틀리는 자리다: 고객용 DTO 를 그대로 재사용하면 <b>목록은 멀쩡히 뜨는데</b>
 *       본문만 «🔒 비밀글입니다.» 로 나와 답을 쓸 수가 없다.</li>
 * </ol>
 *
 * <p>문의는 <b>리포지토리로 직접</b> 만든다 — API 로 만들 수도 있지만 여기서 볼 것은 목록이라
 * 작성 경로까지 태울 이유가 없다({@code AdminReviewHideIntegrationTest} 와 같은 판단).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminInquiryListIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired InquiryRepository inquiryRepository;
    @Autowired EntityManager entityManager;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String LIST = "/api/admin/inquiries";

    private String admin;
    private String user;
    private UUID productA;
    private UUID productB;
    private UUID authorId;
    private UUID openId;     // 미답변, 공개
    private UUID secretId;   // 미답변, 비밀글 (다른 상품)
    private String productAName;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        admin = login(member("iqadmin_" + suffix, "ZZ문의관리자" + suffix, Role.ADMIN));
        user = login(member("iquser_" + suffix, "ZZ문의일반" + suffix, Role.USER));

        Category cat = categoryRepository.save(Category.builder().name("ZZC-문의" + suffix).build());
        productAName = "ZZP-문의상품A" + suffix;
        productA = product(productAName, cat);
        productB = product("ZZP-문의상품B" + suffix, cat);

        authorId = UUID.randomUUID();
        openId = inquiry(productA, "ZZ배송 언제 오나요", false);
        secretId = inquiry(productB, "ZZ비밀 문의", true);
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

    private UUID product(String name, Category cat) {
        return productRepository.save(Product.builder()
                .name(name).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
    }

    private UUID inquiry(UUID productId, String title, boolean secret) {
        return inquiryRepository.save(Inquiry.builder()
                .productId(productId).type(InquiryType.PRODUCT).authorId(authorId).author("ZZ문의자")
                .title(title).content("본문-" + title).secret(secret).imageGroupId(null).build()).getId();
    }

    private String listAs(String token, String query) throws Exception {
        return mockMvc.perform(get(LIST + query).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // ── ① 상품을 가로지른다 ────────────────────────────────────

    @Test
    @DisplayName("🔴 관리자 목록은 **상품을 가로지른다** — 상품 상세에 들어가지 않고도 전부 보인다")
    void adminList_crossesProducts() throws Exception {
        List<String> ids = JsonPath.read(listAs(admin, "?size=200"), "$.data.content[*].id");

        assertThat(ids)
                .as("서로 다른 상품(A·B)에 달린 문의가 **한 목록**에 나와야 한다 — 이게 G-3 을 막던 자리다")
                .contains(openId.toString(), secretId.toString());
    }

    @Test
    @DisplayName("목록에 **상품명**이 실린다(가로지르므로 무엇에 달린 문의인지 알아야 한다)")
    void adminList_carriesProductName() throws Exception {
        List<String> names = JsonPath.read(listAs(admin, "?size=200"), "$.data.content[*].productName");

        assertThat(names).contains(productAName);
    }

    // ── ② status 는 세 가지 상태다 ─────────────────────────────

    @Test
    @DisplayName("⚠ `status` 는 **세 가지 상태**다 — 안 보내면 전체, WAITING 이면 미답변만")
    void adminList_statusFilterHasThreeStates() throws Exception {
        answer(openId);

        List<String> all = JsonPath.read(listAs(admin, "?size=200"), "$.data.content[*].id");
        List<String> waiting = JsonPath.read(listAs(admin, "?size=200&status=WAITING"), "$.data.content[*].id");
        List<String> answered = JsonPath.read(listAs(admin, "?size=200&status=ANSWERED"), "$.data.content[*].id");

        assertThat(all).as("안 보내면 전체다").contains(openId.toString(), secretId.toString());
        assertThat(waiting).as("답변된 것은 미답변 탭에서 빠진다")
                .contains(secretId.toString()).doesNotContain(openId.toString());
        assertThat(answered).as("답변된 것만")
                .contains(openId.toString()).doesNotContain(secretId.toString());
    }

    @Test
    @DisplayName("🔴 **총건수도 필터를 따른다** — 목록과 카운트가 갈리면 «N건» 이라 써 놓고 줄 수가 다르다")
    void adminList_totalCountFollowsFilter() throws Exception {
        // ⚠ 이 테스트는 **변형 주입에서 구멍이 드러나 뒤늦게 넣은 것**이다(2026-08-06, M4).
        //    카운트 쿼리에서 조건을 빼도 앞의 테스트들은 전부 통과했다 — 다들 `content[*].id` 만 봤고
        //    페이지가 한 장이라 목록은 멀쩡했기 때문이다. 어긋남은 **페이저의 총건수**에서만 보인다.
        //
        //    ⚠ 절대값으로 못 박지 않는다 — 공유 DB 라 다른 검증이 남긴 문의가 함께 세어진다.
        //    대신 **쪼갠 합 == 전체** 라는 관계로 본다(둘 중 어느 쪽 데이터가 늘어도 성립한다).
        answer(openId); // 한쪽으로 쏠린 상태를 만든다 — 둘 다 0 이면 어떤 변형도 안 드러난다

        int all = JsonPath.read(listAs(admin, "?size=1"), "$.data.totalElements");
        int waiting = JsonPath.read(listAs(admin, "?size=1&status=WAITING"), "$.data.totalElements");
        int answered = JsonPath.read(listAs(admin, "?size=1&status=ANSWERED"), "$.data.totalElements");

        assertThat(waiting + answered)
                .as("상태는 둘뿐이라 쪼갠 합이 전체와 같아야 한다 — 카운트가 필터를 무시하면 두 배가 된다")
                .isEqualTo(all);
        assertThat(waiting).as("최소한 이 테스트가 만든 미답변 한 건은 있다").isPositive();
        assertThat(answered).isPositive();
    }

    @Test
    @DisplayName("⚠ 잘못된 status 값은 400 이다(조용히 전체를 주지 않는다)")
    void adminList_badStatus_400() throws Exception {
        mockMvc.perform(get(LIST + "?status=NOPE").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── ③ 비밀글 본문 ─────────────────────────────────────────

    @Test
    @DisplayName("🔴 비밀글도 관리자에게는 **본문이 그대로** 실린다(가리면 답을 쓸 수가 없다)")
    void adminList_secretBodyNotMasked() throws Exception {
        String body = listAs(admin, "?size=200");

        List<String> contents = JsonPath.read(body,
                "$.data.content[?(@.id == '" + secretId + "')].content");
        List<Boolean> secrets = JsonPath.read(body,
                "$.data.content[?(@.id == '" + secretId + "')].secret");

        assertThat(contents).containsExactly("본문-ZZ비밀 문의");
        assertThat(secrets).as("가리진 않되 **비밀글이라는 사실**은 실어야 화면이 표시할 수 있다")
                .containsExactly(true);
    }

    @Test
    @DisplayName("대조: 같은 비밀글이 **고객 목록에서는** 마스킹된다(관리자 예외가 마스킹 자체를 깬 게 아니다)")
    void productList_secretStillMaskedForOthers() throws Exception {
        String body = mockMvc.perform(get("/api/products/" + productB + "/inquiries").header("Authorization", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<String> contents = JsonPath.read(body, "$.data.content[*].content");
        List<Boolean> masked = JsonPath.read(body, "$.data.content[*].masked");

        assertThat(contents).doesNotContain("본문-ZZ비밀 문의");
        assertThat(masked).contains(true);
    }

    // ── 답변은 기존 API 를 그대로 쓴다 ─────────────────────────

    @Test
    @DisplayName("🔴 목록에서 찾은 문의에 **기존 답변 API**가 그대로 붙는다 — 새로 만든 건 «찾는 길» 뿐이다")
    void answer_reusesExistingEndpoint() throws Exception {
        answer(openId);

        assertThat(inquiryRepository.findById(openId).orElseThrow().getStatus())
                .isEqualTo(InquiryStatus.ANSWERED);
    }

    private void answer(UUID id) throws Exception {
        mockMvc.perform(post("/api/inquiries/" + id + "/answer").contentType(JSON)
                        .header("Authorization", admin)
                        .content("{\"answer\":\"ZZ답변 내용\"}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
    }

    // ── 권한 ──────────────────────────────────────────────────

    @Test
    @DisplayName("권한: 일반 회원 403 · 비로그인 401 · 관리자 200")
    void requiresAdmin() throws Exception {
        mockMvc.perform(get(LIST)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(LIST).header("Authorization", user)).andExpect(status().isForbidden());
        // ⚠ 401·403 은 엔드포인트가 있다는 증거가 아니다(WA §3) — 관리자에게 200 인지 함께 본다.
        mockMvc.perform(get(LIST).header("Authorization", admin)).andExpect(status().isOk());
    }
}

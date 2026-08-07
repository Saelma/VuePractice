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
 * 일반 고객센터 문의 + 내 문의 목록 (2026-08-07, 백로그 G-3 2·3단계).
 *
 * <p>🔴 <b>이 기능이 푸는 것은 «물어볼 데가 없다» 다.</b> 1단계(2026-08-06)가 관리자 <b>목록</b>을 열어
 * *"답할 경로가 없다"* 를 먼저 풀었고, 그래서 그때까지는 <b>답할 수 있는데 물어볼 데가 없는</b> 상태였다.
 * 작성 경로가 {@code POST /products/{id}/inquiries} 하나뿐이라 *"배송이 안 와요"* 를 물으려면
 * <b>아무 상품이나 골라야</b> 했다.
 *
 * <p>여기서 볼 것은 넷이다:
 * <ol>
 *   <li><b>상품 없이 문의가 만들어진다</b> — 그리고 그게 <b>상품 문의 목록에 안 섞인다.</b></li>
 *   <li><b>유형과 상품은 짝이다</b> — 일반 경로로 PRODUCT 를 보내면 거부한다(조용히 안 바꾼다).</li>
 *   <li><b>내 문의 목록은 내 것만</b> 준다 — 그리고 상품 문의·일반 문의를 <b>가르지 않는다.</b></li>
 *   <li><b>총건수가 목록을 따른다</b> — 2026-08-06 에 변형 M4 가 드러낸 구멍과 같은 자리.</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GeneralInquiryIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired InquiryRepository inquiryRepository;
    @Autowired EntityManager entityManager;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String CREATE = "/api/inquiries";
    private static final String MINE = "/api/inquiries/me";

    private String me;
    private String other;
    private String admin;
    private UUID myId;
    private UUID productId;
    private String productName;
    private UUID myProductInquiry;   // 내가 쓴 **상품** 문의
    private UUID othersInquiry;      // 남이 쓴 문의

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        myId = member("gqme_" + suffix, "ZZ고객센터나" + suffix, Role.USER);
        me = login(myId);
        other = login(member("gqother_" + suffix, "ZZ고객센터남" + suffix, Role.USER));
        admin = login(member("gqadmin_" + suffix, "ZZ고객센터관리자" + suffix, Role.ADMIN));

        Category cat = categoryRepository.save(Category.builder().name("ZZC-고객센터" + suffix).build());
        productName = "ZZP-고객센터상품" + suffix;
        productId = productRepository.save(Product.builder()
                .name(productName).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();

        myProductInquiry = inquiryRepository.save(Inquiry.builder()
                .productId(productId).type(InquiryType.PRODUCT).authorId(myId).author("ZZ고객센터나")
                .title("ZZ이 상품 재입고 되나요").content("본문").secret(false).build()).getId();
        othersInquiry = inquiryRepository.save(Inquiry.builder()
                .productId(null).type(InquiryType.ETC).authorId(UUID.randomUUID()).author("ZZ남")
                .title("ZZ남의 일반 문의").content("본문").secret(false).build()).getId();
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

    private String createGeneral(String token, String type, String title) throws Exception {
        String body = mockMvc.perform(post(CREATE).contentType(JSON).header("Authorization", token)
                        .content("{\"type\":\"" + type + "\",\"title\":\"" + title
                                + "\",\"content\":\"본문-" + title + "\",\"secret\":false}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        entityManager.flush();
        entityManager.clear();
        return JsonPath.read(body, "$.data");
    }

    private String mine(String token, String query) throws Exception {
        return mockMvc.perform(get(MINE + query).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // ── ① 상품 없이 만들어진다 ────────────────────────────────

    @Test
    @DisplayName("🔴 **상품 없이** 문의가 만들어진다 — 이것이 2단계가 연 자리다")
    void createsGeneralInquiry_withoutProduct() throws Exception {
        String id = createGeneral(me, "DELIVERY", "ZZ배송이 안 와요");

        Inquiry saved = inquiryRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(saved.getProductId())
                .as("상품을 안 고르고도 문의가 된다 — 그전엔 아무 상품이나 골라야 했다").isNull();
        assertThat(saved.getType()).isEqualTo(InquiryType.DELIVERY);
        assertThat(saved.getAuthorId()).as("작성자는 **로그인에서** 나온다").isEqualTo(myId);
    }

    @Test
    @DisplayName("🔴 일반 문의는 **상품 문의 목록에 안 섞인다**(product_id 가 열려도)")
    void generalInquiry_doesNotLeakIntoProductList() throws Exception {
        String id = createGeneral(me, "REFUND", "ZZ환불 계좌를 바꾸고 싶어요");

        String body = mockMvc.perform(get("/api/products/" + productId + "/inquiries?size=200"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.data.content[*].id");

        // productId.eq(...) 라 NULL 행은 구조적으로 안 잡힌다(= NULL 은 참이 될 수 없다).
        // ⚠ «구조적으로 안전하다» 는 판단은 맞지만, 조회 조건이 언젠가 바뀔 수 있어 여기서 못 박는다.
        assertThat(ids).as("일반 문의가 남의 상품 페이지에 걸리면 안 된다").doesNotContain(id);
        assertThat(ids).as("같은 상품의 상품 문의는 그대로 나온다").contains(myProductInquiry.toString());
    }

    // ── ② 유형과 상품은 짝이다 ────────────────────────────────

    @Test
    @DisplayName("🔴 일반 경로로 **PRODUCT 를 보내면 400** — 조용히 다른 값으로 바꾸지 않는다")
    void generalCreate_rejectsProductType() throws Exception {
        mockMvc.perform(post(CREATE).contentType(JSON).header("Authorization", me)
                        .content("{\"type\":\"PRODUCT\",\"title\":\"ZZ상품문의인척\","
                                + "\"content\":\"본문\",\"secret\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INQUIRY-400T"));
    }

    @Test
    @DisplayName("⚠ 없는 유형은 400 이다(조용히 기타로 떨어뜨리지 않는다)")
    void generalCreate_rejectsUnknownType() throws Exception {
        mockMvc.perform(post(CREATE).contentType(JSON).header("Authorization", me)
                        .content("{\"type\":\"NOPE\",\"title\":\"ZZ제목\","
                                + "\"content\":\"본문\",\"secret\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상품 문의는 **경로가 유형을 정한다** — 본문에 type 을 받지 않는다")
    void productInquiry_typeComesFromPath() throws Exception {
        String body = mockMvc.perform(post("/api/products/" + productId + "/inquiries")
                        .contentType(JSON).header("Authorization", me)
                        .content("{\"title\":\"ZZ상품문의\",\"content\":\"본문\",\"secret\":false}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        entityManager.flush();
        entityManager.clear();

        Inquiry saved = inquiryRepository.findById(
                UUID.fromString(JsonPath.read(body, "$.data"))).orElseThrow();
        assertThat(saved.getType()).isEqualTo(InquiryType.PRODUCT);
        assertThat(saved.getProductId()).isEqualTo(productId);
    }

    // ── ③ 내 문의 목록 ────────────────────────────────────────

    @Test
    @DisplayName("🔴 내 문의 목록은 **내 것만** 준다")
    void myInquiries_onlyMine() throws Exception {
        String id = createGeneral(me, "ETC", "ZZ내 일반 문의");

        List<String> ids = JsonPath.read(mine(me, "?size=200"), "$.data.content[*].id");

        assertThat(ids).contains(id);
        assertThat(ids).as("남이 쓴 문의가 섞이면 목록 API 의 가장 흔한 구멍이다")
                .doesNotContain(othersInquiry.toString());
    }

    @Test
    @DisplayName("🔴 상품 문의와 일반 문의를 **가르지 않는다** — 한 목록에 함께 온다")
    void myInquiries_mixesProductAndGeneral() throws Exception {
        String general = createGeneral(me, "DELIVERY", "ZZ배송 문의");

        String body = mine(me, "?size=200");
        List<String> ids = JsonPath.read(body, "$.data.content[*].id");
        List<String> types = JsonPath.read(body, "$.data.content[*].type");

        // 사용자는 «내가 물어본 것» 이 상품에 달렸는지 고객센터에 냈는지 기억하지 못한다.
        assertThat(ids).contains(general, myProductInquiry.toString());
        assertThat(types).as("대신 유형이 줄마다 실려 화면이 배지로 구분한다")
                .contains("DELIVERY", "PRODUCT");
    }

    @Test
    @DisplayName("상품 문의 줄에는 **상품명**이 실리고, 일반 문의 줄은 비어 있다")
    void myInquiries_productNameOnlyForProductInquiry() throws Exception {
        String general = createGeneral(me, "ETC", "ZZ일반");

        String body = mine(me, "?size=200");
        List<String> mineName = JsonPath.read(body,
                "$.data.content[?(@.id == '" + myProductInquiry + "')].productName");
        List<String> generalName = JsonPath.read(body,
                "$.data.content[?(@.id == '" + general + "')].productName");

        assertThat(mineName).containsExactly(productName);
        assertThat(generalName).as("일반 문의는 상품이 없다 — 줄은 남고 이름만 빈다")
                .containsExactly((String) null);
    }

    @Test
    @DisplayName("🔴 **총건수가 목록을 따른다** — 내 것만 세야 한다")
    void myInquiries_totalCountFollowsOwner() throws Exception {
        // ⚠ 2026-08-06 M4 가 드러낸 자리와 같다: 카운트 쿼리만 조건을 잃으면 목록은 멀쩡하고
        //   **페이저의 총건수만** 틀린다. 그래서 목록 API 를 만들면 totalElements 를 세트로 단언한다(WA §3).
        //
        // ⚠ 여기서는 **절대값으로 못 박을 수 있다** — 관리자 목록(2026-08-06)과 다른 점이다.
        //   저기는 모든 회원을 가로질러 세므로 공유 DB 의 남은 데이터가 함께 세어졌지만,
        //   여기는 조회 범위가 **setUp 에서 방금 만든 회원**이라 그 사람의 문의는 이 테스트가 만든 것뿐이다.
        //   관계식(쪼갠 합 == 전체)보다 절대값이 강하므로, 쓸 수 있을 때는 쓴다.
        createGeneral(me, "ETC", "ZZ카운트용1");
        createGeneral(me, "ETC", "ZZ카운트용2");

        int total = JsonPath.read(mine(me, "?size=1"), "$.data.totalElements");
        int rows = ((List<?>) JsonPath.read(mine(me, "?size=200"), "$.data.content[*].id")).size();
        int othersTotal = JsonPath.read(mine(other, "?size=1"), "$.data.totalElements");

        assertThat(total).as("한 페이지에 다 담기는 크기라 총건수와 줄 수가 같아야 한다").isEqualTo(rows);
        assertThat(total).as("setUp 의 상품 문의 1 + 방금 만든 일반 문의 2").isEqualTo(3);
        assertThat(othersTotal)
                .as("남의 총건수에 내 문의가 세어지면 안 된다 — 카운트가 authorId 를 잃으면 여기가 터진다")
                .isZero();
    }

    // ── ④ 관리자 목록에도 유형이 실린다 ───────────────────────

    @Test
    @DisplayName("관리자 목록에 **유형**이 실린다 — 상품명이 비어도 성격을 가릴 수 있어야 한다")
    void adminList_carriesType() throws Exception {
        String id = createGeneral(me, "REFUND", "ZZ환불 문의");

        String body = mockMvc.perform(get("/api/admin/inquiries?size=200").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(body, "$.data.content[?(@.id == '" + id + "')].type");
        List<String> names = JsonPath.read(body, "$.data.content[?(@.id == '" + id + "')].productName");

        assertThat(types).containsExactly("REFUND");
        assertThat(names).as("일반 문의라 상품명이 없다 — 그래서 유형이 없으면 성격을 못 가린다")
                .containsExactly((String) null);
    }

    // ── 권한 (WA §2-4) ────────────────────────────────────────

    @Test
    @DisplayName("권한: 작성·내 목록 둘 다 비로그인 401 · 로그인 200/201")
    void requiresLogin() throws Exception {
        // ⚠ **이 401 이 무엇을 증명하는지 정확히 적어 둔다.**
        //   «SecurityConfig 에 규칙이 있다» 는 증명이 **아니다** — 규칙을 통째로 지워도 이 테스트는
        //   통과한다(2026-08-07 변형 M7 실측). 컨트롤러가 `@LoginUser`(required=true)로 받아
        //   LoginUserArgumentResolver 가 **같은 UNAUTHENTICATED 401** 을 내기 때문이다.
        //   즉 두 방어선이 HTTP 로는 구분되지 않는다.
        //   여기서 못 박는 것은 «비로그인에게 남의 문의가 나가지 않는다» 라는 **결과**다 — 그건 어느
        //   방어선이 잡든 참이어야 한다. 규칙 자체를 지키는 것은 테스트가 아니라 코드 리뷰의 몫이다.
        mockMvc.perform(post(CREATE).contentType(JSON)
                        .content("{\"type\":\"ETC\",\"title\":\"ZZ\",\"content\":\"c\",\"secret\":false}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(MINE)).andExpect(status().isUnauthorized());

        // ⚠ 401 은 엔드포인트가 있다는 증거가 아니다(WA §3) — 로그인하면 실제로 도는지 함께 본다.
        mockMvc.perform(get(MINE).header("Authorization", me)).andExpect(status().isOk());
    }
}

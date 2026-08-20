package com.glassvue.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
import com.glassvue.domain.audit.repository.AdminAuditLogRepository;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 확대 3차 — <b>상품 등록·수정 · 쿠폰 · 할인</b>이 원장에 남는가 (2026-08-20, V53).
 *
 * <p>🔴 <b>«발행했다» 가 아니라 «행이 남았다» 를 본다.</b> {@code AdminAuditIntegrationTest} 가
 * 세운 규율 그대로다 — 감사는 <b>쓰기만 하고 읽을 일이 없는 기능</b>이라, 조용히 안 남고 있어도
 * 아무도 모른다.
 *
 * <p>여기서 고정하는 계약:
 * <ul>
 *   <li><b>detail 이 «무엇이 바뀌었나» 를 말한다</b> — 바뀐 것만, 전→후. V50 이 «따로 정해야 한다» 며
 *       미뤄 둔 결정이라 <b>형식 자체가 계약</b>이다.</li>
 *   <li>🔴 <b>안 바뀐 필드는 detail 에 안 나온다</b> — 매번 전부 적으면 원장을 읽을 이유가 없어진다.</li>
 *   <li><b>바뀐 것이 없어도 줄은 남는다</b>(«변경 없음»). 2026-08-20 사용자 결정이고,
 *       {@code PRODUCT_DELETE} 의 멱등 판단과 <b>일부러 갈린다</b>.</li>
 *   <li>🔴 <b>할인 조작의 대상은 상품</b> — 상품 이력과 같은 {@code targetId} 로 묶인다.</li>
 *   <li><b>재고는 detail 에 없다</b> — {@code stock_history} 가 이미 갖고 있다(중복 금지).</li>
 * </ul>
 *
 * <p>⚠ 공유 espdb 라 운영 데이터가 섞여 있다. 단언은 <b>이 테스트가 만든 대상의 id 로 좁혀서만</b> 한다.
 *
 * <p>DB_HOST 있을 때만 실행, {@code @Transactional} 롤백으로 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogAuditIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long BASE_PRICE = 10_000L;

    private String suffix;
    private String adminLoginId;
    private String memberLoginId;
    private UUID adminId;
    private UUID memberId;
    private UUID categoryId;
    private UUID productId;
    private String productName;
    private String auth;

    @BeforeEach
    void setUp() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "ca_a_" + suffix;
        memberLoginId = "ca_m_" + suffix;
        productName = "ZZA-감사상품" + suffix;

        adminId = memberRepository.save(Member.builder().loginId(adminLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ감사관리자" + suffix)
                .role(Role.ADMIN).build()).getId();
        memberId = memberRepository.save(Member.builder().loginId(memberLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ감사회원" + suffix)
                .role(Role.USER).build()).getId();
        categoryId = categoryRepository.save(
                Category.builder().name("ZZC-감사" + suffix).build()).getId();
        auth = login(adminLoginId);
        productId = createProduct();
    }

    // ── 도우미 ────────────────────────────────────────────────

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private UUID createProduct() throws Exception {
        String body = "{\"name\":\"" + productName + "\",\"description\":\"설명\","
                + "\"price\":" + BASE_PRICE + ",\"status\":\"SELLING\","
                + "\"categoryId\":\"" + categoryId + "\",\"variants\":["
                + "{\"name\":\"기본\",\"priceDelta\":0,\"stock\":5}]}";
        String res = mockMvc.perform(post("/api/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(res, "$.data"));
    }

    /** 상품 수정 본문 — 인자로 흔드는 축만 바꾸고 나머지는 등록 때와 같게 둔다. */
    private String productBody(String name, long price, String status, int stock) {
        return "{\"name\":\"" + name + "\",\"description\":\"설명\","
                + "\"price\":" + price + ",\"status\":\"" + status + "\","
                + "\"categoryId\":\"" + categoryId + "\",\"variants\":["
                + "{\"name\":\"기본\",\"priceDelta\":0,\"stock\":" + stock + "}]}";
    }

    private void updateProduct(String body) throws Exception {
        mockMvc.perform(put("/api/products/" + productId).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth).content(body))
                .andExpect(status().isOk());
    }

    /** 이 테스트가 만든 대상의 행만 — 공유 DB 라 전체를 세면 남의 것이 섞인다. */
    private List<AdminAuditLog> rowsOf(UUID targetId, AuditAction action) {
        return auditLogRepository.search(action, null, null, PageRequest.of(0, 50)).getContent()
                .stream().filter(row -> row.getTargetId().equals(targetId)).toList();
    }

    private AdminAuditLog onlyRow(UUID targetId, AuditAction action) {
        List<AdminAuditLog> rows = rowsOf(targetId, action);
        assertThat(rows).as("%s 행이 정확히 하나", action).hasSize(1);
        return rows.get(0);
    }

    // ── 상품 ────────────────────────────────────────────────

    @DisplayName("상품 등록 — 원장에 스냅샷이 남는다(이름·판매가·옵션 수)")
    @Test
    void productCreateIsRecorded() {
        AdminAuditLog row = onlyRow(productId, AuditAction.PRODUCT_CREATE);

        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.PRODUCT);
        // 대상이 상품이라 loginId 는 없다 — «없는 것이 정상» 이다(V44 가 nullable 로 연 자리).
        assertThat(row.getTargetLogin()).isNull();
        assertThat(row.getActorId()).isEqualTo(adminId);
        assertThat(row.getDetail()).contains(productName).contains("10000").contains("옵션 1개");
    }

    @DisplayName("🔴 상품 수정 — 바뀐 것만 «전→후» 로 적는다")
    @Test
    void productUpdateRecordsOnlyChanges() throws Exception {
        updateProduct(productBody(productName, 8_000L, "HIDDEN", 5));

        String detail = onlyRow(productId, AuditAction.PRODUCT_UPDATE).getDetail();

        assertThat(detail).contains("판매가 10000→8000");
        assertThat(detail).contains("상태 SELLING→HIDDEN");
        // 🔴 여기가 이 형식의 요점이다 — 안 바꾼 이름이 나오면 «무엇이 바뀌었나» 가 안 읽힌다.
        assertThat(detail).doesNotContain("이름");
        assertThat(detail).doesNotContain("분류");
        assertThat(detail).doesNotContain("옵션");
    }

    @DisplayName("🔴 재고만 바꾼 수정 — 수량은 없고 «어디를 보라» 만 있다")
    @Test
    void productUpdateRecordsStockAsPointerOnly() throws Exception {
        updateProduct(productBody(productName, BASE_PRICE, "SELLING", 99));

        String detail = onlyRow(productId, AuditAction.PRODUCT_UPDATE).getDetail();

        // 🔴 **이 단언 둘이 한 쌍이다.** 수량을 안 적는 것은 stock_history 와 같은 사실을 두 곳에
        //    남기지 않기 위해서고(한쪽만 고쳐지면 어긋난다), 그래도 «일이 있었다» 는 적어야 한다.
        //    ⚠ 2026-08-20 브라우저 검증에서 드러난 자리다 — 재고가 5개 움직인 저장이 «변경 없음» 으로
        //      남아, **정말 아무 일도 없던 저장과 한 글자도 다르지 않았다.**
        assertThat(detail).isEqualTo("재고 바뀜(이력 참조)");
        assertThat(detail).doesNotContain("99");
    }

    @DisplayName("🔴 재고와 다른 값이 함께 바뀌면 둘 다 적힌다 — 재고가 다른 변경을 가리지 않는다")
    @Test
    void productUpdateRecordsStockAlongsideOtherChanges() throws Exception {
        updateProduct(productBody(productName, 8_000L, "SELLING", 42));

        String detail = onlyRow(productId, AuditAction.PRODUCT_UPDATE).getDetail();

        assertThat(detail).contains("판매가 10000→8000");
        assertThat(detail).contains("재고 바뀜(이력 참조)");
    }

    @DisplayName("⚠ 아무것도 안 바뀐 저장도 줄을 남긴다 — «변경 없음»(2026-08-20 결정)")
    @Test
    void productUpdateWithNoChangeStillRecords() throws Exception {
        updateProduct(productBody(productName, BASE_PRICE, "SELLING", 5));

        // 🔴 PRODUCT_DELETE 는 «조용히 통과한 호출» 에 줄을 안 남긴다. 여기는 갈린다 —
        //    거기는 아무 일도 안 일어났고, 여기는 저장까지 실제로 갔다.
        // 🔴 그리고 이 «변경 없음» 은 **정말 아무 일도 없었다는 뜻이어야 한다** —
        //    재고가 움직인 저장과 같은 문자열이면 원장이 둘을 구분해 주지 못한다
        //    (위 productUpdateRecordsStockAsPointerOnly 와 짝이다).
        assertThat(onlyRow(productId, AuditAction.PRODUCT_UPDATE).getDetail()).isEqualTo("변경 없음");
    }

    @DisplayName("긴 필드는 «바뀜» 만 — 본문을 전/후로 실으면 detail(1000자)을 넘긴다")
    @Test
    void longFieldsAreSummarised() throws Exception {
        String longText = "가".repeat(700);
        updateProduct("{\"name\":\"" + productName + "\",\"description\":\"" + longText + "\","
                + "\"price\":" + BASE_PRICE + ",\"status\":\"SELLING\","
                + "\"categoryId\":\"" + categoryId + "\",\"variants\":["
                + "{\"name\":\"기본\",\"priceDelta\":0,\"stock\":5}]}");

        String detail = onlyRow(productId, AuditAction.PRODUCT_UPDATE).getDetail();

        assertThat(detail).isEqualTo("설명 바뀜");
        assertThat(detail.length()).isLessThanOrEqualTo(1000);
    }

    // ── 할인 ────────────────────────────────────────────────

    @DisplayName("🔴 할인 등록 — 대상이 «할인» 이 아니라 «상품» 이다")
    @Test
    void discountCreateTargetsProduct() throws Exception {
        LocalDate start = LocalDate.now(KST).plusDays(30);
        mockMvc.perform(post("/api/admin/products/" + productId + "/discounts").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .content("{\"rate\":20,\"startDate\":\"" + start + "\",\"endDate\":\""
                                + start.plusDays(1) + "\"}"))
                .andExpect(status().isOk());

        AdminAuditLog row = onlyRow(productId, AuditAction.DISCOUNT_CREATE);

        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.PRODUCT);
        // 🔴 이 단언이 설계 결정이다 — 상품 등록 행과 **같은 targetId** 라 한 줄로 훑을 수 있다.
        assertThat(row.getTargetId()).isEqualTo(productId);
        assertThat(row.getDetail()).contains("20%").contains(start.toString());
    }

    @DisplayName("할인 수정 — 전→후. 기간은 관리자가 적은 대로(종료일 포함) 적는다")
    @Test
    void discountUpdateRecordsBeforeAndAfter() throws Exception {
        LocalDate start = LocalDate.now(KST).plusDays(30);
        String res = mockMvc.perform(post("/api/admin/products/" + productId + "/discounts")
                        .contentType(JSON).header(HttpHeaders.AUTHORIZATION, auth)
                        .content("{\"rate\":20,\"startDate\":\"" + start + "\",\"endDate\":\""
                                + start + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String discountId = JsonPath.read(res, "$.data");

        mockMvc.perform(put("/api/admin/products/" + productId + "/discounts/" + discountId)
                        .contentType(JSON).header(HttpHeaders.AUTHORIZATION, auth)
                        .content("{\"rate\":30,\"startDate\":\"" + start + "\",\"endDate\":\""
                                + start + "\"}"))
                .andExpect(status().isOk());

        String detail = onlyRow(productId, AuditAction.DISCOUNT_UPDATE).getDetail();

        assertThat(detail).contains("20%").contains("30%").contains("→");
        // ⚠ 저장된 endsAt 은 배타 경계라 하루 뒤다. 그대로 적으면 관리자가 «내가 적은 날이
        //    아닌데» 라고 읽는다 — 되돌려서 적는지 본다.
        assertThat(detail).doesNotContain(start.plusDays(1).toString());
    }

    @DisplayName("할인 삭제 — 지우기 전에 읽어서 «무엇을 지웠나» 를 남긴다")
    @Test
    void discountDeleteRecordsWhatWasRemoved() throws Exception {
        LocalDate start = LocalDate.now(KST).plusDays(30);
        String res = mockMvc.perform(post("/api/admin/products/" + productId + "/discounts")
                        .contentType(JSON).header(HttpHeaders.AUTHORIZATION, auth)
                        .content("{\"rate\":25,\"startDate\":\"" + start + "\",\"endDate\":\""
                                + start + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String discountId = JsonPath.read(res, "$.data");

        mockMvc.perform(delete("/api/admin/products/" + productId + "/discounts/" + discountId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        // 지운 뒤엔 읽을 행이 없다 — 순서를 뒤집으면 여기가 빈다.
        assertThat(onlyRow(productId, AuditAction.DISCOUNT_DELETE).getDetail()).contains("25%");
    }

    // ── 쿠폰 ────────────────────────────────────────────────

    @DisplayName("쿠폰 등록 — 대상은 쿠폰 정의, detail 에 할인 내용")
    @Test
    void couponCreateIsRecorded() throws Exception {
        UUID couponId = createCoupon("ZZ쿠폰" + suffix);

        AdminAuditLog row = onlyRow(couponId, AuditAction.COUPON_CREATE);

        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.COUPON);
        assertThat(row.getTargetLogin()).isNull();
        // 이름만 적으면 «그때 얼마짜리였나» 를 못 읽는다 — 이름은 바뀔 수 있다.
        assertThat(row.getDetail()).contains("ZZ쿠폰" + suffix).contains("5000원");
    }

    @DisplayName("🔴 관리자 수동 발급 — 여기만 대상이 회원이고 loginId 가 채워진다")
    @Test
    void couponIssueTargetsMember() throws Exception {
        UUID couponId = createCoupon("ZZ발급쿠폰" + suffix);

        mockMvc.perform(post("/api/admin/coupons/" + couponId + "/issue")
                        .param("memberId", memberId.toString())
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        AdminAuditLog row = onlyRow(memberId, AuditAction.COUPON_ISSUE);

        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.MEMBER);
        // 🔴 이게 대상을 회원으로 잡은 이유다 — loginId 로 «이 사람에게 무엇을 줬나» 를 찾을 수 있다.
        assertThat(row.getTargetLogin()).isEqualTo(memberLoginId);
        assertThat(row.getDetail()).contains("ZZ발급쿠폰" + suffix);
    }

    @DisplayName("가입 쿠폰 지정·해제가 각각 남는다")
    @Test
    void welcomeToggleIsRecorded() throws Exception {
        UUID couponId = createCoupon("ZZ가입쿠폰" + suffix);

        mockMvc.perform(post("/api/admin/coupons/" + couponId + "/welcome")
                .header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/coupons/" + couponId + "/welcome")
                .header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk());

        List<String> details = rowsOf(couponId, AuditAction.COUPON_WELCOME_SET).stream()
                .map(AdminAuditLog::getDetail).toList();

        assertThat(details).hasSize(2);
        assertThat(details).anyMatch(d -> d.contains("지정")).anyMatch(d -> d.contains("해제"));
    }

    private UUID createCoupon(String name) throws Exception {
        String res = mockMvc.perform(post("/api/admin/coupons").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .content("{\"name\":\"" + name + "\",\"discountType\":\"FIXED\","
                                + "\"discountValue\":5000,\"validFrom\":\"2026-01-01T00:00:00Z\","
                                + "\"validUntil\":\"2027-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(res, "$.data"));
    }

    // ── 대상 종류 필터 ────────────────────────────────────────

    @DisplayName("🔴 targetType 필터 — 회원 아닌 행을 좁히는 유일한 수단이다(V50 이 «대가» 로 적어 둔 자리)")
    @Test
    void targetTypeNarrowsNonMemberRows() {
        List<AdminAuditLog> productRows = auditLogRepository
                .search(null, AuditTargetType.PRODUCT, null, PageRequest.of(0, 50)).getContent();

        assertThat(productRows).isNotEmpty();
        // 상품 행만 걸린다 — 대상이 회원인 행이 섞이면 필터가 일을 안 한 것이다.
        assertThat(productRows).allMatch(row -> row.getTargetType() == AuditTargetType.PRODUCT);
        assertThat(productRows).anyMatch(row -> row.getTargetId().equals(productId));
    }
}

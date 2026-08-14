package com.glassvue.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.event.StockRunningLowEvent;
import com.glassvue.domain.catalog.entity.StockChangeReason;
import com.glassvue.domain.catalog.entity.StockHistory;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.catalog.repository.StockHistoryRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 변경 이력 (2026-08-04, 백로그 B-19).
 *
 * <p><b>여기서만 드러나는 것</b> — 단위 테스트는 목(mock)이라 옵션이 실제로 교체되는 걸 볼 수 없다:
 * <ol>
 *   <li>🔴 <b>관리자 편집 뒤에도 이력이 이어지는가.</b> {@code ProductCommandService.update()} 는 옵션을
 *       통째로 교체해 <b>모든 옵션 id 를 새로 발급</b>한다. 이력을 {@code variant_id} 로 이었다면 편집
 *       한 번에 과거 줄이 통째로 안 보이게 되는데, <b>그래도 화면은 멀쩡히 뜬다</b>(빈 목록으로).
 *       이 테스트가 그 자리를 고정한다.</li>
 *   <li><b>변동이 0이면 남기지 않는가.</b> 상품명만 고쳐도 옵션은 다시 만들어진다 — 그때마다 줄이
 *       쌓이면 진짜 변동이 묻힌다.</li>
 *   <li><b>원장이 성립하는가</b>({@code SUM(quantity)} = 현재 재고). 옵션 추가·삭제까지 포함해서다.</li>
 *   <li><b>권한과 존재</b>. 없는 상품은 404 여야 한다 — 빈 목록으로 답하면 "이력이 아직 없다"와
 *       "id 가 틀렸다"를 화면이 구분할 수 없다.</li>
 * </ol>
 *
 * <p>⚠ 공유 espdb 를 쓰므로 <b>내가 만든 상품의 이력만</b> 본다(product_id 로 좁힌다) — 절대 건수를
 * 단정하지 않는 다른 catalog 테스트와 같은 판단이지만, 여기서는 조회 자체가 상품 단위라 격리가 자연스럽다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class StockHistoryIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired StockHistoryRepository stockHistoryRepository;
    @Autowired EntityManager entityManager;
    @Autowired ApplicationEvents events;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String suffix;
    private String adminLoginId;
    private String userLoginId;
    private String adminNickname;
    private Category category;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "shadmin_" + suffix;
        userLoginId = "shuser_" + suffix;
        adminNickname = "ZZ재고이력관리자" + suffix;
        member(adminLoginId, adminNickname, Role.ADMIN);
        member(userLoginId, "ZZ재고이력일반" + suffix, Role.USER);
        category = categoryRepository.save(Category.builder().name("ZZC-재고이력" + suffix).build());
    }

    private void member(String loginId, String nickname, Role role) {
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW)).nickname(nickname).role(role).build());
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** {@code {"name":"검정","priceDelta":0,"stock":10}} 한 줄. */
    private String variantJson(String name, long stock) {
        return "{\"name\":\"" + name + "\",\"priceDelta\":0,\"stock\":" + stock + "}";
    }

    private String productJson(String name, String... variants) {
        return "{\"name\":\"" + name + "\",\"description\":\"재고 이력 테스트\",\"price\":10000,"
                + "\"status\":\"SELLING\",\"categoryId\":\"" + category.getId() + "\","
                + "\"variants\":[" + String.join(",", variants) + "]}";
    }

    /** 상품을 <b>API 로</b> 만든다 — 행위자(AuthUser)가 실제로 흘러가는지 보려면 서비스 직접 호출로는 부족하다. */
    private UUID createProduct(String admin, String label, String... variants) throws Exception {
        String body = mockMvc.perform(post("/api/products").header("Authorization", admin)
                        .contentType(JSON).content(productJson("ZZP-재고이력" + label + suffix, variants)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        entityManager.flush();
        return UUID.fromString(JsonPath.read(body, "$.data"));
    }

    private void updateProduct(String admin, UUID productId, String name, String... variants) throws Exception {
        mockMvc.perform(put("/api/products/" + productId).header("Authorization", admin)
                        .contentType(JSON).content(productJson(name, variants)))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear(); // 옵션이 통째로 교체됐다 — 1차 캐시의 옛 옵션을 보면 안 된다
    }

    /** 이 상품의 이력(최신순). 공유 DB 라 상품으로 좁혀야 단정할 수 있다. */
    private List<StockHistory> history(UUID productId) {
        return stockHistoryRepository
                .findByProductIdOrderByCreatedAtDesc(productId, PageRequest.of(0, 50))
                .getContent();
    }

    private long currentStock(UUID productId) {
        return variantRepository.sumStockByProduct(productId);
    }

    // ── 등록 ────────────────────────────────────────────────────

    @Test
    @DisplayName("상품 등록: 옵션의 초기 재고가 이력 첫 줄로 남는다(행위자 = 로그인한 관리자)")
    void create_recordsInitialStock() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "등록", variantJson("검정", 10));

        List<StockHistory> rows = history(productId);
        assertThat(rows).hasSize(1);
        StockHistory h = rows.get(0);
        assertThat(h.getReason()).isEqualTo(StockChangeReason.ADMIN_CREATE);
        assertThat(h.getQuantity()).isEqualTo(10);
        assertThat(h.getStockAfter()).isEqualTo(10);
        assertThat(h.getVariantName()).isEqualTo("검정");
        // 관리자 조작이므로 행위자가 있고 주문은 없다(주문 경로와 정반대).
        assertThat(h.getActorName()).isEqualTo(adminNickname);
        assertThat(h.getOrderId()).isNull();
    }

    @Test
    @DisplayName("상품 등록: 재고 0 인 옵션은 이력을 남기지 않는다(변동이 없다)")
    void create_zeroStock_noHistory() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "등록0", variantJson("검정", 0));

        assertThat(history(productId)).isEmpty();
        // 그래도 원장은 성립한다 — 합계 0 = 재고 0.
        assertThat(currentStock(productId)).isZero();
    }

    // ── 편집 ────────────────────────────────────────────────────

    @Test
    @DisplayName("편집: 재고가 달라진 만큼만 **차이**로 남는다(절대값이 아니라 delta)")
    void update_recordsDelta() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "편집", variantJson("검정", 10));

        updateProduct(admin, productId, "ZZP-재고이력편집" + suffix, variantJson("검정", 25));

        List<StockHistory> rows = history(productId);
        assertThat(rows).hasSize(2);
        StockHistory latest = rows.get(0);
        assertThat(latest.getReason()).isEqualTo(StockChangeReason.ADMIN_EDIT);
        assertThat(latest.getQuantity()).as("10 → 25 이므로 +15 여야 한다(25 가 아니다)").isEqualTo(15);
        assertThat(latest.getStockAfter()).isEqualTo(25);
        assertThat(latest.getActorName()).isEqualTo(adminNickname);
    }

    @Test
    @DisplayName("🔴 편집으로 옵션 id 가 바뀌어도 **과거 이력이 그대로 이어진다**")
    void update_keepsHistoryAcrossVariantIdChange() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "승계", variantJson("검정", 10));
        UUID idBefore = variantRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(productId)
                .get(0).getId();

        updateProduct(admin, productId, "ZZP-재고이력승계" + suffix, variantJson("검정", 4));

        UUID idAfter = variantRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(productId)
                .get(0).getId();
        // 전제부터 고정한다 — 이 테스트가 지키는 것은 "id 가 바뀐다"는 사실 위에 서 있다.
        assertThat(idAfter).as("옵션은 통째로 교체되므로 id 가 바뀌어야 한다").isNotEqualTo(idBefore);

        List<StockHistory> rows = history(productId);
        assertThat(rows).as("등록 1줄 + 편집 1줄이 **둘 다** 보여야 한다").hasSize(2);
        assertThat(rows).extracting(StockHistory::getVariantName).containsOnly("검정");
        // 옛 줄은 이제 없는 옵션을 가리킨다 — 그래도 이름으로 이어지므로 조회에서 빠지지 않는다.
        assertThat(rows).extracting(StockHistory::getVariantId).contains(idBefore);
    }

    @Test
    @DisplayName("편집: 재고를 안 바꾸면(상품명만 수정) 이력이 늘지 않는다")
    void update_withoutStockChange_noHistory() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "무변동", variantJson("검정", 10));
        int before = history(productId).size();

        updateProduct(admin, productId, "ZZP-재고이력무변동수정" + suffix, variantJson("검정", 10));

        assertThat(history(productId)).as("옵션은 다시 만들어졌지만 재고는 그대로다").hasSize(before);
    }

    @Test
    @DisplayName("편집: 옵션을 지우면 그 재고만큼 **감소**로 남고 변동 후 재고는 0")
    void update_removedVariant_recordsDecrease() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "삭제",
                variantJson("검정", 10), variantJson("흰색", 7));

        updateProduct(admin, productId, "ZZP-재고이력삭제" + suffix, variantJson("검정", 10));

        StockHistory removed = history(productId).stream()
                .filter(h -> "흰색".equals(h.getVariantName()) && h.getReason() == StockChangeReason.ADMIN_EDIT)
                .findFirst().orElseThrow();
        assertThat(removed.getQuantity()).isEqualTo(-7);
        assertThat(removed.getStockAfter()).isZero();
        // 가리킬 옵션이 사라졌으므로 id 는 비어 있다 — 그래도 이름은 남아 이력이 읽힌다.
        assertThat(removed.getVariantId()).isNull();
    }

    @Test
    @DisplayName("편집: 옵션을 새로 추가하면 그 재고만큼 **증가**로 남는다")
    void update_addedVariant_recordsIncrease() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "추가", variantJson("검정", 10));

        updateProduct(admin, productId, "ZZP-재고이력추가" + suffix,
                variantJson("검정", 10), variantJson("흰색", 3));

        StockHistory added = history(productId).stream()
                .filter(h -> "흰색".equals(h.getVariantName())).findFirst().orElseThrow();
        assertThat(added.getReason()).isEqualTo(StockChangeReason.ADMIN_EDIT);
        assertThat(added.getQuantity()).isEqualTo(3);
        assertThat(added.getStockAfter()).isEqualTo(3);
    }

    @Test
    @DisplayName("⚠ 원장의 성질 — 추가·삭제·수정을 거쳐도 **이력 합계 = 현재 재고**")
    void ledgerSumEqualsCurrentStock() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "원장",
                variantJson("검정", 10), variantJson("흰색", 7));
        // 검정 10→3, 흰색 삭제, 파랑 5 추가
        updateProduct(admin, productId, "ZZP-재고이력원장" + suffix,
                variantJson("검정", 3), variantJson("파랑", 5));

        long sum = history(productId).stream().mapToLong(StockHistory::getQuantity).sum();
        assertThat(sum).as("합계가 현재 재고와 다르면 원장이 아니라 그냥 로그다")
                .isEqualTo(currentStock(productId))
                .isEqualTo(8); // 3 + 5
    }

    // ── 주문 경로 ────────────────────────────────────────────────
    //
    // 관리자 경로는 위에서 다 봤다. 여기서만 드러나는 것은 **order_id 가 실제로 실리는지**다 —
    // 차감을 주문 생성 뒤로 옮긴 이유가 그것이고, 목(mock) 테스트로는 DB 왕복을 못 본다.

    /** 담기 → 주문. 반환: orderId. */
    private String checkout(String buyer, UUID variantId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                .content("{\"variantId\":\"" + variantId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 1\",\"address2\":null}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        entityManager.flush();
        entityManager.clear();
        return JsonPath.read(body, "$.data");
    }

    @Test
    @DisplayName("주문: 차감이 **음수**로 남고 그 주문 id 가 실린다(행위자는 비운다)")
    void order_recordsWithOrderId() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "주문", variantJson("검정", 10));
        UUID variantId = variantRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(productId)
                .get(0).getId();

        String orderId = checkout(login(userLoginId), variantId, 3);

        StockHistory ordered = history(productId).get(0); // 최신순 — 등록(+10) 위에 주문(−3)
        assertThat(ordered.getReason()).isEqualTo(StockChangeReason.ORDER);
        assertThat(ordered.getQuantity()).isEqualTo(-3);
        assertThat(ordered.getStockAfter()).isEqualTo(7);
        assertThat(ordered.getOrderId()).as("주문 경로의 근거는 order_id 다").isEqualTo(UUID.fromString(orderId));
        assertThat(ordered.getActorName()).isNull();
        assertThat(currentStock(productId)).isEqualTo(7);
    }

    @Test
    @DisplayName("주문 취소: 복원이 **양수 · CANCEL** 로 남고 원장 합계가 다시 맞는다")
    void cancel_recordsRestore() throws Exception {
        String admin = login(adminLoginId);
        String buyer = login(userLoginId);
        UUID productId = createProduct(admin, "취소", variantJson("검정", 10));
        UUID variantId = variantRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(productId)
                .get(0).getId();
        String orderId = checkout(buyer, variantId, 3);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        List<StockHistory> rows = history(productId);
        assertThat(rows).hasSize(3); // 등록 +10, 주문 −3, 취소 +3
        StockHistory restored = rows.get(0);
        assertThat(restored.getReason()).isEqualTo(StockChangeReason.CANCEL);
        assertThat(restored.getQuantity()).isEqualTo(3);
        assertThat(restored.getStockAfter()).isEqualTo(10);
        assertThat(restored.getOrderId()).isEqualTo(UUID.fromString(orderId));
        assertThat(rows.stream().mapToLong(StockHistory::getQuantity).sum())
                .isEqualTo(currentStock(productId)).isEqualTo(10);
    }

    // ── 조회 API ────────────────────────────────────────────────

    @Test
    @DisplayName("조회: 관리자는 이 상품의 이력을 최신순으로 받는다")
    void api_returnsHistory() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "조회", variantJson("검정", 10));
        updateProduct(admin, productId, "ZZP-재고이력조회" + suffix, variantJson("검정", 12));

        mockMvc.perform(get("/api/admin/products/" + productId + "/stock-history")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                // 최신순이라 편집(+2)이 위, 등록(+10)이 아래다.
                .andExpect(jsonPath("$.data.content[0].reason").value("ADMIN_EDIT"))
                .andExpect(jsonPath("$.data.content[0].quantity").value(2))
                .andExpect(jsonPath("$.data.content[0].stockAfter").value(12))
                .andExpect(jsonPath("$.data.content[0].actorName").value(adminNickname))
                .andExpect(jsonPath("$.data.content[1].reason").value("ADMIN_CREATE"));
    }

    @Test
    @DisplayName("⚠ 조회: 페이징의 totalElements 는 **count 가 실제로 도는 조건**에서 본다(size < 전체)")
    void api_paginates() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "페이징", variantJson("검정", 10));
        updateProduct(admin, productId, "ZZP-재고이력페이징1" + suffix, variantJson("검정", 12));
        updateProduct(admin, productId, "ZZP-재고이력페이징2" + suffix, variantJson("검정", 20));

        mockMvc.perform(get("/api/admin/products/" + productId + "/stock-history?size=2")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.last").value(false));
    }

    @Test
    @DisplayName("조회: 없는 상품은 **404** — 빈 목록으로 답하면 오타와 '이력 없음'을 구분 못 한다")
    void api_unknownProduct_404() throws Exception {
        mockMvc.perform(get("/api/admin/products/" + UUID.randomUUID() + "/stock-history")
                        .header("Authorization", login(adminLoginId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── 재고 부족 알림 — 편집 경로 (2026-08-14, BACKLOG F-8) ──────
    //
    // 🔴 **주문 경로와 규칙이 일부러 다르다**: 주문은 「상태」(차감마다 임계 이하면 발행),
    //    편집은 「전이」(임계 위 → 이하로 넘어갈 때만). 관리자는 자기가 그 값을 입력한 사람이라
    //    저장할 때마다 오면 알림이 아니라 소음이다. 아래 넷이 그 규칙의 경계를 못 박는다.

    /** 이번 테스트에서 발행된 재고 부족 이벤트(옵션명만). */
    private List<String> lowStockEvents() {
        return events.stream(StockRunningLowEvent.class).map(StockRunningLowEvent::variantName).toList();
    }

    @Test
    @DisplayName("🔴 전이: 임계 **위 → 이하**로 내리면 알린다 (10 → 3, 임계 5)")
    void edit_crossesIntoLow_publishes() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "전이", variantJson("검정", 10));

        updateProduct(admin, productId, "ZZP-재고이력전이" + suffix, variantJson("검정", 3));

        assertThat(lowStockEvents()).containsExactly("검정");
    }

    @Test
    @DisplayName("🔴 이미 임계 이하면 **또 내려도 안 알린다** — 여기가 「상태」와 갈리는 자리다 (3 → 2)")
    void edit_alreadyLow_doesNotPublish() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "이미낮음", variantJson("검정", 3));

        updateProduct(admin, productId, "ZZP-재고이력이미낮음" + suffix, variantJson("검정", 2));

        // 주문 경로였다면 여기서 한 건 나갔다. 편집은 이미 넘어와 있던 것이라 새 소식이 아니다.
        assertThat(lowStockEvents()).isEmpty();
    }

    @Test
    @DisplayName("⚠ 새로 만든 옵션은 안 알린다 — 비교할 이전 상태가 없어 「넘어갔다」가 성립 안 한다")
    void edit_newVariant_doesNotPublish() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "새옵션", variantJson("검정", 10));

        updateProduct(admin, productId, "ZZP-재고이력새옵션" + suffix,
                variantJson("검정", 10), variantJson("흰색", 1));

        assertThat(lowStockEvents()).isEmpty();
    }

    @Test
    @DisplayName("🔴 **사라진 옵션은 안 알린다** — 지운 것을 「재고 부족」으로 알리면 채울 대상이 없다")
    void edit_removedVariant_doesNotPublish() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "옵션삭제", variantJson("검정", 10), variantJson("흰색", 10));

        // 「흰색」을 뺀다. ⚠ 재고 **이력**은 이것을 «10 → 0» 감소로 남긴다(update_removedVariant_recordsDecrease).
        //    같은 셈을 알림에 쓰면 0 은 임계 이하라 「흰색 재고 부족」이 나간다 — 그 옵션은 이제 없는데도.
        updateProduct(admin, productId, "ZZP-재고이력옵션삭제" + suffix, variantJson("검정", 10));

        assertThat(lowStockEvents()).isEmpty();
    }

    @Test
    @DisplayName("권한: 일반 회원 403 · 비로그인 401 (재고는 운영 정보다)")
    void api_requiresAdmin() throws Exception {
        String admin = login(adminLoginId);
        UUID productId = createProduct(admin, "권한", variantJson("검정", 10));
        String url = "/api/admin/products/" + productId + "/stock-history";

        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(url).header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
        // ⚠ 401·403 은 **엔드포인트가 있다는 증거가 아니다**(WA §3) — 매처가 없는 경로도 막힌다.
        //    그래서 같은 URL 이 관리자에겐 실제로 200 을 주는지 여기서 함께 못 박는다.
        mockMvc.perform(get(url).header("Authorization", admin)).andExpect(status().isOk());
    }
}

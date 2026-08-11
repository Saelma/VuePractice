package com.glassvue.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.point.repository.PointAccountRepository;
import com.glassvue.domain.point.repository.PointHistoryRepository;
import com.glassvue.domain.point.service.PointService;
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
 * 반품 · 적립금 환불 (2026-07-24, 백로그 C-9).
 *
 * <p>여기서만 드러나는 것:
 * <ol>
 *   <li><b>포인트 파밍 불가</b> — 사서 적립받고 반품해도 순이득 0(환불은 적립을 회수하고 준다).</li>
 *   <li><b>잔액 == 이력 합</b> — 환불도 이력이 원장이라는 규칙을 지켜야 한다.</li>
 *   <li><b>옵션 재고 복원</b> — 반품 승인 시 그 옵션 재고가 돌아온다.</li>
 *   <li><b>상태 가드</b> — 배송완료만 요청, 요청만 승인, 남의 주문 불가, 관리자만 승인.</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderReturnIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired PointAccountRepository accountRepository;
    @Autowired PointHistoryRepository historyRepository;
    @Autowired PointService pointService;
    @Autowired EntityManager entityManager;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String buyerLoginId;
    private String adminLoginId;
    private UUID buyerId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buyerLoginId = "ret_" + suffix;
        adminLoginId = "retadmin_" + suffix;
        buyerId = memberRepository.save(Member.builder().loginId(buyerLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ반품구매자" + suffix).role(Role.USER).build()).getId();
        memberRepository.save(Member.builder().loginId(adminLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ반품관리자" + suffix).role(Role.ADMIN).build());
        pointService.openAccount(buyerId);

        Category cat = categoryRepository.save(Category.builder().name("ZZC-반품" + suffix).build());
        UUID productId = productRepository.save(Product.builder()
                .name("ZZP-반품상품" + suffix).description("d").price(50_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        variantId = variantRepository.save(ProductVariant.of(productId, "기본", 0, 100, 0)).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 담기 → 주문 → 배송완료까지. 반환: orderId. */
    private String orderDelivered(String buyer, String admin, int quantity, Long usePoint) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                .content("{\"variantId\":\"" + variantId + "\",\"quantity\":" + quantity + "}")).andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 1\",\"address2\":null"
                                + (usePoint == null ? "" : ",\"usePoint\":" + usePoint) + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.data");
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", buyer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/ship").header("Authorization", admin).contentType(JSON)
                .content("{\"carrier\":\"CJ\",\"trackingNo\":\"ZZ123\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + orderId + "/deliver").header("Authorization", admin)).andExpect(status().isOk());
        return orderId;
    }

    private void requestReturn(String buyer, String orderId, String reason) throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/return-request").header("Authorization", buyer)
                .contentType(JSON).content("{\"reason\":\"" + reason + "\"}")).andExpect(status().isOk());
    }

    private long balance() {
        entityManager.flush();
        entityManager.clear();
        return accountRepository.findByMemberId(buyerId).orElseThrow().getBalance();
    }

    private void assertLedgerConsistent() {
        assertThat(balance()).as("잔액 == 이력 합")
                .isEqualTo(historyRepository.sumAmountByMemberId(buyerId));
    }

    @Test
    @DisplayName("반품 승인 → 옵션 재고 복원 + 상품금액을 적립금으로 환불")
    void approve_restoresStockAndRefunds() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        String orderId = orderDelivered(buyer, admin, 1, null); // 50,000 · 배송완료 시 500 적립

        long balanceBefore = balance(); // 500
        requestReturn(buyer, orderId, "단순 변심");
        mockMvc.perform(post("/api/orders/" + orderId + "/return-approve").header("Authorization", admin))
                .andExpect(status().isOk());

        // 환불 50,000 − 적립회수 500 = 순 +49,500 → 잔액 500 + 49,500 = 50,000
        assertThat(balance()).isEqualTo(balanceBefore + 50_000 - 500);
        // 재고 복원: 100 - 1(주문) + 1(반품) = 100
        entityManager.clear();
        assertThat(variantRepository.findById(variantId).orElseThrow().getStock()).isEqualTo(100);
        assertLedgerConsistent();

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.returnReason").value("단순 변심"));
    }

    @Test
    @DisplayName("⚠ 포인트 파밍 불가 — 사서 적립받고 반품해도 순이득 0")
    void noPointFarming() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);

        long start = balance(); // 0
        String orderId = orderDelivered(buyer, admin, 1, null); // +500 적립
        assertThat(balance()).isEqualTo(start + 500);

        requestReturn(buyer, orderId, "변심");
        mockMvc.perform(post("/api/orders/" + orderId + "/return-approve").header("Authorization", admin))
                .andExpect(status().isOk());

        // 잔액 = 시작 + 환불(50,000). ⚠ 핵심: **50,500 이 아니다.**
        //   적립 500을 위로 쌓았다면 50,500(파밍 성공)이었을 것이다. 환불이 적립을 회수하므로
        //   500은 50,000 환불 안에 흡수돼 사라진다 — 보상으로 얻은 순이득은 0이다.
        assertThat(balance()).as("환불액과 같아야 한다(적립 500이 위로 안 쌓임)").isEqualTo(start + 50_000);
        assertLedgerConsistent();
    }

    @Test
    @DisplayName("사용했던 적립금도 환불에 포함된다 (환불 = 상품합계 − 쿠폰, 사용분 무관)")
    void refundIncludesUsedPoints() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        // 먼저 적립금을 만든다: 한 건 배송완료(+500)
        orderDelivered(buyer, admin, 1, null);
        long afterFirst = balance(); // 500

        // 두 번째 주문에서 500 사용 → 결제 49,500, 배송완료 시 적립은 49,500의 1% = 495
        String orderId = orderDelivered(buyer, admin, 1, 500L);
        long afterSecond = balance(); // 500 - 500(사용) + 495(적립) = 495

        requestReturn(buyer, orderId, "변심");
        mockMvc.perform(post("/api/orders/" + orderId + "/return-approve").header("Authorization", admin))
                .andExpect(status().isOk());

        // 환불 = 상품합계 50,000 − 쿠폰 0 = 50,000 (사용했던 500 포인트도 이 안에 포함).
        // 적립회수 = 495. 순 +49,505. 잔액 = 495 + 49,505 = 50,000.
        assertThat(balance()).isEqualTo(afterSecond + 50_000 - 495);
        assertLedgerConsistent();
    }

    @Test
    @DisplayName("반품 거절 → 배송완료로 되돌아가고 재고·적립은 그대로 · **거절 사유가 고객에게 보인다**")
    void reject_revertsToDelivered() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        String orderId = orderDelivered(buyer, admin, 1, null);
        long before = balance();

        requestReturn(buyer, orderId, "변심");
        rejectReturn(admin, orderId, "ZZ-사용 흔적이 있습니다");

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                // 🔴 거절은 상태를 안 남긴다 — 이 둘이 없으면 고객 화면에서 반품 이야기가 통째로 사라진다
                //    (2026-08-11: 거절 알림이 «주문 상세에서 확인하세요» 라 했는데 상세가 비어 있었다).
                .andExpect(jsonPath("$.data.returnRejectedReason").value("ZZ-사용 흔적이 있습니다"))
                .andExpect(jsonPath("$.data.returnRejectedAt").exists())
                // ⚠ 요청 시각도 남아야 «언제 요청해서 언제 거절됐나» 가 읽힌다(예전엔 NULL 로 지웠다).
                .andExpect(jsonPath("$.data.returnRequestedAt").exists());
        assertThat(balance()).isEqualTo(before); // 환불 없음
        entityManager.clear();
        assertThat(variantRepository.findById(variantId).orElseThrow().getStock()).isEqualTo(99); // 복원 안 됨
    }

    /**
     * ⚠ 사유는 <b>필수</b>다 — 빈 값으로 거절하면 «거절이 있었다» 를 나타낼 것이 아무것도 안 남는다.
     * DTO 검증이 실제 요청에서도 걸리는지는 여기서만 확인된다(단위 테스트는 DTO 를 안 거친다).
     */
    @Test
    @DisplayName("⚠ 거절 사유 없이 보내면 400 — 사유가 「거절이 있었다」의 유일한 표시라 비울 수 없다")
    void reject_requiresReason() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        String orderId = orderDelivered(buyer, admin, 1, null);
        requestReturn(buyer, orderId, "변심");

        mockMvc.perform(post("/api/orders/" + orderId + "/return-reject")
                        .header("Authorization", admin).contentType(JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());

        // 거절이 안 됐으니 여전히 승인 대기여야 한다 — 400 이 «막았다» 를 뜻하는지 확인한다.
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.status").value("RETURN_REQUESTED"));
    }

    private void rejectReturn(String admin, String orderId, String reason) throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/return-reject")
                        .header("Authorization", admin).contentType(JSON)
                        .content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("배송완료가 아닌 주문은 반품 요청 불가 — ORDER-400R")
    void onlyDeliveredCanRequest() throws Exception {
        String buyer = login(buyerLoginId);
        // 결제만 하고 배송 전
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                .content("{\"variantId\":\"" + variantId + "\",\"quantity\":1}")).andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울 1\",\"address2\":null}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.data");

        mockMvc.perform(post("/api/orders/" + orderId + "/return-request").header("Authorization", buyer)
                        .contentType(JSON).content("{\"reason\":\"변심\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORDER-400R"));
    }

    @Test
    @DisplayName("반품 승인은 관리자만 — 일반 회원 403, 미인증 401")
    void approveIsAdminOnly() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        String orderId = orderDelivered(buyer, admin, 1, null);
        requestReturn(buyer, orderId, "변심");

        mockMvc.perform(post("/api/orders/" + orderId + "/return-approve"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/" + orderId + "/return-approve").header("Authorization", buyer))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("요청되지 않은 반품은 승인할 수 없다 — ORDER-400RP")
    void cannotApproveNotRequested() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        String orderId = orderDelivered(buyer, admin, 1, null); // DELIVERED, 요청 안 함

        mockMvc.perform(post("/api/orders/" + orderId + "/return-approve").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORDER-400RP"));
    }

    @Test
    @DisplayName("큰 구매를 반품하면 등급이 강등될 수 있다")
    void returnCanDemoteGrade() throws Exception {
        String buyer = login(buyerLoginId);
        String admin = login(adminLoginId);
        // 150,000 구매확정 → 누적 150,000 → SILVER
        String orderId = orderDelivered(buyer, admin, 3, null);
        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.grade").value("SILVER"));

        requestReturn(buyer, orderId, "변심");
        mockMvc.perform(post("/api/orders/" + orderId + "/return-approve").header("Authorization", admin))
                .andExpect(status().isOk());

        // 누적에서 150,000 빠짐 → BRONZE 로 강등
        mockMvc.perform(get("/api/points/me").header("Authorization", buyer))
                .andExpect(jsonPath("$.data.grade").value("BRONZE"))
                .andExpect(jsonPath("$.data.totalPurchase").value(0));
        assertLedgerConsistent();
    }
}

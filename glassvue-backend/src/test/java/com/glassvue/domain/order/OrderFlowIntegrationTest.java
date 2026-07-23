package com.glassvue.domain.order;

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
 * 이커머스 전체 플로우 통합 — 실 컨텍스트 + MockMvc.
 * 장바구니 담기 → 결제(checkout) → 주문 조회 → 결제(pay) → 관리자 발송(ship) → 배송완료(deliver)까지 관통.
 * 회원(구매자·관리자)·상품을 리포지토리로 만들고 @Transactional 롤백 → 자체 완결·공유 DB 무오염.
 * (장바구니는 Redis지만 checkout이 비우므로 잔여 최소.)
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String buyerLoginId;
    private String adminLoginId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        buyerLoginId = "buyer_" + UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "admin_" + UUID.randomUUID().toString().substring(0, 8);
        member(buyerLoginId, "구매자", Role.USER);
        member(adminLoginId, "판매자", Role.ADMIN);
        Category cat = categoryRepository.save(Category.builder().name("ZZC-오더").build());
        Product p = productRepository.save(Product.builder()
                .name("ZZP-테스트상품").description("d").price(10_000).stock(100)
                .status(ProductStatus.SELLING).category(cat).build());
        productId = p.getId();
    }

    private void member(String loginId, String nickname, Role role) {
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW)).nickname(nickname).role(role).build());
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    @DisplayName("장바구니→결제→주문조회→결제완료→발송(관리자) 전체 관통")
    void fullOrderFlow() throws Exception {
        String buyer = "Bearer " + login(buyerLoginId);
        String admin = "Bearer " + login(adminLoginId);

        // 1) 장바구니 담기
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":2}"))
                .andExpect(status().isOk());

        // 2) 장바구니 조회 — 구매가능·합계
        mockMvc.perform(get("/api/cart").header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.totalPrice").value(20_000))
                .andExpect(jsonPath("$.data.items[0].available").value(true));

        // 3) 결제(checkout) → 주문 생성
        //    배송지는 본문으로 받는다(V11). 품목·가격은 서버가 장바구니에서 읽으므로 본문에 없다.
        String coBody = mockMvc.perform(post("/api/orders").header("Authorization", buyer)
                        .contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\","
                                + "\"zipcode\":\"06134\",\"address1\":\"서울시 강남구 테헤란로 1\","
                                + "\"address2\":\"3층\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(coBody, "$.data");

        // 4) 주문 조회 — ORDERED + 스냅샷
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ORDERED"))
                .andExpect(jsonPath("$.data.totalPrice").value(20_000))
                // memberId는 화면이 "내 주문인가"를 판단해 결제·취소 버튼을 띄우는 근거다.
                // 빠지면 버튼이 조용히 사라지므로(2026-07-20 실제 발생) 응답 계약으로 고정한다.
                .andExpect(jsonPath("$.data.memberId").isNotEmpty())
                // buyerNickname은 관리자가 목록→상세로 들어와도 구매자를 잃지 않게 하는 스냅샷(V5).
                // 상세 응답 계약으로 고정한다(빠지면 관리자 동선에 구멍이 생긴다).
                .andExpect(jsonPath("$.data.buyerNickname").value("구매자"))
                // 배송지도 주문 시점 스냅샷(V11) — 회원이 나중에 기본 배송지를 바꿔도 과거 주문은
                // "그때 보낸 곳"이어야 CS·배송 이력이 맞는다. buyerNickname과 같은 이유로 계약 고정.
                .andExpect(jsonPath("$.data.shipRecipient").value("ZZ수령인"))
                .andExpect(jsonPath("$.data.shipPhone").value("010-0000-0000"))
                .andExpect(jsonPath("$.data.shipZipcode").value("06134"))
                .andExpect(jsonPath("$.data.shipAddress1").value("서울시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.data.shipAddress2").value("3층"))
                .andExpect(jsonPath("$.data.items[0].productName").value("ZZP-테스트상품"));

        // 5) 구매자는 발송 불가(관리자 전용) → 403
        mockMvc.perform(post("/api/orders/" + orderId + "/ship").header("Authorization", buyer))
                .andExpect(status().isForbidden());

        // 6) 결제 완료(구매자) → PAID
        mockMvc.perform(post("/api/orders/" + orderId + "/pay").header("Authorization", buyer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.status").value("PAID"));

        // 7) 발송(관리자) → SHIPPED. 운송장(택배사·송장번호)이 필수다(V13).
        //    운송장 없이 발송하면 고객이 추적할 수 없고 나중에 채워 넣을 경로도 없어서, 본문을 요구한다.
        mockMvc.perform(post("/api/orders/" + orderId + "/ship").header("Authorization", admin)
                        .contentType(JSON)
                        .content("{\"carrier\":\"CJ\",\"trackingNo\":\"123456789012\"}"))
                .andExpect(status().isOk());

        // 8) 발송 후 조회 — 상태 + 운송장 스냅샷이 응답 계약에 실린다.
        //    trackingUrl은 서버가 택배사별 형식으로 완성해 준다(화면이 택배사 지식을 갖지 않게).
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.shipCarrier").value("CJ"))
                .andExpect(jsonPath("$.data.shipCarrierName").value("CJ대한통운"))
                .andExpect(jsonPath("$.data.shipTrackingNo").value("123456789012"))
                .andExpect(jsonPath("$.data.trackingUrl").value(
                        "https://trace.cjlogistics.co.kr/next/tracking.html?wblNo=123456789012"));

        // 9) 배송완료(관리자) → DELIVERED + 수령 시각 기록
        mockMvc.perform(post("/api/orders/" + orderId + "/deliver").header("Authorization", admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty());

        // 10) 이미 배송완료된 주문은 다시 배송완료할 수 없다 → 400
        mockMvc.perform(post("/api/orders/" + orderId + "/deliver").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORDER-400D"));
    }

    @Test
    @DisplayName("발송 처리에 운송장이 없으면 400 — 추적 불가한 발송을 막는다")
    void ship_requiresTrackingInfo() throws Exception {
        String admin = "Bearer " + login(adminLoginId);

        // 존재하지 않는 주문이라도 본문 검증이 먼저 걸린다(@Valid는 서비스 진입 전에 동작).
        // 즉 "주문을 못 찾음(404)"이 아니라 "본문이 잘못됨(400)"이어야 한다.
        String someId = UUID.randomUUID().toString();

        // 본문 자체가 없음 → 400 (GlobalExceptionHandler의 HttpMessageNotReadable 처리)
        mockMvc.perform(post("/api/orders/" + someId + "/ship").header("Authorization", admin))
                .andExpect(status().isBadRequest());

        // 송장번호 누락 → 400
        mockMvc.perform(post("/api/orders/" + someId + "/ship").header("Authorization", admin)
                        .contentType(JSON).content("{\"carrier\":\"CJ\"}"))
                .andExpect(status().isBadRequest());

        // 택배사 누락 → 400
        mockMvc.perform(post("/api/orders/" + someId + "/ship").header("Authorization", admin)
                        .contentType(JSON).content("{\"trackingNo\":\"123\"}"))
                .andExpect(status().isBadRequest());

        // 없는 택배사 → 400 (DB CHECK 대신 enum이 검증한다 — DeliveryCarrier 참고)
        mockMvc.perform(post("/api/orders/" + someId + "/ship").header("Authorization", admin)
                        .contentType(JSON).content("{\"carrier\":\"없는택배\",\"trackingNo\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("배송완료 처리 권한 — 비로그인 401 / 일반 사용자 403")
    void deliver_requiresAdmin() throws Exception {
        String buyer = "Bearer " + login(buyerLoginId);
        String someId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/orders/" + someId + "/deliver"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/orders/" + someId + "/deliver").header("Authorization", buyer))
                .andExpect(status().isForbidden());
    }
}

package com.glassvue.domain.order;

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
 * 주문 취소 사유 (2026-08-04, 백로그 B-17).
 *
 * <p><b>여기서만 드러나는 것</b> — 엔티티 테스트는 {@code cancel(reason)} 만 보고, 이 기능의 위험은
 * 전부 <b>HTTP 계약</b>에 있다:
 * <ol>
 *   <li>🔴 <b>본문 없이도 취소되는가.</b> 사유는 선택이라 기존 호출(본문 없는 POST)이 그대로 돌아야 한다.
 *       {@code @RequestBody} 의 {@code required = false} 를 빠뜨리면 <b>기존 취소가 전부 400</b> 이 되는데,
 *       단위 테스트는 컨트롤러를 안 지나가서 <b>한 건도 안 빨개진다.</b></li>
 *   <li><b>사유가 응답으로 되돌아오는가</b> — 저장만 되고 {@code OrderResponse} 에 안 실리면
 *       화면이 그릴 수가 없다("API 가 준다 ≠ 화면이 보여준다" 의 한 칸 앞이다).</li>
 *   <li><b>길이 상한이 DB 와 맞는가</b> — DTO 가 더 헐거우면 검증을 통과한 값이
 *       <b>ORA-12899 로 취소 자체를 실패</b>시킨다(B-20 에서 같은 자리를 짚었다). 500자 초과는 <b>400</b>.</li>
 *   <li><b>공백이 NULL 로 눕는가</b> — 왕복해서 {@code null} 로 와야 화면이 빈 줄을 안 그린다.</li>
 * </ol>
 *
 * <p>⚠ 재고 복원·상태 전이는 여기서 다시 보지 않는다 — {@code OrderServiceTest} 와
 * {@code StockHistoryIntegrationTest} 가 이미 덮는다. <b>이 파일은 사유에만 집중한다.</b>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CancelReasonIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String buyer;
    private UUID variantId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String loginId = "cxl_" + suffix;
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname("ZZ취소구매자" + suffix).role(Role.USER).build());

        Category cat = categoryRepository.save(Category.builder().name("ZZC-취소" + suffix).build());
        UUID productId = productRepository.save(Product.builder()
                .name("ZZP-취소상품" + suffix).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        variantId = variantRepository.save(ProductVariant.of(productId, "기본", 0, 100, 0)).getId();

        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        buyer = "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 장바구니 담기 → 주문. 반환: orderId. */
    private String order() throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"quantity\":1}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 테헤란로 1\",\"address2\":null}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data");
    }

    private void assertCancelReason(String orderId, Object expected) throws Exception {
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value(expected));
    }

    @Test
    @DisplayName("사유와 함께 취소하면 **응답으로 되돌아온다**(저장만 하면 화면이 못 그린다)")
    void cancel_withReason() throws Exception {
        String orderId = order();

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer)
                        .contentType(JSON).content("{\"reason\":\"배송이 늦어서\"}"))
                .andExpect(status().isOk());

        assertCancelReason(orderId, "배송이 늦어서");
    }

    @Test
    @DisplayName("🔴 **본문 없이** 취소해도 200 이다 — 사유는 선택이고 기존 호출이 그대로 돌아야 한다")
    void cancel_withoutBody() throws Exception {
        String orderId = order();

        // ⚠ contentType 도 붙이지 않는다 — 기존 프론트가 보내던 그대로다.
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer))
                .andExpect(status().isOk());

        assertCancelReason(orderId, null);
    }

    @Test
    @DisplayName("사유가 공백뿐이면 **null** 로 온다(빈 칸을 그리지 않게)")
    void cancel_blankReason() throws Exception {
        String orderId = order();

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer)
                        .contentType(JSON).content("{\"reason\":\"   \"}"))
                .andExpect(status().isOk());

        assertCancelReason(orderId, null);
    }

    @Test
    @DisplayName("사유가 500자를 넘으면 **400** — DB 컬럼(VARCHAR2(500 CHAR))과 상한이 같아야 한다")
    void cancel_tooLongReason() throws Exception {
        String orderId = order();

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer)
                        .contentType(JSON).content("{\"reason\":\"" + "가".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // 막혔으므로 주문은 그대로다 — 400 을 내고도 취소돼 있으면 최악이다.
        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.status").value("ORDERED"));
    }

    @Test
    @DisplayName("경계: 정확히 500자는 통과한다(한글도 500 **자**여야 한다 — 바이트가 아니다)")
    void cancel_exactly500() throws Exception {
        String orderId = order();
        String reason = "가".repeat(500);

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", buyer)
                        .contentType(JSON).content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk());

        // 여기가 CHAR semantics 를 실제로 재는 자리다 — BYTE 로 만들어졌다면 ORA-12899 로 터진다.
        assertCancelReason(orderId, reason);
    }
}

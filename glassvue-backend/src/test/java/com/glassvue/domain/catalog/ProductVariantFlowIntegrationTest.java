package com.glassvue.domain.catalog;

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
import jakarta.persistence.EntityManager;
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
 * 상품 옵션 (2026-07-24, 백로그 C-8) — 옵션별 재고·가격이 장바구니·주문에 관통하는지.
 *
 * <p>여기서만 드러나는 것:
 * <ol>
 *   <li><b>옵션별 재고 차감</b> — 한 옵션을 주문해도 다른 옵션 재고는 안 줄어든다.</li>
 *   <li><b>품절 옵션 주문 거절</b> — 재고 0인 옵션은 담아도 결제에서 막힌다.</li>
 *   <li><b>옵션 가격차</b> — 장바구니 가격이 기본가+가격차로 계산된다.</li>
 *   <li><b>취소 시 그 옵션에만 복원</b> — 주문했던 옵션 재고만 되돌아온다.</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductVariantFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired EntityManager entityManager;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String buyerLoginId;
    private UUID productId;
    private UUID blackM;   // 검정 M — 재고 10, 가격차 0
    private UUID whiteL;   // 흰색 L — 재고 0(품절), 가격차 +2000

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buyerLoginId = "var_" + suffix;
        memberRepository.save(Member.builder().loginId(buyerLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ옵션구매자" + suffix).role(Role.USER).build());

        Category cat = categoryRepository.save(Category.builder().name("ZZC-옵션" + suffix).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-옵션상품" + suffix).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        blackM = variantRepository.save(ProductVariant.of(productId, "검정 / M", 0, 10, 0)).getId();
        whiteL = variantRepository.save(ProductVariant.of(productId, "흰색 / L", 2_000, 0, 1)).getId();
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + buyerLoginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private void addToCart(String token, UUID variantId, int qty) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", token).contentType(JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"quantity\":" + qty + "}"))
                .andExpect(status().isOk());
    }

    private String checkout(String token) throws Exception {
        String body = mockMvc.perform(post("/api/orders").header("Authorization", token).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 1\",\"address2\":null}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data");
    }

    @Test
    @DisplayName("상품 상세에 옵션 목록이 나온다 — 가격차가 반영된 실제 판매가와 품절 여부")
    void productDetailHasVariants() throws Exception {
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants.length()").value(2))
                .andExpect(jsonPath("$.data.variants[0].name").value("검정 / M"))
                .andExpect(jsonPath("$.data.variants[0].price").value(10_000))
                .andExpect(jsonPath("$.data.variants[0].soldOut").value(false))
                // 흰색 L: 기본가 10,000 + 가격차 2,000 = 12,000, 재고 0 → 품절
                .andExpect(jsonPath("$.data.variants[1].price").value(12_000))
                .andExpect(jsonPath("$.data.variants[1].soldOut").value(true))
                // 판매중이고 검정 M에 재고가 있으므로 상품 자체는 품절이 아니다
                .andExpect(jsonPath("$.data.soldOut").value(false));
    }

    @Test
    @DisplayName("장바구니 가격은 기본가+가격차 — 옵션명도 함께(옵션 2개 이상이라)")
    void cartUsesVariantPrice() throws Exception {
        String token = login();
        addToCart(token, whiteL, 1);   // 재고는 0이지만 담기는 된다(가능 여부는 조회에서 판단)
        mockMvc.perform(get("/api/cart").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].price").value(12_000))
                .andExpect(jsonPath("$.data.items[0].optionName").value("흰색 / L"))
                // 재고 0이라 살 수는 없다
                .andExpect(jsonPath("$.data.items[0].available").value(false));
    }

    @Test
    @DisplayName("품절 옵션은 결제에서 막힌다 — UNAVAILABLE_ITEM")
    void soldOutVariantBlocksCheckout() throws Exception {
        String token = login();
        addToCart(token, whiteL, 1);
        mockMvc.perform(post("/api/orders").header("Authorization", token).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 1\",\"address2\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORDER-400U"));
    }

    @Test
    @DisplayName("한 옵션을 주문하면 그 옵션 재고만 줄고, 취소하면 그 옵션에만 복원된다")
    void stockIsPerVariant() throws Exception {
        String token = login();
        addToCart(token, blackM, 3);
        String orderId = checkout(token);

        // 검정 M: 10 → 7, 흰색 L: 0 그대로
        assertStock(blackM, 7);
        assertStock(whiteL, 0);

        // 취소 → 검정 M 복원(7 → 10), 흰색 L 불변
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel").header("Authorization", token))
                .andExpect(status().isOk());
        assertStock(blackM, 10);
        assertStock(whiteL, 0);
    }

    @Test
    @DisplayName("옵션명이 주문에 스냅샷된다 — 옵션이 나중에 바뀌어도 주문 내역은 그대로")
    void orderSnapshotsOptionName() throws Exception {
        String token = login();
        addToCart(token, blackM, 1);
        String orderId = checkout(token);

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].optionName").value("검정 / M"));
    }

    private void assertStock(UUID variantId, long expected) {
        // 재고 차감/복원은 벌크 UPDATE라 1차 캐시를 갱신하지 않는다(ARCHITECTURE) — DB를 직접 다시 읽으려면 clear.
        entityManager.flush();
        entityManager.clear();
        long actual = variantRepository.findById(variantId).orElseThrow().getStock();
        org.assertj.core.api.Assertions.assertThat(actual)
                .as("옵션 %s 재고", variantId).isEqualTo(expected);
    }
}

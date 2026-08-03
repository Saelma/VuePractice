package com.glassvue.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
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
 * ⚠ <b>장바구니에 담은 뒤 재고가 빠진 경우</b> (2026-08-03, 사용자 지적으로 확인).
 *
 * <p>기존 테스트(`ProductVariantFlowIntegrationTest.soldOutVariantBlocksCheckout`)는
 * <b>담을 때 이미 재고가 0</b>인 경우를 본다. 여기서 보는 것은 <b>담을 땐 있었고 그 뒤에 없어진</b>
 * 경우다 — 장바구니가 Redis 에 {@code variantId → 수량} 만 들고 있어서, <b>담은 시점의 재고는
 * 어디에도 저장되지 않는다.</b> 즉 판정은 매번 <b>조회 시점</b>에 다시 이뤄져야 한다.
 *
 * <p>실사용에서 훨씬 흔한 경로다 — 장바구니에 며칠 담아 두는 게 정상이고, 그 사이 재고는 바뀐다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StaleCartStockIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired EntityManager entityManager;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String ORDER_BODY =
            "{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                    + "\"address1\":\"서울시 강남구 테헤란로 1\",\"address2\":null}";

    private String token;
    private UUID variantId;
    private UUID productId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String loginId = "stale_" + suffix;
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname("ZZ재고변동" + suffix).role(Role.USER).build());

        Category cat = categoryRepository.save(Category.builder().name("ZZC-재고변동" + suffix).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-재고변동" + suffix).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        // ⚠ 담을 때는 재고가 **있다**. 여기가 기존 테스트와 갈리는 지점이다.
        variantId = variantRepository.save(ProductVariant.of(productId, "기본", 0, 5, 0)).getId();

        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        token = "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private void addToCart(long qty) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", token).contentType(JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"quantity\":" + qty + "}"))
                .andExpect(status().isOk());
    }

    /** 담은 뒤 재고를 바꾼다 — 다른 사람이 사 갔거나 관리자가 줄인 상황. */
    private void setStock(long stock) {
        entityManager.createNativeQuery("UPDATE product_variant SET stock = ?1 WHERE id = ?2")
                .setParameter(1, stock)
                .setParameter(2, uuidToBytes(variantId))
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private static byte[] uuidToBytes(UUID uuid) {
        return java.nio.ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits()).array();
    }

    /**
     * ⚠ <b>읽기 전에 영속성 컨텍스트를 비운다.</b> {@code decreaseStock} 은 <b>벌크 UPDATE</b> 라
     * 1차 캐시를 갱신하지 않는데, 결제 과정에서 {@code getCart} 가 이 옵션을 이미 로딩해 뒀다.
     * 안 비우면 <b>차감 전 값</b>을 읽어 "재고가 안 줄었다" 는 <b>가짜 실패</b>가 난다
     * (2026-08-03에 실제로 겪었다 — `ProductVariantRepository` javadoc 이 경고한 그 자리).
     */
    private long currentStock() {
        entityManager.flush();
        entityManager.clear();
        return variantRepository.findById(variantId).orElseThrow().getStock();
    }

    @Test
    @DisplayName("⚠ 담을 땐 재고가 있었는데 **결제 시점에 0** 이면 주문이 막힌다")
    void stockGoneAfterAddingToCart() throws Exception {
        addToCart(1);
        setStock(0);   // 그 사이 품절

        mockMvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(JSON).content(ORDER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORDER-400U"));

        assertThat(currentStock()).as("막혔으면 재고가 음수로 내려가면 안 된다").isZero();
    }

    @Test
    @DisplayName("⚠ 재고가 **주문 수량보다 적어진** 경우도 막힌다 (0 이 아니어도)")
    void stockReducedBelowQuantity() throws Exception {
        addToCart(3);
        setStock(2);   // 3개 담았는데 2개만 남음

        mockMvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(JSON).content(ORDER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORDER-400U"));

        assertThat(currentStock()).as("부분 차감도 일어나면 안 된다").isEqualTo(2);
    }

    @Test
    @DisplayName("재고가 주문 수량과 **정확히 같으면** 주문된다 (경계)")
    void stockExactlyEqualsQuantitySucceeds() throws Exception {
        addToCart(3);
        setStock(3);

        mockMvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(JSON).content(ORDER_BODY))
                .andExpect(status().isCreated());

        assertThat(currentStock()).as("전부 차감돼 0 이 된다").isZero();
    }

    @Test
    @DisplayName("⚠ 담은 뒤 상품이 **판매중지(HIDDEN)** 되면 막힌다 — 재고가 남아 있어도")
    void hiddenProductBlocksCheckout() throws Exception {
        addToCart(1);
        entityManager.createNativeQuery("UPDATE product SET status = 'HIDDEN' WHERE id = ?1")
                .setParameter(1, uuidToBytes(productId)).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/orders").header("Authorization", token)
                        .contentType(JSON).content(ORDER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORDER-400U"));

        assertThat(currentStock()).as("재고는 그대로여야 한다").isEqualTo(5);
    }
}

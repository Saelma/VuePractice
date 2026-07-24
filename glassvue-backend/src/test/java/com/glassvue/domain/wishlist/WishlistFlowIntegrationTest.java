package com.glassvue.domain.wishlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * 위시리스트(찜) — 추가 → 목록 → 해제 + 멱등성 + 소유 경계.
 *
 * <p>여기서만 드러나는 것: ① {@code /api/wishlist/**} 매처를 빠뜨리면 <b>남의 찜이 인증 없이 열린다</b>
 * (SecurityConfig 기본이 permitAll — 2026-07-23 쿠폰에서 겪은 자리라 401을 계약으로 고정한다),
 * ② 추가·해제의 <b>멱등성</b>(더블클릭·재시도에서 에러가 나지 않아야 한다).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WishlistFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PW = "password123";
    private static final String URL = "/api/wishlist";

    private String meLoginId;
    private String otherLoginId;
    private UUID productId;
    private UUID otherProductId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        meLoginId = "wish_" + suffix;
        otherLoginId = "wisho_" + suffix;
        member(meLoginId, "ZZ찜유저" + suffix);
        member(otherLoginId, "ZZ찜타인" + suffix);

        Category category = categoryRepository.save(Category.builder().name("ZZC-찜" + suffix).build());
        productId = product(category, "ZZP-찜상품" + suffix, 10_000L, 5L);
        otherProductId = product(category, "ZZP-찜상품2" + suffix, 20_000L, 3L);
    }

    private void member(String loginId, String nickname) {
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(Role.USER).build());
    }

    private UUID product(Category category, String name, long price, long stock) {
        return productRepository.save(Product.builder()
                .name(name).description("찜 테스트").price(price).stock(stock)
                .status(ProductStatus.SELLING).category(category).build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    @DisplayName("찜 추가 → 목록에 상품 정보가 합성되어 나온다 (가격·재고는 지금 값)")
    void addAndList() throws Exception {
        String token = login(meLoginId);

        mockMvc.perform(post(URL + "/" + productId).header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.data[0].price").value(10_000))
                // 판매중 + 재고 5 → 지금 살 수 있다
                .andExpect(jsonPath("$.data[0].available").value(true))
                .andExpect(jsonPath("$.data[0].addedAt").exists());
    }

    @Test
    @DisplayName("같은 상품을 두 번 찜해도 성공하고 목록은 한 줄 (멱등 — 더블클릭 대비)")
    void addIsIdempotent() throws Exception {
        String token = login(meLoginId);

        mockMvc.perform(post(URL + "/" + productId).header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL + "/" + productId).header("Authorization", token))
                .andExpect(status().isOk());   // 409가 아니라 성공이어야 한다

        mockMvc.perform(get(URL).header("Authorization", token))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("찜한 적 없는 상품을 해제해도 성공한다 (멱등 — 목적은 '없는 상태'다)")
    void removeIsIdempotent() throws Exception {
        String token = login(meLoginId);
        mockMvc.perform(delete(URL + "/" + productId).header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("해제하면 목록에서 빠진다 — 다른 찜은 그대로")
    void remove() throws Exception {
        String token = login(meLoginId);
        mockMvc.perform(post(URL + "/" + productId).header("Authorization", token));
        mockMvc.perform(post(URL + "/" + otherProductId).header("Authorization", token));

        mockMvc.perform(delete(URL + "/" + productId).header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header("Authorization", token))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(otherProductId.toString()));
    }

    @Test
    @DisplayName("찜한 상품 id 목록 — 화면이 하트를 채우는 근거")
    void productIds() throws Exception {
        String token = login(meLoginId);
        mockMvc.perform(post(URL + "/" + productId).header("Authorization", token));

        mockMvc.perform(get(URL + "/product-ids").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value(productId.toString()));
    }

    @Test
    @DisplayName("찜은 회원별로 격리된다 — 남의 찜이 내 목록에 안 보인다")
    void isolatedPerMember() throws Exception {
        String me = login(meLoginId);
        String other = login(otherLoginId);

        mockMvc.perform(post(URL + "/" + productId).header("Authorization", me))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header("Authorization", other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("없는 상품은 찜할 수 없다 → PRODUCT-404")
    void addUnknownProduct() throws Exception {
        mockMvc.perform(post(URL + "/" + UUID.randomUUID()).header("Authorization", login(meLoginId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT-404"));
    }

    @Test
    @DisplayName("인증 없이 찜에 접근하면 401 — SecurityConfig 기본이 permitAll이라 매처가 있어야 한다")
    void requiresAuth() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/product-ids")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL + "/" + productId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(URL + "/" + productId)).andExpect(status().isUnauthorized());
    }
}

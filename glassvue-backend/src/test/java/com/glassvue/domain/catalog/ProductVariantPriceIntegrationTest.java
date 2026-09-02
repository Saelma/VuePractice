package com.glassvue.domain.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.repository.CategoryRepository;
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
 * 🔴 <b>옵션 판매가(기본가 + 가격차)는 0원보다 커야 한다</b> (2026-09-02, BACKLOG §L-1).
 *
 * <p><b>{@code ProductListPriceIntegrationTest} 와 같은 계열이고, 같은 방식으로 발견됐다</b> —
 * 그쪽은 *"API 로 부르면 정가 0원이 그대로 저장됐다"* 였고, 이쪽은
 * <b>«가드가 값을 고쳐 주고 있는 자리» 를 세다가</b> 나왔다(§L 축).
 *
 * <p>🔴 <b>가드가 문제를 «정상» 으로 바꿔 주고 있었다.</b> {@code priceDelta} 는 음수를 허용한다
 * (할인 옵션 — 의도된 결정이고 DTO 주석이 그렇게 적어 뒀다). 그런데 <b>「기본가 + 가격차」의 하한을
 * 아무도 안 봤고</b>, {@code ProductVariant.effectivePrice} 의 {@code Math.max(0L, …)} 이
 * 그것을 <b>0원 판매가로 접었다.</b> 예외가 아니라 <b>«0원짜리 상품»</b> 이 만들어졌고,
 * 그 값이 {@code VariantResponse.price} → 장바구니 → 주문 스냅샷까지 <b>그대로 흘렀다.</b>
 *
 * <p>⚠ <b>이쪽이 정가 건보다 나쁘다</b>: 저쪽은 화면이 뜻 없는 취소선을 그릴 뿐이지만
 * 이쪽은 <b>상품이 0원에 팔린다.</b>
 *
 * <p>⚠ <b>0 도 막는다</b>(«0보다 커야 한다»). 0원 상품을 팔 이유가 지금 없고, 허용하면
 * 「무료 증정」과 「입력 실수」를 <b>구분할 방법이 사라진다</b> — 필요해지면 별도 개념으로 연다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductVariantPriceIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String adminLoginId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "vp_" + suffix;
        memberRepository.save(Member.builder().loginId(adminLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ옵션가관리자" + suffix)
                .role(Role.ADMIN).build());
        categoryId = categoryRepository.save(Category.builder().name("ZZC-옵션가" + suffix).build()).getId();
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + adminLoginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 판매가 10,000 고정. 옵션 하나에 {@code priceDelta} 만 갈아 끼운다. */
    private String body(long... priceDeltas) {
        StringBuilder vs = new StringBuilder();
        for (int i = 0; i < priceDeltas.length; i++) {
            vs.append(i == 0 ? "" : ",")
              .append("{\"name\":\"옵션").append(i).append("\",\"priceDelta\":")
              .append(priceDeltas[i]).append(",\"stock\":5}");
        }
        return "{\"name\":\"ZZP-옵션가상품\",\"description\":\"설명\",\"price\":10000,"
                + "\"status\":\"SELLING\",\"categoryId\":\"" + categoryId + "\","
                + "\"variants\":[" + vs + "]}";
    }

    @Test
    @DisplayName("음수 가격차는 **막지 않는다** — 「할인 옵션」은 의도된 기능이다")
    void negativeDeltaIsStillAllowed() throws Exception {
        String token = login();
        String res = mockMvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(JSON).content(body(-3_000)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        // 🔴 이 테스트가 이 항목의 **경계**다 — 규칙을 「음수 금지」로 만들면 여기가 빨개진다.
        mockMvc.perform(get("/api/products/" + JsonPath.read(res, "$.data")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.variants[0].price").value(7_000));
    }

    @Test
    @DisplayName("🔴 기본가를 다 깎아 0원이 되면 400 — 예전엔 **0원짜리 상품이 만들어졌다**")
    void deltaThatZeroesThePriceIsRejected() throws Exception {
        String token = login();
        mockMvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(JSON).content(body(-10_000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PRODUCT-400P"));
    }

    @Test
    @DisplayName("🔴 기본가보다 더 깎아도 400 — `Math.max` 가 접어서 «0원» 으로 보이던 자리")
    void deltaBelowBasePriceIsRejected() throws Exception {
        String token = login();
        mockMvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(JSON).content(body(-999_999)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PRODUCT-400P"));
    }

    @Test
    @DisplayName("⚠ 옵션이 여럿이면 **한 줄만 틀려도** 400 — 나머지가 멀쩡해도 통과시키지 않는다")
    void oneBadVariantRejectsTheWholeRequest() throws Exception {
        String token = login();
        mockMvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(JSON).content(body(0, 2_000, -10_000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PRODUCT-400P"));
    }

    @Test
    @DisplayName("🔴 수정도 같은 규칙을 지킨다 — 만들 땐 막고 고칠 땐 통과가 되지 않게")
    void updateObeysTheSameRule() throws Exception {
        String token = login();
        String res = mockMvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(JSON).content(body(0)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.data");

        mockMvc.perform(put("/api/products/" + id).header("Authorization", token)
                        .contentType(JSON).content(body(-10_000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PRODUCT-400P"));

        // ⚠ 막힌 뒤에도 상품은 원래 값 그대로여야 한다 — 반쯤 저장되면 더 나쁘다.
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(jsonPath("$.data.variants[0].price").value(10_000));
    }
}

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
 * 배송 요청사항 (2026-08-03, 백로그 B-20).
 *
 * <p>여기서만 드러나는 것 셋:
 * <ol>
 *   <li><b>주문 스냅샷인가</b> — 요청사항은 배송지와 같은 성격이라 <b>주문에 박혀야</b> 한다.
 *       회원 쪽에 뒀다면 나중에 바꿀 때 과거 주문의 요청까지 바뀐다.</li>
 *   <li><b>선택인가</b> — 대부분의 주문엔 요청사항이 없다. 안 보내도 주문이 되어야 하고
 *       그때 값은 {@code null} 이어야 한다(빈 문자열이 아니라).</li>
 *   <li><b>길이 상한이 DB 와 맞는가</b> — DTO 가 더 헐거우면 <b>ORA-12899 로 주문 자체가 실패</b>한다
 *       (V12 닉네임 스냅샷 사고와 같은 자리). 200자를 넘기면 <b>400</b>으로 막혀야 한다.</li>
 * </ol>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShipMemoIntegrationTest {

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
        String loginId = "memo_" + suffix;
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname("ZZ메모구매자" + suffix).role(Role.USER).build());

        Category cat = categoryRepository.save(Category.builder().name("ZZC-메모" + suffix).build());
        UUID productId = productRepository.save(Product.builder()
                .name("ZZP-메모상품" + suffix).description("d").price(10_000)
                .status(ProductStatus.SELLING).category(cat).build()).getId();
        variantId = variantRepository.save(ProductVariant.of(productId, "기본", 0, 100, 0)).getId();

        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        buyer = "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 장바구니 담기 → 주문. {@code memoJson} 은 `,"shipMemo":"…"` 형태이거나 빈 문자열. */
    private String order(String memoJson) throws Exception {
        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"quantity\":1}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 테헤란로 1\",\"address2\":null" + memoJson + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data");
    }

    @Test
    @DisplayName("요청사항을 남기면 주문 상세에 그대로 보인다")
    void memoIsSavedAndReturned() throws Exception {
        String orderId = order(",\"shipMemo\":\"부재 시 경비실에 맡겨 주세요\"");

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipMemo").value("부재 시 경비실에 맡겨 주세요"));
    }

    @Test
    @DisplayName("요청사항은 선택 — 안 보내도 주문되고 값은 null 이다")
    void memoIsOptional() throws Exception {
        String orderId = order("");

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(status().isOk())
                // ⚠ 빈 문자열이 아니라 null 이어야 화면이 "요청사항 줄" 자체를 안 그린다.
                .andExpect(jsonPath("$.data.shipMemo").doesNotExist());
    }

    @Test
    @DisplayName("⚠ 200자를 넘기면 **400** — DTO 상한이 DB 컬럼보다 헐거우면 ORA-12899 로 주문이 통째로 실패한다")
    void rejectsTooLongMemo() throws Exception {
        String tooLong = "가".repeat(201);

        mockMvc.perform(post("/api/cart/items").header("Authorization", buyer).contentType(JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"quantity\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders").header("Authorization", buyer).contentType(JSON)
                        .content("{\"recipient\":\"ZZ수령인\",\"phone\":\"010-0000-0000\",\"zipcode\":\"06134\","
                                + "\"address1\":\"서울시 강남구 테헤란로 1\",\"address2\":null,"
                                + "\"shipMemo\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("⚠ 한글 200자는 통과한다 — VARCHAR2(200 CHAR) 라 바이트가 아니라 글자로 센다")
    void acceptsExactly200KoreanChars() throws Exception {
        // CHAR 를 빠뜨렸다면 한글 200자 = 600바이트라 ORA-12899(500)로 터진다(WA §2-2-1).
        String orderId = order(",\"shipMemo\":\"" + "가".repeat(200) + "\"");

        mockMvc.perform(get("/api/orders/" + orderId).header("Authorization", buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipMemo").value("가".repeat(200)));
    }

    @Test
    @DisplayName("⚠ 요청사항은 **주문 스냅샷**이다 — 주문마다 따로 남는다")
    void memoIsPerOrderSnapshot() throws Exception {
        String first = order(",\"shipMemo\":\"첫 주문 요청\"");
        String second = order(",\"shipMemo\":\"둘째 주문 요청\"");

        String firstMemo = JsonPath.read(mockMvc.perform(
                        get("/api/orders/" + first).header("Authorization", buyer))
                .andReturn().getResponse().getContentAsString(), "$.data.shipMemo");

        assertThat(firstMemo)
                .as("나중 주문의 요청사항이 앞선 주문을 덮어쓰면 안 된다").isEqualTo("첫 주문 요청");
        mockMvc.perform(get("/api/orders/" + second).header("Authorization", buyer))
                .andExpect(jsonPath("$.data.shipMemo").value("둘째 주문 요청"));
    }
}

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
 * 정가(`list_price`)는 <b>판매가보다 커야 한다</b> — 2026-08-13, 사용자 신고에서 나왔다.
 *
 * <p>🔴 <b>이 규칙은 그전까지 화면에만 있었다.</b> DTO 는 {@code @PositiveOrZero} 뿐이고 서비스에
 * 비교가 없어서, <b>API 로 부르면 정가 0원·정가 &lt; 판매가가 그대로 저장됐다.</b>
 * 그러면 상세·목록이 <b>할인이 아닌데 취소선을 그린다</b> — 화면은 멀쩡히 도는데 값이 거짓말을 한다.
 *
 * <p>⚠ 발견 경위가 뒤집혀 있다: 사용자가 신고한 것은 «정가 칸을 지울 수 없다» 는 <b>화면 버그</b>였고
 * (DevExtreme 이 빈 칸을 {@code min=0} 으로 되돌려 «저장도 비우기도 안 되는» 상태가 됐다),
 * 그걸 보다가 <b>그 0 을 서버가 아무 말 없이 받는다</b> 는 걸 알았다.
 * → 화면은 지울 수 있게 고치고(`show-clear-button`), <b>규칙은 서버로 내렸다.</b>
 *
 * <p>여기서 고정하는 계약 넷:
 * <ul>
 *   <li>정가를 <b>비우면 통과</b>한다 — 그게 «할인 없음» 의 표현이다.</li>
 *   <li>정가 &gt; 판매가면 통과한다(정상 할인).</li>
 *   <li>정가 == 판매가, 정가 &lt; 판매가, <b>정가 0</b> 은 400.</li>
 *   <li><b>수정도 같다</b> — 등록에만 걸면 «만들 때는 막히고 고칠 때는 통과» 가 된다.</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductListPriceIntegrationTest {

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
        adminLoginId = "lp_" + suffix;
        memberRepository.save(Member.builder().loginId(adminLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ정가관리자" + suffix)
                .role(Role.ADMIN).build());
        categoryId = categoryRepository.save(Category.builder().name("ZZC-정가" + suffix).build()).getId();
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + adminLoginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 판매가는 10,000 고정. {@code listPrice} 가 null 이면 그 줄을 아예 뺀다(관리 화면이 보내는 모양). */
    private String body(Long listPrice) {
        String lp = (listPrice == null) ? "" : "\"listPrice\":" + listPrice + ",";
        return "{\"name\":\"ZZP-정가상품\",\"description\":\"설명\",\"price\":10000,"
                + lp + "\"status\":\"SELLING\",\"categoryId\":\"" + categoryId + "\","
                + "\"variants\":[{\"name\":\"기본\",\"priceDelta\":0,\"stock\":5}]}";
    }

    @Test
    @DisplayName("정가를 비우면 통과한다 — 그게 「할인 없음」이다")
    void emptyListPriceIsAllowed() throws Exception {
        String token = login();
        String res = mockMvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(JSON).content(body(null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/products/" + JsonPath.read(res, "$.data")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.listPrice").doesNotExist());
    }

    @Test
    @DisplayName("정가가 판매가보다 크면 통과한다 — 정상 할인")
    void higherListPriceIsAllowed() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", login())
                        .contentType(JSON).content(body(13_000L)))
                .andExpect(status().isCreated());
    }

    /**
     * 🔴 <b>0 이 이 버그의 입구였다.</b> 화면이 «비움» 을 0 으로 되돌려 보냈고 서버는 받아 줬다.
     * ⚠ 정가 0원은 <b>어떤 판매가에도</b> 유효할 수 없다 — 판매가가 0이어도 «0보다 커야» 를 못 채운다.
     */
    @Test
    @DisplayName("정가 0 · 판매가와 같음 · 판매가보다 작음 — 셋 다 400 (PRODUCT-400L)")
    void invalidListPriceRejected() throws Exception {
        String token = login();
        for (long invalid : new long[]{0L, 10_000L, 9_999L}) {
            mockMvc.perform(post("/api/products").header("Authorization", token)
                            .contentType(JSON).content(body(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("PRODUCT-400L"));
        }
    }

    /**
     * ⚠ <b>수정에도 같은 규칙이 걸려야 한다.</b> 등록에만 걸면 «만들 때는 막히고 고칠 때는 통과» 가
     * 되어 규칙이 반쪽이 된다 — 그 반쪽은 없느니만 못하다(어긋난 값이 결국 들어온다).
     */
    @Test
    @DisplayName("수정도 같은 규칙을 지킨다 — 만들 땐 막고 고칠 땐 통과가 되지 않게")
    void updateIsGuardedToo() throws Exception {
        String token = login();
        String res = mockMvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(JSON).content(body(13_000L)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.data");

        mockMvc.perform(put("/api/products/" + id).header("Authorization", token)
                        .contentType(JSON).content(body(9_000L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PRODUCT-400L"));

        // 대조군 — 비우는 수정은 통과해야 한다(할인을 없애는 정상 경로).
        mockMvc.perform(put("/api/products/" + id).header("Authorization", token)
                        .contentType(JSON).content(body(null)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/products/" + id))
                .andExpect(jsonPath("$.data.listPrice").doesNotExist());
    }
}

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
 * 상품 한 줄 카피(tagline, V33 · 2026-07-29) — 목록 카드에 얹는 짧은 설명.
 *
 * <p>여기서 고정하는 계약은 셋이다:
 * <ul>
 *   <li><b>선택 필드다</b> — 안 보내도 생성된다(기존 상품은 전부 null 이고 그 상태가 정상이다).</li>
 *   <li><b>길이 상한 100</b> — DDL 이 {@code VARCHAR2(100 CHAR)} 라, 검증이 없으면 DB 가
 *       ORA-12899 로 터진다(=500). 400 으로 돌려주는 게 계약이다.</li>
 *   <li><b>비우면 지워진다</b> — 수정에서 null 을 보내면 다시 "없음"이 된다. 관리 화면이 빈 입력을
 *       null 로 보내므로(빈 문자열이면 카드에 빈 줄이 생긴다) 그 경로가 실제로 지워지는지 본다.</li>
 * </ul>
 *
 * <p>⚠ 한글로 상한을 시험한다. 이 DB 의 {@code NLS_LENGTH_SEMANTICS} 는 BYTE 라
 * {@code CHAR} 를 빠뜨렸다면 한글 100자가 300바이트가 돼 DB 에서 터진다(WA §2-2-1 의 V5·V9 사고).
 * 즉 이 테스트는 <b>DDL 의 CHAR semantics 까지</b> 함께 지킨다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductTaglineIntegrationTest {

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
        adminLoginId = "tag_" + suffix;
        memberRepository.save(Member.builder().loginId(adminLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ태그관리자" + suffix)
                .role(Role.ADMIN).build());
        categoryId = categoryRepository.save(Category.builder().name("ZZC-태그" + suffix).build()).getId();
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + adminLoginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /**
     * tagline 이 null 이면 그 줄을 아예 뺀 요청(= V33 이전 관리 화면이 보내던 모양)을 만든다.
     *
     * <p>⚠ {@code status} 를 항상 넣는다 — 생성에선 선택이지만 <b>수정에선 {@code @NotNull}</b> 이다
     * (같은 필드가 두 DTO 에서 다르게 검증된다). 빼고 PUT 하면 400 이 난다.
     */
    private String body(String tagline) {
        String taglineJson = tagline == null ? "" : "\"tagline\":\"" + tagline + "\",";
        return "{\"name\":\"ZZP-태그상품\"," + taglineJson
                + "\"description\":\"설명\",\"price\":10000,\"status\":\"SELLING\","
                + "\"categoryId\":\"" + categoryId + "\","
                + "\"variants\":[{\"name\":\"기본\",\"priceDelta\":0,\"stock\":5}]}";
    }

    private String create(String token, String tagline) throws Exception {
        String res = mockMvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(JSON).content(body(tagline)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.data");
    }

    @Test
    @DisplayName("생성 시 한 줄 카피를 담으면 상세·목록 응답에 실린다")
    void createWithTagline() throws Exception {
        String token = login();
        String id = create(token, "하루의 끝에 편안하게");

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tagline").value("하루의 끝에 편안하게"));

        // 목록에도 실려야 한다 — 카드가 읽는 건 목록 응답이다(상세만 되면 화면엔 안 보인다).
        mockMvc.perform(get("/api/products").param("name", "ZZP-태그상품"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].tagline").value("하루의 끝에 편안하게"));
    }

    @Test
    @DisplayName("한 줄 카피는 선택 — 안 보내면 null 로 생성된다(기존 상품과 같은 상태)")
    void taglineIsOptional() throws Exception {
        String id = create(login(), null);

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tagline").doesNotExist());
    }

    @Test
    @DisplayName("100자 초과 → 400 (DB 가 ORA-12899 로 터지기 전에 막는다)")
    void taglineTooLong() throws Exception {
        String tooLong = "가".repeat(101);
        mockMvc.perform(post("/api/products").header("Authorization", login())
                        .contentType(JSON).content(body(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("한글 100자는 통과 — VARCHAR2(100 CHAR) 라 바이트가 아니라 글자 수다")
    void taglineExactlyHundredKorean() throws Exception {
        String id = create(login(), "가".repeat(100));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tagline").value("가".repeat(100)));
    }

    @Test
    @DisplayName("수정에서 null 을 보내면 지워진다(관리 화면의 빈 입력 경로)")
    void clearTagline() throws Exception {
        String token = login();
        String id = create(token, "지워질 카피");

        mockMvc.perform(put("/api/products/" + id).header("Authorization", token)
                        .contentType(JSON).content(body(null)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tagline").doesNotExist());
    }
}

package com.glassvue.domain.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * 배송지 주소록 (2026-07-24, V18) — 추가 → 기본 전환 → 삭제 승계 + 소유 경계.
 *
 * <p>여기서 확인하는 것 중 <b>단위 테스트로는 절대 안 잡히는 것</b>이 둘 있다:
 * ① 기본 배송지가 회원당 하나라는 보장(DB 함수 기반 유니크 인덱스 + 서비스의 flush 순서),
 * ② 남의 주소에 대한 응답이 403이 아니라 404라는 것(실제 요청을 보내야 드러난다, §2-4).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberAddressFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String URL = "/api/members/me/addresses";

    private String meLoginId;
    private String otherLoginId;

    @BeforeEach
    void setUp() {
        meLoginId = "addr_" + UUID.randomUUID().toString().substring(0, 8);
        otherLoginId = "addro_" + UUID.randomUUID().toString().substring(0, 8);
        member(meLoginId, "ZZ주소유저");
        member(otherLoginId, "ZZ주소타인");
    }

    private void member(String loginId, String nickname) {
        memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname + UUID.randomUUID().toString().substring(0, 4)).role(Role.USER).build());
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private String json(String alias, String recipient, boolean setDefault) {
        return "{\"alias\":\"" + alias + "\",\"recipient\":\"" + recipient + "\","
                + "\"phone\":\"010-1234-5678\",\"zipcode\":\"06134\","
                + "\"address1\":\"서울시 강남구 테헤란로 1\",\"address2\":\"3층\","
                + "\"setDefault\":" + setDefault + "}";
    }

    private String add(String token, String alias, String recipient, boolean setDefault) throws Exception {
        String body = mockMvc.perform(post(URL).header("Authorization", token)
                        .contentType(JSON).content(json(alias, recipient, setDefault)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    @Test
    @DisplayName("첫 주소는 setDefault=false 로 보내도 기본 배송지가 된다")
    void firstAddressBecomesDefault() throws Exception {
        String token = login(meLoginId);
        // 요청은 명시적으로 false 다 — 그래도 기본이 되어야 한다.
        // 안 그러면 "주소는 있는데 기본이 없어" 주문서 자동 채움이 빈 폼이 된다.
        mockMvc.perform(post(URL).header("Authorization", token)
                        .contentType(JSON).content(json("집", "ZZ수령인", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.alias").value("집"));
    }

    @Test
    @DisplayName("기본 배송지는 회원당 하나 — 두 번째를 기본으로 지정하면 첫 번째가 해제된다")
    void onlyOneDefault() throws Exception {
        String token = login(meLoginId);
        add(token, "집", "ZZ수령인집", false);          // 첫 주소 → 자동 기본
        String office = add(token, "회사", "ZZ수령인회사", true);  // 두 번째를 기본으로

        // 목록은 기본 배송지가 맨 위. 나머지는 전부 false 여야 한다.
        // (여기서 예외 없이 통과한다는 것 자체가 "해제 → 지정" 순서가 DB 에 제대로 갔다는 증거다 —
        //  순서가 뒤집히면 유니크 인덱스가 ORA-00001 을 던진다.)
        mockMvc.perform(get(URL).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(office))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].isDefault").value(false));
    }

    @Test
    @DisplayName("기본 배송지를 지우면 남은 주소가 기본을 승계한다")
    void deletingDefaultPromotesAnother() throws Exception {
        String token = login(meLoginId);
        String home = add(token, "집", "ZZ수령인집", false);
        String office = add(token, "회사", "ZZ수령인회사", false);

        mockMvc.perform(delete(URL + "/" + home).header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(office))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    @DisplayName("남의 주소는 403 이 아니라 404 — 존재 여부를 알려주지 않는다")
    void othersAddressIsNotFound() throws Exception {
        String mine = add(login(meLoginId), "집", "ZZ수령인", false);
        String other = login(otherLoginId);

        mockMvc.perform(put(URL + "/" + mine).header("Authorization", other)
                        .contentType(JSON).content(json("가로채기", "ZZ침입자", true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADDRESS-404"));

        mockMvc.perform(patch(URL + "/" + mine + "/default").header("Authorization", other))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(URL + "/" + mine).header("Authorization", other))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("인증 없이 주소록에 접근하면 401")
    void requiresAuth() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).contentType(JSON).content(json("집", "ZZ수령인", true)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("MemberResponse.ship* 는 주소록의 기본 배송지에서 온다 (V18 이후 출처 변경)")
    void meReflectsDefaultAddress() throws Exception {
        String token = login(meLoginId);

        // 주소가 없으면 전부 null — 주문서는 빈 폼으로 시작한다.
        mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipRecipient").doesNotExist());

        add(token, "집", "ZZ수령인집", false);
        add(token, "회사", "ZZ수령인회사", true);

        // 기본 배송지(회사)의 값이 실려야 한다. 응답 필드 이름은 V18 이전과 같다(계약 유지).
        mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipRecipient").value("ZZ수령인회사"))
                .andExpect(jsonPath("$.data.shipZipcode").value("06134"));
    }

    @Test
    @DisplayName("옛 계약 PATCH /me/shipping-address 는 주소록의 기본 항목을 만들고 덮어쓴다")
    void legacyShippingAddressWritesToAddressBook() throws Exception {
        String token = login(meLoginId);
        String body = "{\"recipient\":\"ZZ옛경로\",\"phone\":\"010-0000-0000\",\"zipcode\":\"12345\","
                + "\"address1\":\"서울시 종로구 1\",\"address2\":null}";

        mockMvc.perform(patch("/api/members/me/shipping-address").header("Authorization", token)
                        .contentType(JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipRecipient").value("ZZ옛경로"));

        // 주소록에 한 건 생겼고 그게 기본 배송지다 — 주소가 두 벌로 갈라지지 않는다.
        mockMvc.perform(get(URL).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[0].recipient").value("ZZ옛경로"));

        // 다시 호출하면 새로 만들지 않고 기존 기본 항목을 덮어쓴다(옛 동작과 결과가 같다).
        mockMvc.perform(patch("/api/members/me/shipping-address").header("Authorization", token)
                        .contentType(JSON)
                        .content(body.replace("ZZ옛경로", "ZZ덮어씀")))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].recipient").value("ZZ덮어씀"));
    }
}

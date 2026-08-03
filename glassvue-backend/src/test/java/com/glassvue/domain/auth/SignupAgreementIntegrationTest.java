package com.glassvue.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * 가입 약관 동의 (2026-08-03, 백로그 B-21).
 *
 * <p>여기서만 드러나는 것 넷:
 * <ol>
 *   <li><b>필드를 아예 안 보내는 것도 "동의 안 함"이다</b> — {@code null} 과 {@code false} 를 함께
 *       막지 않으면 <b>동의 체크 없이 가입되는 경로</b>가 남는다. 화면만 고쳐서는 못 막는다.</li>
 *   <li><b>동의 시각의 출처가 서버인가</b> — 요청에 시각을 넣어도 무시돼야 한다.
 *       근거로 쓸 값이라 클라이언트가 정하면 조작 가능해진다.</li>
 *   <li><b>선택 동의는 안 하면 {@code null}</b> — {@code false} 를 시각으로 남기면
 *       "동의했다"와 구분이 사라진다.</li>
 *   <li>⚠ <b>소급 적용이 없다</b> — V37 이전 회원은 동의 기록이 없어도 <b>계속 로그인된다.</b>
 *       비밀번호 정책(E-3, 2026-07-30)에서 정한 것과 같은 규칙이다. 이걸 안 고정하면 나중에
 *       "동의 안 한 회원 로그인 차단"을 무심코 넣어 <b>기존 회원 전부를 잠글</b> 수 있다.</li>
 * </ol>
 *
 * <p>⚠ 픽스처가 <b>두 갈래</b>다(WA §3): <b>API 로 만드는 계정</b>은 동의 정책을 타야 하고,
 * <b>리포지토리로 직접 저장하는 픽스처</b>는 정책을 안 탄다 — 그리고 그게 <b>V37 이전 회원을
 * 재현하는 유일한 수단</b>이다(④를 검증할 방법이 그것뿐이다).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SignupAgreementIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    /**
     * ⚠ <b>API 로 가입하는 계정의 비밀번호는 정책(E-3)을 통과하는 값이어야 한다</b>(WA §3 — 픽스처 두 갈래).
     * 처음에 다른 테스트를 따라 {@code password123} 을 썼다가 <b>9건이 AUTH-400P2(흔한 비밀번호)로
     * 떨어졌다</b> — 그 테스트들은 계정을 <b>리포지토리로</b> 만들어 정책을 안 타는 쪽이었다.
     */
    private static final String PW_API = "Zz-consent-9174";
    /** 리포지토리 직접 저장용 — 정책을 안 탄다. <b>V37 이전 회원을 재현하는 자리</b>라 이게 맞다. */
    private static final String PW_LEGACY = "password123";
    private static final String SIGNUP = "/api/auth/signup";

    private String id;

    @BeforeEach
    void setUp() {
        id = "ag_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** {@code agreeTerms}·{@code agreeMarketing} 을 지정해 가입 본문을 만든다. {@code null} 이면 필드를 뺀다. */
    private String body(Boolean agreeTerms, Boolean agreeMarketing) {
        StringBuilder sb = new StringBuilder("{\"loginId\":\"").append(id)
                .append("\",\"password\":\"").append(PW_API)
                .append("\",\"nickname\":\"ZZ동의").append(id.substring(3))
                .append("\",\"email\":\"").append(id).append("@example.com\"");
        if (agreeTerms != null) {
            sb.append(",\"agreeTerms\":").append(agreeTerms);
        }
        if (agreeMarketing != null) {
            sb.append(",\"agreeMarketing\":").append(agreeMarketing);
        }
        return sb.append("}").toString();
    }

    private Member saved() {
        return memberRepository.findByLoginId(id).orElseThrow();
    }

    @Test
    @DisplayName("⚠ 동의 필드를 **아예 안 보내면** 가입이 막힌다 (null 도 '동의 안 함'이다)")
    void missingAgreementIsRejected() throws Exception {
        mockMvc.perform(post(SIGNUP).contentType(JSON).content(body(null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH-400T"));

        assertThat(memberRepository.findByLoginId(id))
                .as("거부됐으면 계정이 생기면 안 된다 — 잔재도 안 남는다").isEmpty();
    }

    @Test
    @DisplayName("동의를 false 로 명시해도 막힌다")
    void explicitFalseIsRejected() throws Exception {
        mockMvc.perform(post(SIGNUP).contentType(JSON).content(body(false, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH-400T"));
    }

    @Test
    @DisplayName("⚠ 전용 에러 코드로 답한다 — 형식 오류(COMMON-400)와 구분돼야 화면이 체크박스를 붉힐 수 있다")
    void usesDedicatedErrorCode() throws Exception {
        String bodyText = mockMvc.perform(post(SIGNUP).contentType(JSON).content(body(false, null)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(bodyText, "$.error.code")).isEqualTo("AUTH-400T");
        assertThat((String) JsonPath.read(bodyText, "$.error.message"))
                .as("사용자에게 그대로 보일 문구라 필드명이 섞이면 안 된다")
                .doesNotContain("agreeTerms");
    }

    @Test
    @DisplayName("동의하면 가입되고 **동의 시각이 남는다**")
    void agreementIsRecorded() throws Exception {
        mockMvc.perform(post(SIGNUP).contentType(JSON).content(body(true, null)))
                .andExpect(status().isCreated());

        Member m = saved();
        assertThat(m.getTermsAgreedAt()).as("필수 동의 시각이 남아야 한다").isNotNull();
        assertThat(m.hasAgreedToTerms()).isTrue();
    }

    @Test
    @DisplayName("선택 동의(마케팅)를 하면 그 시각도 남는다")
    void marketingAgreementIsRecorded() throws Exception {
        mockMvc.perform(post(SIGNUP).contentType(JSON).content(body(true, true)))
                .andExpect(status().isCreated());

        Member m = saved();
        assertThat(m.getMarketingAgreedAt()).isNotNull();
        assertThat(m.hasAgreedToMarketing()).isTrue();
    }

    @Test
    @DisplayName("⚠ 선택 동의를 안 하면 **null** — false 를 시각으로 남기면 '동의함'과 구분이 사라진다")
    void marketingNotAgreedStaysNull() throws Exception {
        mockMvc.perform(post(SIGNUP).contentType(JSON).content(body(true, false)))
                .andExpect(status().isCreated());

        Member m = saved();
        assertThat(m.getMarketingAgreedAt()).isNull();
        assertThat(m.hasAgreedToMarketing()).isFalse();
        assertThat(m.getTermsAgreedAt()).as("필수 동의는 그대로 남아 있어야 한다").isNotNull();
    }

    @Test
    @DisplayName("마케팅 필드를 아예 안 보내도 가입은 되고 미동의로 남는다 (선택이니까)")
    void marketingIsOptional() throws Exception {
        mockMvc.perform(post(SIGNUP).contentType(JSON).content(body(true, null)))
                .andExpect(status().isCreated());

        assertThat(saved().getMarketingAgreedAt()).isNull();
    }

    @Test
    @DisplayName("⚠ 동의 시각은 **서버가 찍는다** — 요청이 보낸 시각은 무시된다")
    void agreedAtComesFromServer() throws Exception {
        // 클라이언트가 과거 시각을 우겨넣어 본다. 서버는 이 필드를 아예 받지 않으므로 무시돼야 한다.
        String forged = body(true, null).replaceFirst("\\}$",
                ",\"termsAgreedAt\":\"2000-01-01T00:00:00Z\"}");
        mockMvc.perform(post(SIGNUP).contentType(JSON).content(forged))
                .andExpect(status().isCreated());

        assertThat(saved().getTermsAgreedAt())
                .as("클라이언트가 보낸 2000년이 아니라 지금이어야 한다")
                .isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("⚠ 소급 적용 없음 — 동의 기록이 없는 **기존 회원은 계속 로그인된다**")
    void existingMembersWithoutAgreementCanStillLogIn() throws Exception {
        // 리포지토리 직접 저장 = 정책을 안 타는 픽스처. V37 이전 회원을 재현하는 유일한 수단이다(WA §3).
        memberRepository.save(Member.builder()
                .loginId(id).password(passwordEncoder.encode(PW_LEGACY))
                .nickname("ZZ구회원" + id.substring(3)).role(Role.USER).build());

        assertThat(saved().getTermsAgreedAt()).as("전제: 동의 기록이 없는 회원이다").isNull();

        mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + id + "\",\"password\":\"" + PW_LEGACY + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("관리자 회원 조회에 동의 시각이 실린다 — 조회 못 하는 기록은 근거 구실을 못 한다")
    void adminCanSeeAgreement() throws Exception {
        mockMvc.perform(post(SIGNUP).contentType(JSON).content(body(true, true)))
                .andExpect(status().isCreated());

        String adminId = "agadm_" + UUID.randomUUID().toString().substring(0, 8);
        memberRepository.save(Member.builder()
                .loginId(adminId).password(passwordEncoder.encode(PW_LEGACY))
                .nickname("ZZ동의관리자" + id.substring(3)).role(Role.ADMIN).build());
        String loginBody = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + adminId + "\",\"password\":\"" + PW_LEGACY + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String admin = "Bearer " + JsonPath.read(loginBody, "$.data.accessToken");

        mockMvc.perform(get("/api/admin/members/" + saved().getId()).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.termsAgreedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.marketingAgreedAt").isNotEmpty());
    }
}

package com.glassvue.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationRepository;
import com.glassvue.domain.notification.service.NotificationCommandService;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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
 * 마케팅 알림 발송 (2026-08-03, B-21 후속).
 *
 * <p>여기서만 드러나는 것 넷:
 * <ol>
 *   <li><b>동의하지 않은 회원에게 가지 않는다</b> — 동의를 받는 기능을 만들어 놓고 발송이 그걸 무시하면
 *       기능 전체가 무의미해진다. V37 이전 가입자(동의 기록 없음)도 여기서 자연히 빠져야 한다.</li>
 *   <li><b>동의와 수신 거부는 다른 것이다</b> — 동의했지만 알림 설정을 끈 회원은 <b>발송에서 빠지되
 *       동의 기록은 그대로</b> 남아야 한다.</li>
 *   <li><b>보고 숫자가 정직한가</b> — 대상 수를 그대로 "보냈다"고 세면 <b>수신 거부자까지 발송으로
 *       집계</b>된다. `sent` 는 실제로 만들어진 것만 세야 한다.</li>
 *   <li><b>권한</b> — 전 회원에게 나가는 되돌릴 수 없는 조작이다. 401/403 을 계약으로 고정한다(WA §2-4).</li>
 * </ol>
 *
 * <p>⚠ 공유 espdb 에 회원이 이미 쌓여 있어 <b>절대 수를 단정하지 않고 증분</b>으로 본다.
 * 발송 결과도 마찬가지라, <b>"내가 만든 회원에게 알림이 갔는가"</b> 를 직접 확인한다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminMarketingIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationCommandService notificationCommandService;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    private static final String PW = "password123";
    private static final String URL = "/api/admin/notifications/marketing";
    private static final String BODY =
            "{\"title\":\"ZZ마케팅제목\",\"message\":\"ZZ마케팅내용\",\"link\":\"/products\"}";

    private String suffix;
    private String adminLoginId;
    private String userLoginId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        adminLoginId = "mkadm_" + suffix;
        userLoginId = "mkusr_" + suffix;
        member(adminLoginId, "ZZ마케팅관리자" + suffix, Role.ADMIN, null);
        member(userLoginId, "ZZ마케팅일반" + suffix, Role.USER, null);
    }

    /** 리포지토리 직접 저장 — 정책을 안 탄다. {@code marketingAgreedAt} 을 원하는 대로 박을 수 있다. */
    private UUID member(String loginId, String nickname, Role role, Instant marketingAgreedAt) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role)
                .marketingAgreedAt(marketingAgreedAt)
                .build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private long marketingCountOf(UUID memberId) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getMemberId().equals(memberId))
                .filter(n -> n.getType() == NotificationType.MARKETING)
                .count();
    }

    private int send(String admin) throws Exception {
        String body = mockMvc.perform(post(URL).header("Authorization", admin)
                        .contentType(JSON).content(BODY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.sent")).intValue();
    }

    @Test
    @DisplayName("동의한 회원에게 마케팅 알림이 만들어진다")
    void sendsToAgreedMember() throws Exception {
        UUID agreedId = member("mkyes_" + suffix, "ZZ동의함" + suffix, Role.USER, Instant.now());
        String admin = login(adminLoginId);

        send(admin);

        assertThat(marketingCountOf(agreedId)).as("동의자에게 한 건 갔어야 한다").isEqualTo(1);
    }

    @Test
    @DisplayName("⚠ 동의하지 않은 회원에게는 가지 않는다 — 동의 기능을 만들어 놓고 발송이 무시하면 무의미하다")
    void doesNotSendToMemberWithoutAgreement() throws Exception {
        // marketingAgreedAt = null → V37 이전 가입자와 같은 상태(거부한 게 아니라 물어본 적이 없다)
        UUID notAgreedId = member("mkno_" + suffix, "ZZ미동의" + suffix, Role.USER, null);
        String admin = login(adminLoginId);

        send(admin);

        assertThat(marketingCountOf(notAgreedId)).as("동의 기록이 없으면 대상이 아니다").isZero();
    }

    @Test
    @DisplayName("⚠ 동의했어도 **알림 설정을 끄면** 가지 않는다 (수신 거부는 동의와 별개)")
    void respectsOptOut() throws Exception {
        UUID optedOutId = member("mkoff_" + suffix, "ZZ수신거부" + suffix, Role.USER, Instant.now());
        notificationCommandService.changeSetting(optedOutId, NotificationType.MARKETING, false);
        String admin = login(adminLoginId);

        send(admin);

        assertThat(marketingCountOf(optedOutId)).as("수신을 껐으면 안 만들어진다").isZero();
        assertThat(memberRepository.findById(optedOutId).orElseThrow().getMarketingAgreedAt())
                .as("⚠ 수신을 꺼도 **동의 기록은 지워지지 않는다** — 둘은 다른 값이다")
                .isNotNull();
    }

    @Test
    @DisplayName("⚠ 보고 숫자가 정직하다 — sent 는 **실제로 만들어진 것만** 센다")
    void reportsSentHonestly() throws Exception {
        member("mky1_" + suffix, "ZZ보고1" + suffix, Role.USER, Instant.now());
        UUID optedOutId = member("mky2_" + suffix, "ZZ보고2" + suffix, Role.USER, Instant.now());
        notificationCommandService.changeSetting(optedOutId, NotificationType.MARKETING, false);
        String admin = login(adminLoginId);

        String body = mockMvc.perform(post(URL).header("Authorization", admin)
                        .contentType(JSON).content(BODY))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        int agreed = ((Number) JsonPath.read(body, "$.data.agreed")).intValue();
        int sent = ((Number) JsonPath.read(body, "$.data.sent")).intValue();
        int optedOut = ((Number) JsonPath.read(body, "$.data.optedOut")).intValue();

        assertThat(sent).as("수신 거부자가 있으므로 발송은 동의자보다 적어야 한다").isLessThan(agreed);
        assertThat(optedOut).as("optedOut 은 agreed - sent 여야 한다").isEqualTo(agreed - sent);
        assertThat(optedOut).as("적어도 이 테스트가 만든 거부자 1명은 잡혀야 한다").isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("대상 수 조회는 **동의자 수**를 준다 (발송 전에 규모를 알 수 있게)")
    void audienceCountsAgreedMembers() throws Exception {
        String admin = login(adminLoginId);
        int before = ((Number) JsonPath.read(
                mockMvc.perform(get(URL + "/audience").header("Authorization", admin))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.data")).intValue();

        member("mkaud_" + suffix, "ZZ대상수" + suffix, Role.USER, Instant.now());

        mockMvc.perform(get(URL + "/audience").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(before + 1));
    }

    @Test
    @DisplayName("⚠ 대상을 요청으로 지정할 수 없다 — 동의하지 않은 회원에게 보내는 구멍을 막는다")
    void cannotTargetArbitraryMembers() throws Exception {
        UUID notAgreedId = member("mktgt_" + suffix, "ZZ지정대상" + suffix, Role.USER, null);
        String admin = login(adminLoginId);

        // 요청에 대상을 우겨넣어 본다. 서버는 이 필드를 아예 받지 않으므로 무시돼야 한다.
        mockMvc.perform(post(URL).header("Authorization", admin).contentType(JSON)
                        .content("{\"title\":\"ZZ지정\",\"message\":\"ZZ내용\","
                                + "\"memberIds\":[\"" + notAgreedId + "\"]}"))
                .andExpect(status().isOk());

        assertThat(marketingCountOf(notAgreedId))
                .as("요청이 지목했어도 동의하지 않았으면 안 간다").isZero();
    }

    @Test
    @DisplayName("제목·내용이 비면 거부된다 (내용 없는 알림을 전 회원에게 보내지 않는다)")
    void rejectsBlankContent() throws Exception {
        String admin = login(adminLoginId);
        mockMvc.perform(post(URL).header("Authorization", admin).contentType(JSON)
                        .content("{\"title\":\"\",\"message\":\"ZZ내용\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL).header("Authorization", admin).contentType(JSON)
                        .content("{\"title\":\"ZZ제목\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("마케팅 발송은 관리자만 — 미인증 401, 일반 회원 403 (발송·대상조회 둘 다)")
    void requiresAdmin() throws Exception {
        mockMvc.perform(post(URL).contentType(JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL + "/audience")).andExpect(status().isUnauthorized());

        String user = login(userLoginId);
        mockMvc.perform(post(URL).header("Authorization", user).contentType(JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/audience").header("Authorization", user))
                .andExpect(status().isForbidden());
    }
}

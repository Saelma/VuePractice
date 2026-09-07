package com.glassvue.domain.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.global.common.KstDates;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 이력 <b>기간 필터</b>(B-26 잔여, 2026-09-07).
 *
 * <p>🔴 <b>지키는 것은 «종료일 포함» 이다.</b> 「그날 누가 무엇을 했나」가 이 화면에서 가장 잦은
 * 질문인데, 상한을 {@code 23:59:59} 로 잡으면 그 날 <b>마지막 한 줄이 조용히 빠진다.</b>
 *
 * <p>⚠ <b>대조군을 함께 단언한다</b>(WA §3-3) — 좁혀서 «안 나온다» 만 보면 필터가 <b>전부</b>
 * 걸러내도 초록이다. 그래서 같은 행이 넓은 기간에서는 <b>나온다</b>는 것도 본다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAuditPeriodFilterIntegrationTest {

    private static final String URL = "/api/admin/audit";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String PW = "Test1234!@";

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    private String superToken;
    private String targetLogin;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String superLogin = "sup" + suffix;
        targetLogin = "tgt" + suffix;
        member(superLogin, "슈퍼" + suffix, Role.SUPER_ADMIN);
        member(targetLogin, "대상" + suffix, Role.USER);
        superToken = login(superLogin);
    }

    private UUID member(String loginId, String nickname, Role role) {
        Member m = Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).email(loginId + "@example.com").role(role)
                .build();
        return memberRepository.save(m).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 감사 한 줄을 만든다 — 대상 회원을 정지시킨다(SUPER 만 가능한 조작이라 토큰도 맞는다). */
    private void makeOneAuditRow() throws Exception {
        UUID targetId = memberRepository.findByLoginId(targetLogin).orElseThrow().getId();
        mockMvc.perform(post("/api/admin/members/" + targetId + "/suspend")
                        .header("Authorization", superToken))
                .andExpect(status().isOk());
    }

    /**
     * 방금 만든 감사 줄의 {@code created_at} 을 특정 시각으로 박는다.
     *
     * <p>정상 경로로는 «지금» 밖에 만들 수 없어 <b>KST 경계를 재현할 방법이 없다</b> —
     * `AdminSalesStatsIntegrationTest.forcePaidAt` 과 같은 이유·같은 방식이다.
     * ⚠ 운영 코드에는 이런 경로가 없다.
     */
    private void forceCreatedAt(Instant at) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "UPDATE admin_audit_log SET created_at = ?1 WHERE target_login = ?2")
                .setParameter(1, at)
                .setParameter(2, targetLogin)
                .executeUpdate();
        entityManager.clear();
    }

    private int countIn(String from, String to) throws Exception {
        String q = URL + "?targetLogin=" + targetLogin
                + (from == null ? "" : "&from=" + from)
                + (to == null ? "" : "&to=" + to);
        String body = mockMvc.perform(get(q).header("Authorization", superToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.totalElements")).intValue();
    }

    @Test
    @DisplayName("종료일은 포함된다 — 그 날 23:59:59.5 에 남은 줄도 걸린다")
    void endDateIsInclusive_evenAtLastFractionOfSecond() throws Exception {
        makeOneAuditRow();
        LocalDate day = LocalDate.of(2026, 5, 20);
        // KST 2026-05-20 23:59:59.5 = UTC 14:59:59.5
        forceCreatedAt(Instant.parse("2026-05-20T14:59:59.500Z"));

        org.assertj.core.api.Assertions.assertThat(countIn(day.format(DAY), day.format(DAY)))
                .as("종료일 당일 마지막 순간이 빠지면 안 된다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("기간 밖은 안 걸린다 — 그리고 넓히면 다시 걸린다(대조군)")
    void outsidePeriodIsExcluded_andWiderRangeFindsItAgain() throws Exception {
        makeOneAuditRow();
        LocalDate day = LocalDate.of(2026, 5, 20);
        forceCreatedAt(KstDates.startOfDay(day).plusSeconds(3600));   // KST 01:00

        org.assertj.core.api.Assertions.assertThat(countIn(day.plusDays(1).format(DAY), null))
                .as("시작일이 하루 뒤면 안 걸려야 한다").isZero();
        org.assertj.core.api.Assertions.assertThat(countIn(null, day.minusDays(1).format(DAY)))
                .as("종료일이 하루 앞이면 안 걸려야 한다").isZero();
        // 🔴 대조군 — 필터가 전부 걸러내는 것이 아님을 같은 행으로 보인다.
        org.assertj.core.api.Assertions.assertThat(countIn(day.format(DAY), day.format(DAY)))
                .as("같은 날로 좁히면 걸려야 한다").isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(countIn(null, null))
                .as("기간을 안 주면 전체다").isEqualTo(1);
    }

    @Test
    @DisplayName("시작일도 포함된다 — 그 날 00:00:00 에 남은 줄이 걸린다")
    void startDateIsInclusive() throws Exception {
        makeOneAuditRow();
        LocalDate day = LocalDate.of(2026, 5, 20);
        forceCreatedAt(KstDates.startOfDay(day));   // KST 00:00 정각

        org.assertj.core.api.Assertions.assertThat(countIn(day.format(DAY), day.format(DAY)))
                .as("시작일 00:00 정각이 빠지면 안 된다").isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(countIn(day.plusDays(1).format(DAY), null))
                .as("대조군: 다음 날부터로 좁히면 안 걸린다").isZero();
    }

    @Test
    @DisplayName("잘못된 날짜 형식은 400 — 조용히 무시하지 않는다")
    void malformedDateIsRejected() throws Exception {
        mockMvc.perform(get(URL + "?from=2026-13-99").header("Authorization", superToken))
                .andExpect(status().isBadRequest());
    }

    private static byte[] uuidToBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}

package com.glassvue.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.repository.AdminAuditLogRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.member.service.command.MemberAdminCommandService;
import com.glassvue.global.security.AuthUser;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 감사 로그(H-1) — <b>리스너·저장·조회·권한</b>을 관통해서 본다.
 *
 * <p>이 도메인은 소스 10개에 테스트가 0개였다(2026-07-30 실측). 기존에 있던 건
 * {@code MemberAdminCommandServiceTest} 뿐인데 그건 <b>이벤트가 발행되는가</b>만 본다 —
 * 즉 {@code AdminAuditListener} 이후(저장·조회·권한)는 한 번도 실행된 적이 없었다.
 *
 * <p>하필 감사 로그라는 게 중요하다: <b>쓰기만 하고 읽을 일이 없는 기능</b>이라 조용히 안 남고 있어도
 * 아무도 모른다. 그래서 여기서는 "이벤트를 발행했다"가 아니라 <b>행이 실제로 남았는지</b>를 본다.
 *
 * <p>권한 테스트는 WORKING-AGREEMENTS §2-4 의 필수 규약이다(401/403/200). 이 엔드포인트는 한 칸 더
 * 좁아서 <b>일반 ADMIN 도 403</b> 이다 — {@code /api/admin/audit/**} 에 SUPER_ADMIN 규칙이 얹혀 있다.
 *
 * <p>DB_HOST 있을 때만 실행, {@code @Transactional} 롤백으로 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAuditIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MemberAdminCommandService memberAdminCommandService;
    @Autowired PlatformTransactionManager txManager;

    private static final String PW = "password123";

    private String superLoginId;
    private String adminLoginId;
    private String userLoginId;
    private String targetLoginId;
    private UUID superId;
    private UUID userId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        superLoginId = "zzaudsuper_" + suffix();
        adminLoginId = "zzaudadm_" + suffix();
        userLoginId = "zzauduser_" + suffix();
        // 대상 loginId 에만 표식을 박아 둔다 — 이 테스트가 남긴 감사 행만 골라내는 열쇠(targetLogin 검색).
        targetLoginId = "zzaudtarget_" + suffix();
        superId = member(superLoginId, "ZZ감사최상위" + suffix(), Role.SUPER_ADMIN);
        member(adminLoginId, "ZZ감사관리자" + suffix(), Role.ADMIN);
        userId = member(userLoginId, "ZZ감사일반" + suffix(), Role.USER);
        targetId = member(targetLoginId, "ZZ감사대상" + suffix(), Role.USER);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    /** 이 테스트가 남긴 감사 행만(대상 loginId 표식으로 격리). 공유 DB 에 남의 이력이 있어도 안 섞인다. */
    private List<AdminAuditLog> rowsForTarget() {
        return auditLogRepository.search(null, null, targetLoginId, PageRequest.of(0, 10)).getContent();
    }

    // ---------- 권한 (WA §2-4) ----------

    @Test
    @DisplayName("감사 조회 권한: 비로그인 401 / USER 403 / 일반 ADMIN 403 / SUPER_ADMIN 200")
    void list_permission() throws Exception {
        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/audit").header("Authorization", login(userLoginId)))
                .andExpect(status().isForbidden());
        // ⚠ 여기가 이 엔드포인트의 특이점 — /api/admin/** 를 통과하는 일반 관리자도 막혀야 한다.
        mockMvc.perform(get("/api/admin/audit").header("Authorization", login(adminLoginId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit").header("Authorization", login(superLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ---------- 발행 → 저장 (리스너가 실제로 도는가) ----------

    @Test
    @DisplayName("정지하면 감사 행이 실제로 남는다 — 종류·행위자·대상 스냅샷까지")
    void suspend_writesAuditRow() throws Exception {
        String actor = login(superLoginId);
        assertThat(rowsForTarget()).isEmpty(); // 시작점: 이 대상에 대한 이력 0건

        mockMvc.perform(post("/api/admin/members/" + targetId + "/suspend")
                        .header("Authorization", actor))
                .andExpect(status().isOk());

        List<AdminAuditLog> rows = rowsForTarget();
        assertThat(rows).hasSize(1);
        AdminAuditLog row = rows.getFirst();
        assertThat(row.getAction()).isEqualTo(AuditAction.MEMBER_SUSPEND);
        assertThat(row.getActorId()).isEqualTo(superId);
        assertThat(row.getActorName()).startsWith("ZZ감사최상위");
        assertThat(row.getTargetId()).isEqualTo(targetId);
        assertThat(row.getTargetLogin()).isEqualTo(targetLoginId);
        assertThat(row.getDetail()).isNull(); // 정지·해제는 전/후가 없다
        assertThat(row.getCreatedAt()).isNotNull(); // BaseTimeEntity
    }

    @Test
    @DisplayName("역할 변경은 detail 에 전/후가 남고, 조회 API 로도 읽힌다")
    void changeRole_detailVisibleThroughApi() throws Exception {
        mockMvc.perform(patch("/api/admin/members/" + targetId + "/role")
                        .header("Authorization", login(superLoginId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", login(superLoginId))
                        .param("targetLogin", targetLoginId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("MEMBER_ROLE_CHANGE"))
                .andExpect(jsonPath("$.data.content[0].targetLogin").value(targetLoginId))
                .andExpect(jsonPath("$.data.content[0].detail").value("USER → ADMIN"))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists());
    }

    @Test
    @DisplayName("대상이 탈퇴해도 이력은 스냅샷으로 읽힌다 (FK 없는 설계의 근거)")
    void snapshotSurvivesTargetDeletion() throws Exception {
        String actor = login(superLoginId);
        mockMvc.perform(post("/api/admin/members/" + targetId + "/suspend")
                        .header("Authorization", actor))
                .andExpect(status().isOk());

        memberRepository.deleteById(targetId); // 탈퇴 = 하드 삭제

        mockMvc.perform(get("/api/admin/audit").header("Authorization", actor)
                        .param("targetLogin", targetLoginId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].targetLogin").value(targetLoginId));
    }

    // ---------- 조회 (필터 · 정렬) ----------

    @Test
    @DisplayName("조회: action 필터로 좁히고, 정렬을 안 주면 최신순")
    void list_filterAndDefaultSort() throws Exception {
        String actor = login(superLoginId);
        mockMvc.perform(post("/api/admin/members/" + targetId + "/suspend")
                        .header("Authorization", actor))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/members/" + targetId + "/unsuspend")
                        .header("Authorization", actor))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/members/" + targetId + "/role")
                        .header("Authorization", actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());

        // 대상으로 좁히면 3건이 최신순으로 — 마지막 조작(역할변경)이 맨 위
        mockMvc.perform(get("/api/admin/audit").header("Authorization", actor)
                        .param("targetLogin", targetLoginId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].action").value("MEMBER_ROLE_CHANGE"));

        // action 필터는 종류로 좁힌다
        mockMvc.perform(get("/api/admin/audit").header("Authorization", actor)
                        .param("targetLogin", targetLoginId)
                        .param("action", "MEMBER_SUSPEND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("MEMBER_SUSPEND"));
    }

    @Test
    @DisplayName("targetLogin 검색은 부분일치 + 대소문자 무시")
    void list_targetLoginPartialMatch() throws Exception {
        String actor = login(superLoginId);
        mockMvc.perform(post("/api/admin/members/" + targetId + "/suspend")
                        .header("Authorization", actor))
                .andExpect(status().isOk());

        String fragment = targetLoginId.substring(targetLoginId.length() - 6).toUpperCase();
        mockMvc.perform(get("/api/admin/audit").header("Authorization", actor)
                        .param("targetLogin", fragment))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].targetLogin").value(targetLoginId));
    }

    // ---------- 조작이 없으면 감사도 없다 ----------

    @Test
    @DisplayName("권한 거부로 조작이 안 됐으면 감사 행도 남지 않는다")
    void forbiddenAction_writesNothing() throws Exception {
        // 일반 관리자는 역할 변경 권한이 없다(MEMBER-403A)
        mockMvc.perform(patch("/api/admin/members/" + targetId + "/role")
                        .header("Authorization", login(adminLoginId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        assertThat(rowsForTarget()).isEmpty();
    }

    @Test
    @DisplayName("조작이 롤백되면 감사도 남지 않는다 — 별도 트랜잭션이 아님을 실측")
    void rollbackAlsoDropsAuditRow() {
        // ⚠ 이 단언은 "같은 트랜잭션"이라는 설계 주장의 유일한 실측 지점이다.
        // 감사 저장이 REQUIRES_NEW 였다면 조작이 롤백돼도 감사 행은 **커밋된 채 살아남는다**
        // — 그러면 "일어나지 않은 조작"이 이력에 남는다. 그 경우를 이 테스트가 잡는다.
        //
        // 테스트 자신의 트랜잭션 안에서는 롤백을 볼 수 없으므로(끝에 통째로 롤백된다)
        // REQUIRES_NEW 로 **독립 트랜잭션**을 열어 그 안에서 실패시킨다. 롤백되므로 공유 DB 에 잔재가 없다.
        TransactionTemplate newTx = new TransactionTemplate(txManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        AuthUser actor = new AuthUser(superId, Role.SUPER_ADMIN, "ZZ감사최상위롤백");
        String rollbackLogin = "zzaudroll_" + suffix();

        assertThatThrownBy(() -> newTx.executeWithoutResult(status -> {
            UUID victim = member(rollbackLogin, "ZZ롤백대상" + suffix(), Role.USER);
            memberAdminCommandService.suspend(actor, victim); // 여기서 감사 행이 저장된다
            // ⚠ 롤백 전에 "행이 실제로 만들어졌다"를 먼저 단언한다 — 이게 없으면 아래 isEmpty() 가
            // **아무 일도 안 일어나서** 통과하는 경우와 구분되지 않는다(빈손 통과 방지).
            assertThat(auditLogRepository.search(null, null, rollbackLogin, PageRequest.of(0, 10)).getContent())
                    .hasSize(1);
            throw new IllegalStateException("의도적 실패 — 조작 전체를 롤백시킨다");
        })).isInstanceOf(IllegalStateException.class);

        List<AdminAuditLog> rows = newTx.execute(status ->
                auditLogRepository.search(null, null, rollbackLogin, PageRequest.of(0, 10)).getContent());
        assertThat(rows).isEmpty();
    }

    /**
     * 🔴 <b>긴 detail 이 DB 까지 «실제로» 눕는가</b> (2026-08-27, BACKLOG §I-13).
     *
     * <p>{@code AdminAuditLogDetailTest} 는 <b>메서드</b>를 지킨다 — 자바 문자열이 1000자 안으로
     * 들어오는지. 🔴 <b>그것만으로는 부족하다</b>: 컬럼이 {@code VARCHAR2(1000 CHAR)} 인데
     * {@code MAX_STRING_SIZE=STANDARD} 라 <b>4000바이트</b>라는 또 하나의 천장이 있고, 한글은
     * UTF-8 에서 <b>3바이트</b>다. 「1000자」를 통과해도 <b>바이트에서 걸릴 수 있다</b> —
     * 그건 DB 에 실제로 넣어 봐야만 드러난다.
     *
     * <p>⚠ 이 자리가 없으면 {@code ORA-12899} 는 <b>운영에서</b> 처음 보게 된다. 그리고 감사는
     * 조작과 같은 트랜잭션이라 그 순간 <b>조작이 통째로 롤백된다.</b>
     */
    @Test
    @DisplayName("🔴 1000자를 넘는 detail 이 «…(잘림)» 으로 눕고 DB 에 실제로 저장된다 (§I-13)")
    void overlongDetailIsTruncatedAndPersists() {
        String login = "zzaudlong_" + suffix();
        UUID targetId = member(login, "ZZ긴사유대상" + suffix(), Role.USER);

        // ⚠ **한글로 채운다** — ASCII 로 채우면 «바이트 천장» 을 영영 안 밟는다(1자=1바이트).
        String overlong = "ZZ" + "가".repeat(1_500);
        auditLogRepository.save(AdminAuditLog.builder()
                .action(AuditAction.MEMBER_SUSPEND)
                .actorId(UUID.randomUUID()).actorName("ZZ감사관리자")
                .targetId(targetId).targetLogin(login)
                .detail(overlong)
                .build());
        // 🔴 **flush 해서 실제 INSERT 를 일으킨다** — 안 하면 트랜잭션 끝까지 SQL 이 안 나가고,
        //    이 테스트는 «DB 가 받아 줬다» 가 아니라 «자바 객체가 만들어졌다» 만 보게 된다.
        auditLogRepository.flush();

        List<AdminAuditLog> rows =
                auditLogRepository.search(null, null, login, PageRequest.of(0, 10)).getContent();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDetail()).hasSize(AdminAuditLog.DETAIL_MAX);
        assertThat(rows.get(0).getDetail()).endsWith("…(잘림)");
    }
}

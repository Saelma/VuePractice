package com.glassvue.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.audit.entity.AdminAuditLog;
import com.glassvue.domain.audit.entity.AuditAction;
import com.glassvue.domain.audit.entity.AuditTargetType;
import com.glassvue.domain.audit.repository.AdminAuditLogRepository;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 확대 <b>4차</b> — <b>카테고리 · 공지 · 문의 답변</b>이 원장에 남는가 (2026-08-21, V56).
 *
 * <p>🔴 <b>이 파일이 진짜로 지키는 것은 «새 target_type 두 값이 DB 를 통과하는가» 다.</b>
 * {@code AuditActionTargetTypeTest} 는 <b>DB 를 안 띄워서</b> enum 끼리만 대조한다 — 그건
 * {@code CHECK (target_type IN (...))} 를 <b>한 번도 밟지 않는다.</b> V56 이 그 제약을 넓혔는데
 * 안 넓혔다면 여기서만 빨개진다(Oracle enum CHECK 트랩 — 백로그가 *"통합 테스트를 반드시
 * 함께 쓴다"* 로 못 박아 둔 자리다).
 *
 * <p>⚠ <b>MockMvc 호출과 같은 트랜잭션이라 flush 를 따로 안 부른다</b> — 아래 조회가 Hibernate 의
 * auto-flush 를 일으켜 INSERT 가 실제로 DB 에 닿는다. 🔴 <b>단언을 조회 없이 하면</b>(예: 이벤트만
 * 세면) INSERT 가 롤백까지 안 나가서 <b>제약이 한 번도 실행되지 않는다.</b>
 *
 * <p>여기서 고정하는 계약:
 * <ul>
 *   <li>🔴 <b>카테고리·공지는 자기 {@code target_type} 을 갖는다</b> — 상품·회원으로 접히지 않는다.
 *       V53 틀이 «값을 더해» 확장된 첫 자리이고, <b>그 확장이 실제로 저장되는가</b>가 요점이다.</li>
 *   <li><b>지우기 전에 이름을 읽는다</b> — 카테고리·공지에는 유예(F-7)가 없어 행이 진짜로 사라진다.
 *       detail 이 <b>유일하게 남는 흔적</b>이다.</li>
 *   <li><b>문의 답변의 대상은 질문자</b>({@code MEMBER}) — «숨김» 과 같은 {@code targetId} 로 묶인다.</li>
 *   <li>🔴 <b>답변 detail 이 «알림이 나갔나» 를 답한다</b> — 첫 답변에만 알림이 나가고, 그건 회수할 수 없다.</li>
 * </ul>
 *
 * <p>⚠ 공유 espdb 라 운영 데이터가 섞여 있다. 단언은 <b>이 테스트가 만든 대상의 id 로 좁혀서만</b> 한다.
 *
 * <p>DB_HOST 있을 때만 실행, {@code @Transactional} 롤백으로 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentAuditIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String JSON = "application/json";
    private static final String PW = "password123";

    private String suffix;
    private String memberLoginId;
    private UUID adminId;
    private UUID memberId;
    private UUID categoryId;
    private UUID productId;
    private String auth;
    private String memberAuth;

    @BeforeEach
    void setUp() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminLoginId = "co_a_" + suffix;
        memberLoginId = "co_m_" + suffix;

        adminId = memberRepository.save(Member.builder().loginId(adminLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ4감사관리자" + suffix)
                .role(Role.ADMIN).build()).getId();
        memberId = memberRepository.save(Member.builder().loginId(memberLoginId)
                .password(passwordEncoder.encode(PW)).nickname("ZZ4감사회원" + suffix)
                .role(Role.USER).build()).getId();
        categoryId = categoryRepository.save(
                Category.builder().name("ZZC4-감사" + suffix).build()).getId();
        auth = login(adminLoginId);
        memberAuth = login(memberLoginId);
        productId = createProduct();
    }

    // ── 도우미 ────────────────────────────────────────────────

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private UUID createProduct() throws Exception {
        String body = "{\"name\":\"ZZ4-문의상품" + suffix + "\",\"description\":\"설명\","
                + "\"price\":10000,\"status\":\"SELLING\","
                + "\"categoryId\":\"" + categoryId + "\",\"variants\":["
                + "{\"name\":\"기본\",\"priceDelta\":0,\"stock\":5}]}";
        String res = mockMvc.perform(post("/api/products").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(res, "$.data"));
    }

    /** 이 테스트가 만든 대상의 행만 — 공유 DB 라 전체를 세면 남의 것이 섞인다. */
    private List<AdminAuditLog> rowsOf(UUID targetId, AuditAction action) {
        return auditLogRepository.search(action, null, null, PageRequest.of(0, 50)).getContent()
                .stream().filter(row -> row.getTargetId().equals(targetId)).toList();
    }

    private AdminAuditLog onlyRow(UUID targetId, AuditAction action) {
        List<AdminAuditLog> rows = rowsOf(targetId, action);
        assertThat(rows).as("%s 행이 정확히 하나", action).hasSize(1);
        return rows.get(0);
    }

    // ── 카테고리 ────────────────────────────────────────────────

    private UUID createCategory(String name) throws Exception {
        String res = mockMvc.perform(post("/api/categories").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(res, "$.data.id"));
    }

    @DisplayName("🔴 카테고리 등록 — target_type=CATEGORY 가 DB 를 통과한다 (V56 의 요점)")
    @Test
    void categoryCreateIsRecorded() throws Exception {
        String name = "ZZC4-새분류" + suffix;
        UUID id = createCategory(name);

        AdminAuditLog row = onlyRow(id, AuditAction.CATEGORY_CREATE);

        // 🔴 이 단언이 V56 그 자체다. CHECK 제약을 안 넓혔다면 위 POST 가 여기 오기 전에 터진다
        //    (감사와 조작이 같은 트랜잭션 — «감사가 안 남는다» 가 아니라 «기능이 안 된다»).
        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.CATEGORY);
        assertThat(row.getActorId()).isEqualTo(adminId);
        assertThat(row.getDetail()).isEqualTo(name);
        // 대상이 회원이 아니다 — 「대상 아이디」로 못 찾고 「대상 종류」로만 걸린다.
        assertThat(row.getTargetLogin()).isNull();
    }

    @DisplayName("🔴 카테고리 삭제 — 이름을 지우기 전에 읽는다(행이 진짜로 사라지므로 유일한 흔적)")
    @Test
    void categoryDeleteKeepsNameInDetail() throws Exception {
        String name = "ZZC4-없앨분류" + suffix;
        UUID id = createCategory(name);

        mockMvc.perform(delete("/api/categories/" + id)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        AdminAuditLog row = onlyRow(id, AuditAction.CATEGORY_DELETE);
        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.CATEGORY);
        // ⚠ 카테고리는 사라졌는데 이름은 남는다 — 감사는 대상보다 오래 산다.
        assertThat(row.getDetail()).isEqualTo(name);
        assertThat(categoryRepository.findById(id)).isEmpty();
    }

    @DisplayName("⚠ 소속 상품이 있어 막힌 삭제는 원장에 안 남는다 — 「일어난 일」만 적는다")
    @Test
    void blockedCategoryDeleteLeavesNoRow() throws Exception {
        // setUp 의 categoryId 에는 상품이 하나 붙어 있다 → CATEGORY_IN_USE 로 막힌다.
        mockMvc.perform(delete("/api/categories/" + categoryId)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isConflict());

        assertThat(rowsOf(categoryId, AuditAction.CATEGORY_DELETE)).isEmpty();
    }

    // ── 공지 ────────────────────────────────────────────────

    private UUID createNotice(String title, boolean pinned) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"content\":\"본문\",\"pinned\":" + pinned + "}";
        String res = mockMvc.perform(post("/api/notices").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(res, "$.data"));
    }

    @DisplayName("🔴 공지 등록 — target_type=NOTICE 가 DB 를 통과한다. E-4 가 풀어 준 자리다")
    @Test
    void noticeCreateIsRecorded() throws Exception {
        String title = "ZZ4-공지" + suffix;
        UUID id = createNotice(title, false);

        AdminAuditLog row = onlyRow(id, AuditAction.NOTICE_CREATE);

        // ✅ 2026-08-20(E-4) 전에는 공지를 고객도 쓸 수 있어 «관리자 조작» 이 아니었다 —
        //    그래서 이 줄은 어제 기능 하나가 닫히면서 비로소 뜻을 갖게 된 줄이다.
        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.NOTICE);
        assertThat(row.getActorId()).isEqualTo(adminId);
        assertThat(row.getDetail()).isEqualTo(title);
    }

    @DisplayName("🔴 공지 수정 — 바뀐 것만 «전→후», 본문은 «바뀜» 만 (detail 1000자를 넘기지 않는다)")
    @Test
    void noticeUpdateRecordsOnlyChanges() throws Exception {
        UUID id = createNotice("ZZ4-원제목" + suffix, false);

        mockMvc.perform(put("/api/notices/" + id).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .content("{\"title\":\"ZZ4-새제목" + suffix + "\",\"content\":\"딴 본문\",\"pinned\":true}"))
                .andExpect(status().isOk());

        String detail = onlyRow(id, AuditAction.NOTICE_UPDATE).getDetail();
        assertThat(detail).contains("제목 ZZ4-원제목" + suffix + "→ZZ4-새제목" + suffix);
        assertThat(detail).contains("상단 고정");
        // 🔴 본문 전/후를 다 실으면 1000자를 넘긴다 — 잘린 원장은 틀린 원장이다.
        assertThat(detail).contains("본문 바뀜").doesNotContain("딴 본문");
    }

    @DisplayName("🔴 공지 수정 — 바뀐 것이 없어도 «변경 없음» 으로 줄이 남는다 (PRODUCT_UPDATE 와 같은 선택)")
    @Test
    void noticeUpdateWithoutChangeStillRecords() throws Exception {
        String title = "ZZ4-그대로" + suffix;
        UUID id = createNotice(title, false);

        mockMvc.perform(put("/api/notices/" + id).contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .content("{\"title\":\"" + title + "\",\"content\":\"본문\",\"pinned\":false}"))
                .andExpect(status().isOk());

        // 관리 화면이 공지 전체를 다시 보내 흔한 경우다. «누가 언제 손댔나» 를 접근 기록으로 본다.
        assertThat(onlyRow(id, AuditAction.NOTICE_UPDATE).getDetail()).isEqualTo("변경 없음");
    }

    @DisplayName("🔴 공지 삭제 — 제목을 지우기 전에 읽는다(공지에도 soft delete 가 없다)")
    @Test
    void noticeDeleteKeepsTitleInDetail() throws Exception {
        String title = "ZZ4-사라질공지" + suffix;
        UUID id = createNotice(title, false);

        mockMvc.perform(delete("/api/notices/" + id)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        AdminAuditLog row = onlyRow(id, AuditAction.NOTICE_DELETE);
        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.NOTICE);
        assertThat(row.getDetail()).isEqualTo(title);
    }

    // ── 문의 답변 ────────────────────────────────────────────────

    private UUID createInquiry(String title) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"content\":\"언제 오나요\","
                + "\"secret\":false,\"imageIds\":[]}";
        String res = mockMvc.perform(post("/api/products/" + productId + "/inquiries").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, memberAuth).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(res, "$.data"));
    }

    private void answer(UUID inquiryId, String text) throws Exception {
        mockMvc.perform(post("/api/inquiries/" + inquiryId + "/answer").contentType(JSON)
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .content("{\"answer\":\"" + text + "\"}"))
                .andExpect(status().isOk());
    }

    @DisplayName("🔴 문의 답변 — 대상은 문의가 아니라 **질문자**다(숨김과 같은 target_id 로 묶인다)")
    @Test
    void inquiryAnswerTargetsAsker() throws Exception {
        String title = "ZZ4-문의" + suffix;
        UUID inquiryId = createInquiry(title);

        answer(inquiryId, "내일 나갑니다");

        AdminAuditLog row = onlyRow(memberId, AuditAction.INQUIRY_ANSWER);
        assertThat(row.getTargetType()).isEqualTo(AuditTargetType.MEMBER);
        // ⚠ 대상이 회원이라 loginId 가 채워진다 — 「대상 아이디」 검색으로 찾을 수 있다.
        assertThat(row.getTargetLogin()).isEqualTo(memberLoginId);
        assertThat(row.getActorId()).isEqualTo(adminId);
        assertThat(row.getDetail()).contains(title);
    }

    @DisplayName("🔴 detail 이 «알림이 나갔나» 를 답한다 — 첫 답변에만 나가고 회수할 수 없다")
    @Test
    void inquiryAnswerDetailSaysWhetherNotificationWentOut() throws Exception {
        UUID inquiryId = createInquiry("ZZ4-두번답변" + suffix);

        answer(inquiryId, "첫 답");
        answer(inquiryId, "고쳐 씀");

        List<AdminAuditLog> rows = rowsOf(memberId, AuditAction.INQUIRY_ANSWER);
        assertThat(rows).hasSize(2);
        // 🔴 두 줄이 **다르게** 적혀야 한다. 같으면 원장만 보고 «알림이 나갔나» 를 못 가른다.
        assertThat(rows).extracting(AdminAuditLog::getDetail)
                .anyMatch(d -> d.endsWith("· 첫 답변"))
                .anyMatch(d -> d.endsWith("· 답변 수정"));
    }
}

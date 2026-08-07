package com.glassvue.domain.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.inquiry.entity.Inquiry;
import com.glassvue.domain.inquiry.entity.InquiryType;
import com.glassvue.domain.inquiry.event.InquiryAnsweredEvent;
import com.glassvue.domain.inquiry.repository.InquiryRepository;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.notification.InquiryNotificationHandler;
import com.glassvue.domain.notification.entity.Notification;
import com.glassvue.domain.notification.entity.NotificationPref;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationPrefRepository;
import com.glassvue.domain.notification.repository.NotificationRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문의 답변 알림 (B-15, 2026-07-31).
 *
 * <p>여기서만 드러나는 것:
 * <ol>
 *   <li><b>답변 API 를 태우면 이벤트가 실제로 나가는가</b> — 단위 테스트는 서비스만 보고,
 *       컨트롤러·권한을 지나 그 자리까지 오는지는 여기서만 확인된다.</li>
 *   <li><b>답변 수정은 이벤트를 만들지 않는가</b> — 알림 스팸이 생기는 유일한 경로다.</li>
 *   <li><b>알림을 끈 회원에겐 만들어지지도 않는가</b> — 새 타입이 opt-out 규칙에 실제로 얹히는지는
 *       타입을 추가한 날 확인해야 한다(설정은 서버가 전 타입을 내려주므로 화면엔 저절로 생긴다).</li>
 * </ol>
 *
 * <p>이벤트는 실제로 {@code @Async}+{@code AFTER_COMMIT} 로 소비되는데 트랜잭션 테스트는 커밋을
 * 하지 않아 리스너가 뜨지 않는다. 그래서 <b>발행</b>은 {@link ApplicationEvents} 로 보고,
 * <b>소비</b>는 핸들러를 직접 불러 결과(알림 행)를 확인한다 — {@code RestockFlowIntegrationTest} 와 같은 방식.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Transactional
class InquiryAnswerNotificationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired InquiryRepository inquiryRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationPrefRepository prefRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired InquiryNotificationHandler handler;
    @Autowired ApplicationEvents events;

    private static final String JSON = "application/json";
    // 리포지토리로 직접 저장하는 픽스처라 비밀번호 정책(E-3)을 타지 않는다(WA §3 — 두 갈래 중 이쪽).
    private static final String PW = "password123";

    private String suffix;
    private String authorLoginId;
    private String adminLoginId;
    private UUID authorId;
    private UUID productId;
    private UUID inquiryId;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        authorLoginId = "iq_" + suffix;
        adminLoginId = "iqa_" + suffix;
        authorId = member(authorLoginId, "ZZ문의작성자" + suffix, Role.USER);
        member(adminLoginId, "ZZ문의관리자" + suffix, Role.ADMIN);

        Category category = categoryRepository.save(Category.builder().name("ZZC-문의" + suffix).build());
        productId = productRepository.save(Product.builder()
                .name("ZZP-문의상품" + suffix).description("문의 알림 테스트").price(10_000L)
                .status(ProductStatus.SELLING).category(category).build()).getId();
        inquiryId = inquiryRepository.save(Inquiry.builder()
                .productId(productId).type(InquiryType.PRODUCT).authorId(authorId).author("ZZ문의작성자" + suffix)
                .title("배송 언제 오나요").content("궁금합니다").secret(false).build()).getId();
    }

    private UUID member(String loginId, String nickname, Role role) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(role).build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    private void answer(String token, String text) throws Exception {
        mockMvc.perform(post("/api/inquiries/" + inquiryId + "/answer").header("Authorization", token)
                        .contentType(JSON).content("{\"answer\":\"" + text + "\"}"))
                .andExpect(status().isOk());
    }

    private long publishedEvents() {
        return events.stream(InquiryAnsweredEvent.class).count();
    }

    // ── 발행 ──────────────────────────────────────────────────

    @Test
    @DisplayName("관리자 답변 → InquiryAnsweredEvent 1건, 대상은 **작성자**")
    void answerPublishesEvent() throws Exception {
        answer(login(adminLoginId), "곧 발송됩니다");

        assertThat(publishedEvents()).isEqualTo(1);
        InquiryAnsweredEvent event = events.stream(InquiryAnsweredEvent.class).findFirst().orElseThrow();
        assertThat(event.authorId()).isEqualTo(authorId);   // 답변한 관리자가 아니다
        assertThat(event.productId()).isEqualTo(productId);
        assertThat(event.inquiryTitle()).isEqualTo("배송 언제 오나요");
    }

    @Test
    @DisplayName("⚠ 답변을 고쳐도 이벤트는 늘지 않는다 — 오타 수정마다 알림이 가면 안 된다")
    void editingAnswerDoesNotPublishAgain() throws Exception {
        String token = login(adminLoginId);
        answer(token, "곧 발송됩니다");
        answer(token, "내일 발송됩니다");   // 같은 API 가 등록·수정 겸용이다

        assertThat(publishedEvents()).isEqualTo(1);   // 두 번 호출했지만 1건
    }

    @Test
    @DisplayName("권한: 일반 회원은 답변할 수 없다(403) — 이벤트도 없다")
    void nonAdminCannotAnswer() throws Exception {
        mockMvc.perform(post("/api/inquiries/" + inquiryId + "/answer")
                        .header("Authorization", login(authorLoginId))
                        .contentType(JSON).content("{\"answer\":\"내가 답변\"}"))
                .andExpect(status().isForbidden());

        assertThat(publishedEvents()).isZero();
    }

    // ── 소비(핸들러 직접 호출) ────────────────────────────────

    @Test
    @DisplayName("이벤트 소비 → 작성자에게 INQUIRY 알림, 링크는 상품 문의 섹션 앵커")
    void handlerCreatesNotification() {
        handler.handle(new InquiryAnsweredEvent(inquiryId, productId, authorId, "배송 언제 오나요"));

        List<Notification> mine = notificationRepository
                .findByMemberIdOrderByCreatedAtDesc(authorId, PageRequest.of(0, 10)).getContent();
        assertThat(mine).anyMatch(n -> n.getType() == NotificationType.INQUIRY
                && n.getLink().equals("/products/" + productId + "#inquiries")
                && n.getMessage().contains("배송 언제 오나요"));
    }

    @Test
    @DisplayName("⚠ 「문의 답변 알림」을 끈 회원에겐 알림이 **만들어지지도 않는다**(타입별 opt-out)")
    void respectsOptOut() {
        prefRepository.save(NotificationPref.of(authorId, NotificationType.INQUIRY, false));

        handler.handle(new InquiryAnsweredEvent(inquiryId, productId, authorId, "배송 언제 오나요"));

        List<Notification> mine = notificationRepository
                .findByMemberIdOrderByCreatedAtDesc(authorId, PageRequest.of(0, 10)).getContent();
        assertThat(mine).noneMatch(n -> n.getType() == NotificationType.INQUIRY);
    }
}

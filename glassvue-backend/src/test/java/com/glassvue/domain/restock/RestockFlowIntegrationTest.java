package com.glassvue.domain.restock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassvue.domain.catalog.dto.ProductUpdateRequest;
import com.glassvue.domain.catalog.dto.VariantRequest;
import com.glassvue.domain.catalog.entity.Category;
import com.glassvue.domain.catalog.entity.Product;
import com.glassvue.domain.catalog.entity.ProductStatus;
import com.glassvue.domain.catalog.entity.ProductVariant;
import com.glassvue.domain.catalog.event.StockReplenishedEvent;
import com.glassvue.domain.catalog.repository.CategoryRepository;
import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.catalog.repository.ProductVariantRepository;
import com.glassvue.domain.catalog.service.command.ProductCommandService;
import com.glassvue.domain.member.entity.Member;
import com.glassvue.domain.member.entity.Role;
import com.glassvue.domain.member.repository.MemberRepository;
import com.glassvue.domain.notification.entity.Notification;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationRepository;
import com.glassvue.domain.restock.repository.RestockSubscriptionRepository;
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
 * 재입고 알림 (B-9) — 신청 API(멱등·소유 경계·401) + 재입고 이벤트 발화(총재고 0→양수) + 발송·소진.
 *
 * <p>여기서만 드러나는 것:
 * ① {@code /api/restock/**} 매처를 빠뜨리면 <b>남의 신청 목록이 인증 없이 열린다</b>(위시리스트와 같은 자리),
 * ② 신청·취소의 <b>멱등성</b>,
 * ③ 재입고 이벤트가 <b>상품 총재고 0→양수에서만</b> 나는지(재고가 있던 상품 복원엔 안 난다),
 * ④ 발송 후 그 상품 구독이 <b>비워지는지</b>(재입고는 일회성).
 *
 * <p>이벤트는 실제로는 {@code @Async}+{@code AFTER_COMMIT} 로 소비되지만, 트랜잭션 테스트는 커밋을
 * 안 하므로 리스너가 안 뜬다. 그래서 ③은 {@link ApplicationEvents} 로 <b>발행됐는지</b>를 보고,
 * ④는 핸들러를 <b>직접 호출</b>해 소비 결과(알림 생성·구독 소진)를 확인한다.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Transactional
class RestockFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RestockSubscriptionRepository subscriptionRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ProductCommandService productCommandService;
    @Autowired RestockNotificationHandler restockNotificationHandler;
    @Autowired ApplicationEvents events;

    private static final String PW = "password123";
    private static final String URL = "/api/restock";

    private String meLoginId;
    private String otherLoginId;
    private UUID meId;
    private UUID productId;      // 품절(총재고 0)
    private UUID variantId;      // productId 의 옵션
    private UUID otherProductId; // 재고 있는 상품
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        meLoginId = "rs_" + suffix;
        otherLoginId = "rso_" + suffix;
        meId = member(meLoginId, "ZZ재입고유저" + suffix);
        member(otherLoginId, "ZZ재입고타인" + suffix);

        Category category = categoryRepository.save(Category.builder().name("ZZC-재입고" + suffix).build());
        // 품절 상품: 옵션 재고 0
        productId = productRepository.save(Product.builder()
                .name("ZZP-품절상품" + suffix).description("재입고 테스트").price(10_000L)
                .status(ProductStatus.SOLD_OUT).category(category).build()).getId();
        variantId = variantRepository.save(ProductVariant.of(productId, "기본", 0, 0L, 0)).getId();
        // 재고 있는 상품
        otherProductId = productRepository.save(Product.builder()
                .name("ZZP-재고상품" + suffix).description("재입고 테스트").price(20_000L)
                .status(ProductStatus.SELLING).category(category).build()).getId();
        variantRepository.save(ProductVariant.of(otherProductId, "기본", 0, 5L, 0));
    }

    private UUID member(String loginId, String nickname) {
        return memberRepository.save(Member.builder()
                .loginId(loginId).password(passwordEncoder.encode(PW))
                .nickname(nickname).role(Role.USER).build()).getId();
    }

    private String login(String loginId) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.accessToken");
    }

    // ── 신청 API ──────────────────────────────────────────────

    @Test
    @DisplayName("재입고 신청 → 내 신청 상품 id 목록에 나온다")
    void subscribeAndProductIds() throws Exception {
        String token = login(meLoginId);
        mockMvc.perform(post(URL + "/" + productId).header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL + "/product-ids").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value(productId.toString()));
    }

    @Test
    @DisplayName("같은 상품을 두 번 신청해도 성공 (멱등 — 더블클릭 대비)")
    void subscribeIsIdempotent() throws Exception {
        String token = login(meLoginId);
        mockMvc.perform(post(URL + "/" + productId).header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(post(URL + "/" + productId).header("Authorization", token)).andExpect(status().isOk());

        mockMvc.perform(get(URL + "/product-ids").header("Authorization", token))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("신청한 적 없는 상품을 취소해도 성공한다 (멱등)")
    void unsubscribeIsIdempotent() throws Exception {
        mockMvc.perform(delete(URL + "/" + productId).header("Authorization", login(meLoginId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("신청은 회원별로 격리된다 — 남의 신청이 내 목록에 안 보인다")
    void isolatedPerMember() throws Exception {
        mockMvc.perform(post(URL + "/" + productId).header("Authorization", login(meLoginId)))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL + "/product-ids").header("Authorization", login(otherLoginId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("없는 상품은 신청할 수 없다 → PRODUCT-404")
    void subscribeUnknownProduct() throws Exception {
        mockMvc.perform(post(URL + "/" + UUID.randomUUID()).header("Authorization", login(meLoginId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT-404"));
    }

    @Test
    @DisplayName("인증 없이 접근하면 401 — 기본이 permitAll이라 매처가 있어야 한다")
    void requiresAuth() throws Exception {
        mockMvc.perform(get(URL + "/product-ids")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL + "/" + productId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(URL + "/" + productId)).andExpect(status().isUnauthorized());
    }

    // ── 재입고 이벤트 발화(총재고 0→양수) ───────────────────────

    @Test
    @DisplayName("품절 상품 재고 복원(0→양수) → StockReplenishedEvent 1건 발행")
    void publishesEventWhenSoldOutRestocked() {
        productCommandService.increaseStock(variantId, 5);

        List<StockReplenishedEvent> published = events.stream(StockReplenishedEvent.class)
                .filter(e -> e.productId().equals(productId)).toList();
        assertThat(published).hasSize(1);
    }

    @Test
    @DisplayName("재고가 있던 상품을 더 채우면(양수→양수) 재입고가 아니다 — 이벤트 없음")
    void noEventWhenStockWasNotZero() {
        UUID otherVariantId = variantRepository.findByProductIdOrderBySortOrderAscCreatedAtAsc(otherProductId)
                .get(0).getId();
        productCommandService.increaseStock(otherVariantId, 5);

        assertThat(events.stream(StockReplenishedEvent.class)
                .anyMatch(e -> e.productId().equals(otherProductId))).isFalse();
    }

    @Test
    @DisplayName("관리자 상품 편집으로 품절이 풀려도(옵션 통째 교체 0→양수) 재입고 이벤트가 난다")
    void adminEditPublishesRestockEvent() {
        Category cat = categoryRepository.findAll().get(0);
        ProductUpdateRequest req = new ProductUpdateRequest(
                "ZZP-품절상품" + suffix, "재입고 테스트", 10_000L, null,
                ProductStatus.SELLING, cat.getId(), null,
                List.of(new VariantRequest("기본", 0L, 7L))); // 재고 7 로 다시 채움
        productCommandService.update(productId, req);

        assertThat(events.stream(StockReplenishedEvent.class)
                .anyMatch(e -> e.productId().equals(productId))).isTrue();
    }

    // ── 발송·소진(핸들러 직접 호출) ────────────────────────────

    @Test
    @DisplayName("재입고 발화 → 신청자에게 RESTOCK 알림이 생기고, 그 상품 구독은 비워진다(일회성)")
    void handlerNotifiesAndClears() {
        subscriptionRepository.save(
                com.glassvue.domain.restock.entity.RestockSubscription.of(meId, productId));

        restockNotificationHandler.handle(new StockReplenishedEvent(productId, "ZZP-품절상품" + suffix));

        List<Notification> mine = notificationRepository
                .findByMemberIdOrderByCreatedAtDesc(meId, PageRequest.of(0, 10)).getContent();
        assertThat(mine).anyMatch(n -> n.getType() == NotificationType.RESTOCK
                && n.getLink().equals("/products/" + productId));
        assertThat(subscriptionRepository.existsByMemberIdAndProductId(meId, productId)).isFalse();
    }
}

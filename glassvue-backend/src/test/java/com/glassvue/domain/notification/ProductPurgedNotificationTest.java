package com.glassvue.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.catalog.event.ProductPurgedEvent;
import com.glassvue.domain.notification.entity.Notification;
import com.glassvue.domain.notification.entity.NotificationType;
import com.glassvue.domain.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품이 영구 삭제되면 그 상품을 가리키던 알림이 사라진다 (2026-09-10 · BACKLOG M-4).
 *
 * <p>🔴 <b>«링크가 어디까지 걸리나» 가 이 기능의 전부다.</b> 링크는 두 모양으로 온다 —
 * {@code /products/<id>} 와 {@code /products/<id>#inquiries}(B-15 문의 답변 알림).
 * 접두사로 안 지우면 <b>뒤엣것만 남아</b> 절반만 고친 것이 된다.
 *
 * <p>⚠ <b>리스너를 태우지 않는다.</b> {@code AFTER_COMMIT} 이라 롤백하는 테스트에서는 안 돈다 —
 * 여기서는 <b>핸들러를 직접</b> 부른다. 리스너가 «수신·위임만» 하는 어댑터라 그래도 되는 것이고,
 * 그 규약이 깨지면 이 테스트가 못 잡는다(그건 규약 쪽 문제다).
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
@Transactional
class ProductPurgedNotificationTest {

    @Autowired NotificationRepository notificationRepository;
    @Autowired ProductPurgedNotificationHandler handler;

    private final UUID member = UUID.randomUUID();

    private UUID save(NotificationType type, String link) {
        return notificationRepository.save(
                Notification.of(member, type, "제목", "내용", link)).getId();
    }

    private boolean exists(UUID id) {
        return notificationRepository.findById(id).isPresent();
    }

    @Test
    @DisplayName("그 상품을 가리키는 알림이 사라진다 — 앵커가 붙은 것도 함께")
    void purgedProductNotificationsAreRemoved() {
        UUID gone = UUID.randomUUID();
        UUID plain = save(NotificationType.STOCK, "/products/" + gone);
        UUID anchored = save(NotificationType.INQUIRY, "/products/" + gone + "#inquiries");

        handler.handle(new ProductPurgedEvent(gone, "사라진 상품"));

        assertThat(exists(plain)).as("링크가 그 상품이면 지운다").isFalse();
        assertThat(exists(anchored))
                .as("🔴 #inquiries 가 붙어도 같은 상품이다 — 접두사로 안 지우면 이것만 남는다")
                .isFalse();
    }

    @Test
    @DisplayName("🔴 대조군: 다른 상품·다른 도메인 알림은 그대로 있다")
    void otherNotificationsSurvive() {
        UUID gone = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID target = save(NotificationType.STOCK, "/products/" + gone);
        UUID otherProduct = save(NotificationType.STOCK, "/products/" + other);
        UUID order = save(NotificationType.ORDER, "/orders/" + UUID.randomUUID());
        UUID noLink = save(NotificationType.MARKETING, null);

        handler.handle(new ProductPurgedEvent(gone, "사라진 상품"));

        assertThat(exists(target)).isFalse();
        assertThat(exists(otherProduct)).as("다른 상품은 안 건드린다").isTrue();
        assertThat(exists(order)).as("주문 알림은 안 건드린다 — 주문은 지워지지 않는다").isTrue();
        assertThat(exists(noLink)).as("링크 없는 알림도 안 건드린다").isTrue();
    }

    @Test
    @DisplayName("⚠ 지울 것이 없으면 아무 일도 안 한다 — 대부분의 purge 가 이 경우다")
    void purgeWithoutNotificationsIsNoop() {
        UUID untouched = save(NotificationType.ORDER, "/orders/" + UUID.randomUUID());

        handler.handle(new ProductPurgedEvent(UUID.randomUUID(), "알림 없던 상품"));

        assertThat(exists(untouched)).isTrue();
    }

    @Test
    @DisplayName("🔴 앞자리가 같은 «다른» id 는 안 지운다 — 접두사 매칭의 경계다")
    void prefixDoesNotBleedIntoOtherIds() {
        UUID gone = UUID.fromString("01a00000-0000-7000-8000-000000000000");
        // 같은 글자로 시작하지만 다른 상품이다. `like '/products/<id>%'` 가 id 전체를 요구하므로 안 걸린다.
        UUID lookalike = UUID.fromString("01a00000-0000-7000-8000-000000000001");
        UUID victim = save(NotificationType.STOCK, "/products/" + gone);
        UUID bystander = save(NotificationType.STOCK, "/products/" + lookalike);

        handler.handle(new ProductPurgedEvent(gone, "사라진 상품"));

        assertThat(exists(victim)).isFalse();
        assertThat(exists(bystander)).as("한 글자 다른 id 는 다른 상품이다").isTrue();
    }

    @Test
    @DisplayName("여러 사람에게 나간 같은 상품 알림이 한 번에 사라진다 (재고 알림은 관리자 전원에게 간다)")
    void allRecipientsAreCleaned() {
        UUID gone = UUID.randomUUID();
        List<UUID> ids = List.of(
                notificationRepository.save(Notification.of(UUID.randomUUID(),
                        NotificationType.STOCK, "재고", "부족", "/products/" + gone)).getId(),
                notificationRepository.save(Notification.of(UUID.randomUUID(),
                        NotificationType.STOCK, "재고", "부족", "/products/" + gone)).getId(),
                notificationRepository.save(Notification.of(UUID.randomUUID(),
                        NotificationType.STOCK, "재고", "부족", "/products/" + gone)).getId());

        handler.handle(new ProductPurgedEvent(gone, "사라진 상품"));

        assertThat(ids.stream().filter(this::exists))
                .as("수신자가 셋이어도 남는 것이 없다 — 09-02 에 39건이 쌓인 모양이 이것이다")
                .isEmpty();
    }
}

package com.glassvue.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.order.entity.DeliveryCarrier;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.global.config.JpaAuditingConfig;
import com.glassvue.global.config.QuerydslConfig;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * existsPurchase(리뷰 구매 인증)의 **주문 상태별** 동작 검증 — 실제 Oracle 대상.
 *
 * <p>이 테스트가 있는 이유: 2026-07-16 주문 상태 확장(ORDERED→PAID→SHIPPED) 때 existsPurchase가
 * {@code status = ORDERED}에 머물러, 결제·발송까지 끝난 고객이 리뷰를 못 쓰는 버그가 있었다.
 * 기존 {@code OrderServiceTest.hasPurchased_delegates}는 리포지토리 위임만 검증해 이걸 못 잡는다.
 * 상태 전이가 결과를 바꾸는 쿼리라 단위 테스트가 아니라 여기서 막는다.
 *
 * <p>DB_HOST 있을 때만 실행(= .env 소싱). @DataJpaTest가 각 테스트를 롤백 → 공유 DB 무오염.
 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 임베디드 대체 금지 → 실제 Oracle
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class OrderRepositoryIntegrationTest {

    @Autowired OrderRepository orderRepository;

    private final UUID memberId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    /** 해당 상품을 담은 주문을 만들고 상태를 전이시킨다. */
    private void orderWithStatus(Consumer<Order> transition) {
        Order order = Order.create(memberId, "ZZ구매자",
                List.of(OrderItem.of(productId, "ZZ-리뷰검증상품", null, 10_000, null, 1)), "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", 3_000, uniqueOrderNo(), null, 0L);
        transition.accept(order);
        orderRepository.save(order);
    }

    @Test
    @DisplayName("ORDERED 주문 → 구매함")
    void ordered_counts() {
        orderWithStatus(o -> {});
        assertThat(orderRepository.existsPurchase(memberId, productId)).isTrue();
    }

    @Test
    @DisplayName("PAID 주문 → 구매함 (ORDERED만 보던 버그의 회귀 방지)")
    void paid_counts() {
        orderWithStatus(Order::pay);
        assertThat(orderRepository.existsPurchase(memberId, productId)).isTrue();
    }

    @Test
    @DisplayName("SHIPPED 주문 → 구매함 (배송까지 받은 고객이 리뷰를 쓸 수 있어야 한다)")
    void shipped_counts() {
        orderWithStatus(o -> { o.pay(); o.ship(DeliveryCarrier.CJ, "123"); });
        assertThat(orderRepository.existsPurchase(memberId, productId)).isTrue();
    }

    @Test
    @DisplayName("CANCELLED 주문 → 구매 안 함")
    void cancelled_excluded() {
        orderWithStatus(Order::cancel);
        assertThat(orderRepository.existsPurchase(memberId, productId)).isFalse();
    }

    @Test
    @DisplayName("주문한 적 없는 상품 → 구매 안 함")
    void otherProduct_excluded() {
        orderWithStatus(o -> {});
        assertThat(orderRepository.existsPurchase(memberId, UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("남의 주문 → 구매 안 함")
    void otherMember_excluded() {
        orderWithStatus(o -> {});
        assertThat(orderRepository.existsPurchase(UUID.randomUUID(), productId)).isFalse();
    }

    /**
     * 테스트용 주문번호. {@code orders.order_no} 에 유니크 제약이 있어(V15)
     * 여러 건을 만드는 테스트가 같은 값을 쓰면 충돌한다 — 매번 다른 값을 준다.
     * (운영 채번은 시퀀스가 하지만 여기선 엔티티를 직접 만들어 저장하므로 서비스를 안 탄다.)
     */
    private static String uniqueOrderNo() {
        return "20260101-" + UUID.randomUUID().toString().substring(0, 8);
    }

}

package com.glassvue.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.order.entity.DeliveryCarrier;
import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import com.glassvue.domain.order.entity.OrderStatus;
import com.glassvue.global.config.JpaAuditingConfig;
import com.glassvue.global.config.QuerydslConfig;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * existsPurchase(리뷰 구매 인증)의 <b>주문 상태별</b> 동작 검증 — 실제 Oracle 대상.
 *
 * <p>이 테스트가 있는 이유: 2026-07-16 주문 상태 확장(ORDERED→PAID→SHIPPED) 때 existsPurchase가
 * {@code status = ORDERED}에 머물러, 결제·발송까지 끝난 고객이 리뷰를 못 쓰는 버그가 있었다.
 * 기존 {@code OrderServiceTest.hasPurchased_delegates}는 리포지토리 위임만 검증해 이걸 못 잡는다.
 * 상태 전이가 결과를 바꾸는 쿼리라 단위 테스트가 아니라 여기서 막는다.
 *
 * <p>🔴 <b>그런데 이 테스트도 같이 어긋났다</b>(2026-08-10 §16-2-1 발견 → 2026-08-11 재작성).
 * 막으려던 코드가 «손으로 늘리는 열거» 였는데 <b>테스트도 정확히 같은 모양</b>(4개 상태를 손으로 적은
 * 4개 메서드)이라, 2026-07-23 에 {@code DELIVERED} 가 생겼을 때 <b>코드와 테스트가 나란히</b> 안 자랐다.
 * 「테스트가 있으니 괜찮다」가 성립하지 않은 자리다.
 *
 * <p>→ 그래서 <b>상태를 여기 적지 않는다.</b> {@link EnumSource} 가 {@code OrderStatus} 를 통째로 돌리고,
 * 기대값은 {@link OrderStatus#isPurchaseProven()} 에서 받는다. <b>상태를 추가하면</b>:
 * <ul>
 *   <li>{@code isPurchaseProven} 의 switch 가 망라를 잃어 <b>운영 코드가 컴파일에서 깨지고</b>,</li>
 *   <li>아래 {@code transitionTo} 의 switch 도 같이 깨져 <b>«그 상태를 어떻게 만드나» 를 반드시 적게 된다.</b></li>
 * </ul>
 * 세 번째가 오지 않게 하는 것은 주석이 아니라 이 두 switch 다.
 *
 * <p>⚠ 이 테스트는 «어떤 상태가 구매 인증인가» 를 <b>스스로 주장하지 않는다</b> — 그 판단은
 * {@code isPurchaseProven} 한 곳에 있다. 여기가 못 박는 것은 <b>«쿼리가 그 판단과 일치한다»</b> 이고,
 * 그래서 정책이 바뀌면 한 줄만 고치면 된다. (판단까지 여기 복사하면 또 두 곳이 되어 같은 사고가 난다.)
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

    /**
     * 각 상태를 <b>실제 전이로</b> 만든다 — 리플렉션이나 setter 로 상태만 꽂지 않는다.
     * 그렇게 하면 «도달할 수 없는 상태» 도 통과해 버려, 검증하는 상태가 운영에 존재한다는 보장이 사라진다.
     *
     * <p>⚠ {@code default} 가 없다 — {@code OrderStatus} 에 값을 추가하면 여기서 컴파일이 깨진다(위 참조).
     */
    private static Consumer<Order> transitionTo(OrderStatus status) {
        return switch (status) {
            case ORDERED -> o -> { };
            case PAID -> Order::pay;
            case SHIPPED -> o -> { o.pay(); o.ship(DeliveryCarrier.CJ, "123"); };
            case DELIVERED -> o -> { o.pay(); o.ship(DeliveryCarrier.CJ, "123"); o.deliver(); };
            case CANCELLED -> o -> o.cancel(null); // 사유는 선택(B-17) — 여기선 상태만 본다
            case RETURN_REQUESTED -> o -> {
                o.pay(); o.ship(DeliveryCarrier.CJ, "123"); o.deliver();
                o.requestReturn("ZZ-반품검증");
            };
            case RETURNED -> o -> {
                o.pay(); o.ship(DeliveryCarrier.CJ, "123"); o.deliver();
                o.requestReturn("ZZ-반품검증"); o.approveReturn();
            };
        };
    }

    /** 해당 상품을 담은 주문을 만들고 상태를 전이시킨다. */
    private void orderWithStatus(Consumer<Order> transition) {
        Order order = Order.create(memberId, "ZZ구매자",
                List.of(OrderItem.of(productId, UUID.randomUUID(), null, "ZZ-리뷰검증상품", null, 10_000, null, 1)), "수령인", "010-1234-5678", "06134", "서울시 강남구 테헤란로 1", "3층", null, 3_000, uniqueOrderNo(), null, 0L, null, 0L);
        transition.accept(order);
        orderRepository.save(order);
    }

    /**
     * <b>7개 상태를 전부 센다.</b> 기대값은 {@code isPurchaseProven} 에서 받으므로, 쿼리와 정책이
     * 어긋나는 순간 그 상태의 케이스가 빨개진다 — 어느 상태가 새로 생기든 자동으로 포함된다.
     */
    @ParameterizedTest(name = "{0} → 구매 인증 = isPurchaseProven")
    @EnumSource(OrderStatus.class)
    @DisplayName("주문 상태 전수 — existsPurchase 가 OrderStatus.isPurchaseProven 과 일치한다")
    void everyStatus_matchesPolicy(OrderStatus status) {
        orderWithStatus(transitionTo(status));

        assertThat(orderRepository.existsPurchase(memberId, productId))
                .as("%s 는 구매 인증 %s 여야 한다", status, status.isPurchaseProven() ? "통과" : "거부")
                .isEqualTo(status.isPurchaseProven());
    }

    /**
     * 위 전수 테스트는 «정책과 일치한다» 만 본다 — 정책이 통째로 뒤집혀도(전부 true/false) 통과한다.
     * 그래서 <b>양쪽이 실제로 존재하는지</b>를 여기서 따로 못 박는다.
     * ⚠ 특히 {@code DELIVERED} 는 <b>이번 사고의 당사자</b>라 이름을 박아 둔다(2026-08-11).
     */
    @Test
    @DisplayName("정책 자체 — DELIVERED 는 구매 인증이고 CANCELLED 는 아니다 (양쪽이 다 있다)")
    void policy_hasBothSides() {
        assertThat(OrderStatus.DELIVERED.isPurchaseProven())
                .as("배송완료가 리뷰를 쓰는 가장 자연스러운 시점이다 — 2026-07-23~08-11 동안 여기가 막혀 있었다")
                .isTrue();
        assertThat(OrderStatus.CANCELLED.isPurchaseProven()).isFalse();
        assertThat(OrderStatus.purchaseProven())
                .containsExactlyInAnyOrder(OrderStatus.ORDERED, OrderStatus.PAID,
                        OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("주문한 적 없는 상품 → 구매 안 함")
    void otherProduct_excluded() {
        orderWithStatus(o -> { });
        assertThat(orderRepository.existsPurchase(memberId, UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("남의 주문 → 구매 안 함")
    void otherMember_excluded() {
        orderWithStatus(o -> { });
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

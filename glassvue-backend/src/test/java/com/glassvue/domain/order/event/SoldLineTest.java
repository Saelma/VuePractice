package com.glassvue.domain.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.glassvue.domain.order.entity.Order;
import com.glassvue.domain.order.entity.OrderItem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 판매량 이벤트 payload — 같은 상품의 여러 옵션이 상품 단위로 합쳐지는지(인기순 기준)의 순수 단위 테스트. */
class SoldLineTest {

    @Test
    @DisplayName("같은 상품의 여러 옵션은 한 줄로 수량이 합쳐진다(판매량은 상품 단위)")
    void aggregatesVariantsByProduct() {
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), "구매자닉",
                List.of(
                        // 상품 A 의 두 옵션 — 판매량에선 A 하나로 3개가 돼야 한다
                        OrderItem.of(productA, UUID.randomUUID(), "빨강", "지바", null, 10_000, null, 2),
                        OrderItem.of(productA, UUID.randomUUID(), "파랑", "지바", null, 10_000, null, 1),
                        OrderItem.of(productB, UUID.randomUUID(), null, "마우스", null, 5_000, null, 4)),
                "수령인", "010-1234-5678", "06134", "서울시 강남구 1", "3층", null, 3_000, "20260101-0001", null, 0L, 0L);

        List<SoldLine> lines = SoldLine.from(order);

        assertThat(lines).hasSize(2);
        assertThat(lines).anySatisfy(l -> {
            assertThat(l.productId()).isEqualTo(productA);
            assertThat(l.quantity()).isEqualTo(3L); // 2 + 1 로 합쳐짐
        });
        assertThat(lines).anySatisfy(l -> {
            assertThat(l.productId()).isEqualTo(productB);
            assertThat(l.quantity()).isEqualTo(4L);
        });
    }
}

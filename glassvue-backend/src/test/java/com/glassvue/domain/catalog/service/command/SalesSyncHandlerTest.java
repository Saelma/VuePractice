package com.glassvue.domain.catalog.service.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.order.event.SoldLine;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesSyncHandlerTest {

    @Mock ProductRepository productRepository;
    @InjectMocks SalesSyncHandler handler;

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    @DisplayName("주문됨 — 상품별 수량을 양수 delta 로 더한다")
    void increaseAddsPositiveDelta() {
        when(productRepository.addSoldCount(any(), anyLong())).thenReturn(1);

        handler.increase(List.of(new SoldLine(a, 2), new SoldLine(b, 5)));

        verify(productRepository).addSoldCount(a, 2L);
        verify(productRepository).addSoldCount(b, 5L);
    }

    @Test
    @DisplayName("취소·반품 — 같은 수량을 음수 delta 로 되돌린다")
    void decreaseAppliesNegativeDelta() {
        when(productRepository.addSoldCount(any(), anyLong())).thenReturn(1);

        handler.decrease(List.of(new SoldLine(a, 2), new SoldLine(b, 5)));

        verify(productRepository).addSoldCount(a, -2L);
        verify(productRepository).addSoldCount(b, -5L);
    }

    @Test
    @DisplayName("갱신 대상 상품이 없어도(삭제됨) 예외 없이 끝난다")
    void missingProductIsNotAnError() {
        when(productRepository.addSoldCount(any(), anyLong())).thenReturn(0);

        handler.increase(List.of(new SoldLine(a, 1))); // 예외 없이 반환되면 통과
    }
}

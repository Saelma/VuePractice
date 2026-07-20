package com.glassvue.domain.catalog.service.command;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassvue.domain.catalog.repository.ProductRepository;
import com.glassvue.domain.review.event.ReviewRatingChangedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingSyncHandlerTest {

    @Mock ProductRepository productRepository;
    @InjectMocks RatingSyncHandler handler;

    private final UUID productId = UUID.randomUUID();

    @Test
    @DisplayName("이벤트의 집계값을 그대로 상품에 반영한다(review를 되묻지 않는다)")
    void appliesEventValues() {
        when(productRepository.updateRating(productId, 4.5, 10L)).thenReturn(1);

        handler.handle(new ReviewRatingChangedEvent(productId, 4.5, 10));

        verify(productRepository).updateRating(productId, 4.5, 10L);
    }

    @Test
    @DisplayName("리뷰가 모두 삭제되면 0으로 되돌린다")
    void resetsToZero() {
        when(productRepository.updateRating(productId, 0.0, 0L)).thenReturn(1);

        handler.handle(new ReviewRatingChangedEvent(productId, 0.0, 0));

        verify(productRepository).updateRating(productId, 0.0, 0L);
    }

    @Test
    @DisplayName("갱신 대상 상품이 없어도(삭제됨) 예외 없이 끝난다")
    void missingProductIsNotAnError() {
        when(productRepository.updateRating(any(), anyDouble(), anyLong())).thenReturn(0);

        handler.handle(new ReviewRatingChangedEvent(productId, 3.0, 1)); // 예외 없이 반환되면 통과
    }
}

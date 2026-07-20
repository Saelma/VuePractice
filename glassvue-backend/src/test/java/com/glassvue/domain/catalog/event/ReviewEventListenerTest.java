package com.glassvue.domain.catalog.event;

import static org.mockito.Mockito.verify;

import com.glassvue.domain.catalog.service.command.RatingSyncHandler;
import com.glassvue.domain.review.event.ReviewRatingChangedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewEventListenerTest {

    @Mock RatingSyncHandler ratingSyncHandler;
    @InjectMocks ReviewEventListener listener;

    @Test
    @DisplayName("리스너는 로직 없이 Handler에 위임만 한다")
    void delegatesToHandler() {
        ReviewRatingChangedEvent event = new ReviewRatingChangedEvent(UUID.randomUUID(), 4.5, 10);
        listener.onReviewRatingChanged(event);
        verify(ratingSyncHandler).handle(event);
    }
}

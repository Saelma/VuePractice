package com.glassvue.domain.catalog.event;

import com.glassvue.domain.catalog.service.command.RatingSyncHandler;
import com.glassvue.domain.review.event.ReviewRatingChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 리뷰 집계 이벤트 리스너(어댑터). StockEventListener·OrderEventListener와 같은 규약 — 수신·위임만.
 *
 * <p>구독자가 catalog에 있는 이유: 갱신 대상이 product의 비정규화 컬럼이라 **catalog 소유 데이터**다.
 * review는 이 리스너의 존재를 모르고, catalog는 review 서비스를 호출하지 않는다(순환 없음).
 *
 * <p>AFTER_COMMIT — 리뷰 저장이 롤백되면 상품 별점도 바뀌면 안 된다.
 * @Async — 리뷰 작성 응답이 상품 갱신을 기다리지 않는다(이벤트 풀 event-*).
 */
@Component
@RequiredArgsConstructor
public class ReviewEventListener {

    private final RatingSyncHandler ratingSyncHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewRatingChanged(ReviewRatingChangedEvent event) {
        ratingSyncHandler.handle(event);
    }
}

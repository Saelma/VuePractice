package com.glassvue.domain.review.event;

import com.glassvue.global.messaging.DomainEvent;
import java.util.UUID;

/**
 * 상품의 리뷰 집계(평균 별점·개수)가 바뀌었을 때 발행되는 도메인 이벤트 — 작성·수정·삭제 모두.
 *
 * <p><b>집계값을 이벤트에 실어 보내는 이유</b>: {@code productId}만 보내면 구독자(catalog)가 평균을
 * 구하려고 review를 되물어야 해서 {@code catalog → review} 의존이 생기고, 기존
 * {@code review → catalog}와 합쳐져 **순환**이 된다. 리뷰는 review 소유이므로 집계도 review가
 * 계산해서 넘기고, catalog는 받아 쓰기만 한다. 덕분에 의존 방향은 review → catalog 한쪽뿐이고
 * MSA 전환 시 폴더째 분리가 유지된다.
 *
 * @param averageRating 리뷰가 없으면 0.0
 * @param reviewCount   리뷰가 없으면 0
 */
public record ReviewRatingChangedEvent(UUID productId, double averageRating, long reviewCount)
        implements DomainEvent {
}

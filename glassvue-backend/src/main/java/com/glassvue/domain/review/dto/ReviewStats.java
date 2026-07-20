package com.glassvue.domain.review.dto;

/**
 * 상품별 리뷰 집계(평균 별점·개수) 프로젝션. 리뷰가 없으면 average=null.
 *
 * <p>avg()는 JPQL이 Double로 주고 리뷰가 없으면 null이라, 반올림·null 처리를 여기 한곳에 모은다
 * (리뷰 목록 응답과 상품 목록 비정규화가 같은 값을 쓰도록).
 */
public record ReviewStats(Double average, long count) {

    /** 소수 첫째 자리 반올림. 리뷰가 없으면 0.0. */
    public double roundedAverage() {
        return average == null ? 0.0 : Math.round(average * 10) / 10.0;
    }
}

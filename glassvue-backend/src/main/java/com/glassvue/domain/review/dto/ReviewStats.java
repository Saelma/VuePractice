package com.glassvue.domain.review.dto;

/** 상품별 리뷰 집계(평균 별점·개수) 프로젝션. 리뷰가 없으면 average=null. */
public record ReviewStats(Double average, long count) {
}

-- 상품 목록 평균별점 — review 집계를 product에 비정규화.
-- review가 ReviewRatingChangedEvent로 밀어넣고 catalog가 받아 쓴다(catalog는 review를 조회하지 않음 = 순환 없음).
-- 목록 조회에서 조인/추가쿼리 0회로 별점을 읽으려는 것.
ALTER TABLE product ADD (
    avg_rating   DOUBLE PRECISION DEFAULT 0 NOT NULL,
    review_count NUMBER           DEFAULT 0 NOT NULL
);

-- 백필 — 이미 쌓여 있는 리뷰를 반영한다. 이게 없으면 기존 상품이 전부 별점 0으로 보이고,
-- 해당 상품에 리뷰가 새로 달릴 때까지 영영 0으로 남는다(이벤트는 "변경 시점"에만 오므로).
-- 반올림 자리수는 ReviewStats.roundedAverage()(소수 첫째 자리)와 일치시킨다.
UPDATE product p SET (avg_rating, review_count) = (
    SELECT NVL(ROUND(AVG(r.rating), 1), 0), COUNT(*)
    FROM review r WHERE r.product_id = p.id
);

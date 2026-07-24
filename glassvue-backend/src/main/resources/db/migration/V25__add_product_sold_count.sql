-- 상품 인기(판매량) 정렬 — order 집계를 product에 비정규화 (2026-07-24, 백로그 B-8).
-- avg_rating(V4)과 같은 이유·같은 방식이다: 목록 정렬에서 order_item 조인/추가쿼리 0회로 읽으려는 것.
-- order가 주문/취소/반품 이벤트로 밀어넣고 catalog가 받아 쓴다(catalog는 order를 조회하지 않음 = 순환 없음).
ALTER TABLE product ADD (
    sold_count NUMBER DEFAULT 0 NOT NULL
);

-- 백필 — 이미 쌓인 주문을 반영한다. 없으면 기존 상품이 전부 판매량 0으로 보이고,
-- 새 주문이 들어올 때까지 0으로 남는다(이벤트는 "변경 시점"에만 오므로 — V4 별점 백필과 같은 이유).
--
-- 집계 정의는 이벤트 동기화와 일치시킨다: 주문(placed) +, 취소 −, 반품승인 −.
-- 그래서 "지금 취소·반품되지 않은" 주문의 수량 합이다 = status NOT IN (CANCELLED, RETURNED).
-- (RETURN_REQUESTED 는 아직 반품 확정 전이라 판매량에 남는다 — 승인 시점에 빠진다.)
UPDATE product p SET sold_count = (
    SELECT NVL(SUM(oi.quantity), 0)
    FROM order_item oi JOIN orders o ON o.id = oi.order_id
    WHERE oi.product_id = p.id
      AND o.status NOT IN ('CANCELLED', 'RETURNED')
);

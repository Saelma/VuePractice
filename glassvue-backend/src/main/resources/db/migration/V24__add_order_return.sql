-- 반품 · 적립금 환불 (2026-07-24, 백로그 C-9)
--
-- 배송완료(DELIVERED) 주문을 고객이 반품 요청 → 관리자 승인 → 옵션 재고 복원 + 결제금액을 적립금으로 환불.
-- PG 가 없어 현금 환불은 못 하지만, 오늘 만든 적립금(V21)이 환불 수단이 된다.
--
-- 상태 흐름 확장:
--   DELIVERED → RETURN_REQUESTED → RETURNED   (승인)
--   RETURN_REQUESTED → DELIVERED              (거절, 원상복귀)
--
-- ⚠ 환불 규칙(코드에 있지만 근거를 여기도 남긴다):
--   · 환불액 = 상품합계 − 쿠폰할인. 사용했던 적립금 + 현금분을 한꺼번에 적립금으로 돌려준다.
--   · 배송비는 환불하지 않는다(운임은 이미 소진됐다 — 실제 커머스와 같다).
--   · 배송완료 때 준 적립(earned_point)은 **회수**한다. 안 하면 "사서 적립받고 반품해서
--     적립+환불 둘 다 챙기는" 포인트 파밍 구멍이 생긴다.
--   · 순변동 = 환불 − 적립회수 ≥ 0 이 항상 성립(환불이 적립보다 크다) → 잔액이 음수가 될 일이 없다.

-- ---------------------------------------------------------------- 반품 스냅샷 컬럼
--
-- 반품 사유·시각. 기존 주문은 반품이 없었으므로 전부 NULL 이 사실이다(nullable, 백필 불필요).
ALTER TABLE orders ADD (
    return_reason       VARCHAR2(500 CHAR),
    return_requested_at TIMESTAMP(9) WITH TIME ZONE,
    returned_at         TIMESTAMP(9) WITH TIME ZONE
);

-- ---------------------------------------------------------------- 주문 상태 CHECK 확장
--
-- V13 에서 ck_orders_status 로 **이름을 붙여 둔** 덕에 동적 탐색 없이 DROP/ADD 두 줄이면 된다.
-- (그전에는 SYS_C##### 자동 생성 이름을 PL/SQL 로 찾아 지워야 했다.)
ALTER TABLE orders DROP CONSTRAINT ck_orders_status;
ALTER TABLE orders ADD CONSTRAINT ck_orders_status
    CHECK (status IN ('ORDERED','PAID','SHIPPED','DELIVERED','CANCELLED','RETURN_REQUESTED','RETURNED'));

-- ---------------------------------------------------------------- 적립금 이력 타입 확장
--
-- 반품 환불은 적립(EARN)도 사용(USE)도 관리자조정(ADJUST)도 아니라 REFUND 로 남긴다.
-- ck_point_history_type 도 V21 에서 이름을 붙여 뒀다.
ALTER TABLE point_history DROP CONSTRAINT ck_point_history_type;
ALTER TABLE point_history ADD CONSTRAINT ck_point_history_type
    CHECK (type IN ('EARN','USE','ADJUST','REFUND'));

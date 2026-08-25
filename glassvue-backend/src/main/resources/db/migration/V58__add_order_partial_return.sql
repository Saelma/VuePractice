-- 부분 반품 (2026-08-25, BACKLOG G-10) — 품목·수량 단위로 반품하고 정산을 나눈다.
--
-- 🔴 **정산 규칙은 BACKLOG G-10 「결정 셋 (2026-08-25, 사용자 확정)」이 원본이다.** 여기 다시 적지
--   않는다(같은 사실을 두 곳에 적으면 한쪽만 고쳐진다 — CLAUDE.md). 스키마가 그 규칙의
--   **어느 부분을 떠받치는지만** 적는다. V57(부분 취소)과 **같은 모양이고 같은 이유**다.
--
-- ---------------------------------------------------------------- 🔴 왜 취소 칸을 재사용하지 않나
--
-- G-10 결정 1. `cancelled_quantity` 를 «빠진 수량» 으로 넓히면 구현은 훨씬 작지만,
-- **주문 테이블만 보고는 «취소로 빠졌나 반품으로 빠졌나» 를 못 가른다** — 사유가
-- `stock_history.reason`(CANCEL/RETURN)에만 남는다. 그 둘은 **돈이 다르게 움직이는** 사건이다:
--
--     취소 환불 = 반품금액 − 쿠폰몫 − 적립금몫   (쓴 적립금만 계정으로, 돈은 seam)
--     반품 환불 = 반품금액 − 쿠폰몫             (현금결제분 + 사용적립금을 **함께** 적립금으로)
--
-- → 값이 갈리므로 **누적 칸도 갈린다.** `returned_*` 셋이 `cancelled_*` 셋과 짝을 이룬다.
--
--     남은 상품합계 = total_price     − cancelled_items_total     − returned_items_total
--     남은 쿠폰할인 = coupon_discount − cancelled_coupon_discount − returned_coupon_discount
--     남은 적립금   = used_point      − cancelled_point           − returned_point
--
-- ---------------------------------------------------------------- 🔴 왜 reversed_earned_point 가 필요한가
--
-- 반품은 배송완료 적립을 **회수**한다. 전량 반품이던 시절엔 `earned_point` 전액이라 스냅샷 한 칸으로
-- 충분했지만, **부분이 생기면 «지금까지 얼마 회수했나» 를 알아야** 다음 회차를 계산할 수 있다.
-- ⚠ 내림 배분은 **경로 의존**이다 — 어느 품목을 먼저 반품했느냐에 따라 1원이 다른 자리에 남는다.
--   **유도할 수 있는 값이 아니다.** V57 이 `cancelled_point` 를 둔 것과 글자 그대로 같은 이유다.
--
-- ---------------------------------------------------------------- 🔴 왜 요청 수량 칸이 따로 있나
--
-- G-10 결정 2 — **고객이 요청 때 품목·수량을 고르고, 승인은 «요청한 대로 해 준다»**
-- (`ReturnRejectRequest` 주석이 정한 규약). 요청과 승인 사이에 관리자 판단이 들어가므로
-- **그 사이에 요청 내용을 들고 있을 자리**가 필요하다 → `return_requested_quantity`.
-- ⚠ 이것은 «확정된 것» 이 아니라 «요청된 것» 이다. 거절되면 0 으로 지워진다
--   (`return_rejected_reason` 이 요청을 지우는 것과 같은 자리).
--
-- ---------------------------------------------------------------- ⚠ 배송비 컬럼이 없는 이유
--
-- G-10 결정 3 — **반품은 배송비를 안 돌려준다.** 취소와 갈라지는 유일한 지점이다(물건이 이미 나갔다).
-- 🔴 지금 `refundableAmount()` 가 이미 배송비를 안 더하므로 **바꾸는 것이 아니라 명문화**이고,
--   그래서 이 마이그레이션이 `shipping_fee` 를 손댈 일이 없다. V57 과 같은 결론, 다른 이유다.
--
-- ---------------------------------------------------------------- 구 jar 영향 (WA §5)
--
-- ⚠ **일곱 컬럼 전부 «추가 + DEFAULT 0 + NOT NULL»** 이고 시각 컬럼 하나는 NULL 허용이다.
--   V57 이 같은 모양으로 이미 지나갔다. 구 jar 는 이 컬럼을 안 읽고, INSERT 에서 빠져도 DEFAULT 가
--   채운다 → **동작이 안 바뀐다.** 🔴 기존 행의 0 은 **모르는 값이 아니라 아는 값**이다
--   (부분 반품이 없던 시절의 주문이라 실제로 0 이다) — V55 가 «모르는 값은 NULL» 로 간 것과 갈린다.
--
-- ⚠ CHECK 는 전부 새 컬럼에만 걸린다. 기존 행은 returned_* = 0 이라 통과한다(적용 전 조회로 확인).
-- ⚠ 시각 컬럼은 감사 컬럼 관례대로 `TIMESTAMP(9) WITH TIME ZONE` 이다 —
--   plain 이면 validate 는 통과하고 **읽을 때 ORA-18716** 이 난다(V26 사고).

ALTER TABLE order_item ADD (
    returned_quantity         NUMBER DEFAULT 0 NOT NULL,
    return_requested_quantity NUMBER DEFAULT 0 NOT NULL,
    returned_at               TIMESTAMP(9) WITH TIME ZONE
);

-- 🔴 취소분과 반품분은 **합쳐서** 원본 수량을 넘을 수 없다. 한쪽만 보면 «1개를 취소하고 같은 1개를
--    반품하는» 조합이 통과한다 — 그러면 재고가 2개 돌아간다(2026-08-24 사고 ②와 같은 모양).
ALTER TABLE order_item ADD CONSTRAINT ck_order_item_returned_qty
    CHECK (returned_quantity >= 0 AND cancelled_quantity + returned_quantity <= quantity);

-- ⚠ 요청 수량은 **아직 살아 있는 수량** 안에서만 유효하다(이미 빠진 것을 또 요청할 수 없다).
ALTER TABLE order_item ADD CONSTRAINT ck_order_item_return_req_qty
    CHECK (return_requested_quantity >= 0
       AND cancelled_quantity + returned_quantity + return_requested_quantity <= quantity);

ALTER TABLE orders ADD (
    returned_items_total     NUMBER DEFAULT 0 NOT NULL,
    returned_coupon_discount NUMBER DEFAULT 0 NOT NULL,
    returned_point           NUMBER DEFAULT 0 NOT NULL,
    reversed_earned_point    NUMBER DEFAULT 0 NOT NULL
);

-- 🔴 회수된 몫은 **취소분과 합쳐서** 원본을 넘을 수 없다. V57 의 `ck_orders_cancelled_within` 은
--    취소분만 봤으므로, 반품분을 따로만 검사하면 **둘을 합쳐 원본을 넘는 조합**이 빠져나간다.
--    배분식이 어긋나는 순간을 DB 가 마지막으로 잡는 자리다(앱 가드가 먼저 막지만 WA §2-4-2).
ALTER TABLE orders ADD CONSTRAINT ck_orders_returned_within
    CHECK (returned_items_total     >= 0 AND cancelled_items_total     + returned_items_total     <= total_price
       AND returned_coupon_discount >= 0 AND cancelled_coupon_discount + returned_coupon_discount <= coupon_discount
       AND returned_point           >= 0 AND cancelled_point           + returned_point           <= used_point
       AND reversed_earned_point    >= 0 AND reversed_earned_point                                <= earned_point);

COMMENT ON COLUMN order_item.returned_quantity IS
    '반품 승인으로 빠진 수량(G-10). cancelled_quantity 와 합쳐 quantity 를 넘을 수 없다';
COMMENT ON COLUMN order_item.return_requested_quantity IS
    '고객이 반품 요청한 수량(G-10). 아직 확정 아님 — 승인되면 returned_quantity 로 옮겨가고 거절되면 0 이 된다';
COMMENT ON COLUMN order_item.returned_at IS
    '마지막 반품 승인 시각(G-10). 회차별 이력이 아니다 — 관리자 조작 이력은 admin_audit_log 에 있다';
COMMENT ON COLUMN orders.returned_items_total IS
    '반품으로 빠진 상품금액 누적(G-10). 남은 상품합계 = total_price - cancelled_items_total - 이 값';
COMMENT ON COLUMN orders.returned_coupon_discount IS
    '반품으로 회수된 쿠폰 할인 몫 누적(G-10). 금액 비례·내림';
COMMENT ON COLUMN orders.returned_point IS
    '반품으로 회수된 사용 적립금 몫 누적(G-10). 🔴 환불 계산에는 안 쓰인다(반품 환불액에 이미 포함) — 남은 적립금을 낮추는 용도다';
COMMENT ON COLUMN orders.reversed_earned_point IS
    '반품으로 회수한 배송완료 적립 누적(G-10). earned_point 는 스냅샷이라 «얼마나 회수했나» 를 담을 곳이 없어 신설했다';

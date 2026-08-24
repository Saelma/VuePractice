-- 부분 취소 (2026-08-24, BACKLOG G-4) — 품목·수량 단위로 취소하고 정산을 나눈다.
--
-- 🔴 **정산 규칙은 BACKLOG G-4 「결정 (2026-08-24, 사용자 확정)」이 원본이다.** 여기 다시 적지 않는다
--   (같은 사실을 두 곳에 적으면 한쪽만 고쳐진다 — CLAUDE.md). 스키마가 그 규칙의 **어느 부분을
--   떠받치는지만** 적는다.
--
-- ---------------------------------------------------------------- 🔴 왜 원본 금액을 안 고치나
--
-- `orders.total_price` 주석이 못박고 있다 — *"이 의미는 바꾸지 않는다. 바꾸면 과거 주문의 숫자가
-- 무엇인지 알 수 없어진다."* `coupon_discount` 는 아예 엔티티에서 `updatable = false` 다.
--
-- → **원본 셋은 그대로 두고, «취소로 회수된 몫» 을 따로 누적한다.** 지금 받을 금액은 뺄셈으로 나온다:
--
--     남은 상품합계 = total_price            − cancelled_items_total
--     남은 쿠폰할인 = coupon_discount        − cancelled_coupon_discount
--     남은 적립금   = used_point             − cancelled_point
--     결제금액      = 남은상품합계 − 남은쿠폰할인 − 남은적립금 + shipping_fee
--     누적 환불액   = cancelled_items_total − cancelled_coupon_discount − cancelled_point
--
-- ⚠ **`shipping_fee` 는 이 마이그레이션이 손대지 않는다.** 무료배송 기준을 소급하지 않기로 했고
--   (G-4 결정 2), 그래서 부분 취소로 **배송비가 움직일 일이 없다.** 컬럼이 필요 없다는 뜻이다.
--
-- ---------------------------------------------------------------- 🔴 왜 잔돈 컬럼이 없나
--
-- 배분은 «남은 값에서 매번 다시 계산 · 내림» 이라 **잔돈이 저절로 마지막 품목에 흡수된다**
-- (G-4 검산: 1,000원을 셋에 나누면 333·333·**334**). 위 세 컬럼이 곧 «아직 안 나눠진 몫» 이므로
-- 잔돈을 따로 들고 있을 자리가 없다.
--
-- ---------------------------------------------------------------- ⚠ 이력은 여기 담지 않는다
--
-- `cancelled_at` 은 **마지막 취소 시각**이다. 한 품목을 수량으로 나눠 여러 번 취소하면 앞선 시각은
-- 덮인다. 🔴 **그건 감수한다** — `Order.requestReturn` 주석이 같은 자리에서 정한 것과 같다:
-- *"이력이 필요해지면 별도 테이블이 답이지 컬럼을 늘리는 게 아니다."*
-- 관리자 조작은 `admin_audit_log` 에 회차마다 남으므로 «누가 언제» 는 그쪽에서 온전히 읽힌다.
--
-- ---------------------------------------------------------------- 구 jar 영향 (WA §5)
--
-- ⚠ **다섯 컬럼 전부 «추가 + DEFAULT 0 + NOT NULL» 이다.** `orders.shipping_fee`·`used_point` 가
--   이미 같은 모양이라(실측: DATA_DEFAULT=0, NULLABLE=N) 이 조합은 이 스키마의 선례가 있다.
--   구 jar 는 이 컬럼을 안 읽고, INSERT 에서 빼도 DEFAULT 가 채운다 → **동작이 안 바뀐다.**
--   🔴 기존 행은 전부 «취소된 몫 0» 인데 그게 **사실이다**(부분 취소가 없던 시절의 주문이다) —
--   V55 가 «모르는 값은 NULL» 로 간 것과 다른 이유다. 여기서 0 은 모르는 값이 아니라 아는 값이다.
--
-- ⚠ CHECK 둘은 **새 컬럼에만 걸린다.** 기존 행은 cancelled_quantity=0 이고 quantity >= 1 이라
--   전부 통과한다(적용 전 조회로 확인). 구 jar 가 만드는 행도 0 이라 걸리지 않는다.
--
-- ⚠ `NUMBER` 라 §2-2-1(VARCHAR2 CHAR) 은 해당 없다. 시각 컬럼은 §감사 컬럼 관례대로
--   `TIMESTAMP(9) WITH TIME ZONE` 이다 — plain 이면 validate 는 통과하고 **읽을 때 ORA-18716** 이 난다(V26).

ALTER TABLE order_item ADD (
    cancelled_quantity NUMBER DEFAULT 0 NOT NULL,
    cancelled_at       TIMESTAMP(9) WITH TIME ZONE
);

ALTER TABLE order_item ADD CONSTRAINT ck_order_item_cancelled_qty
    CHECK (cancelled_quantity >= 0 AND cancelled_quantity <= quantity);

ALTER TABLE orders ADD (
    cancelled_items_total     NUMBER DEFAULT 0 NOT NULL,
    cancelled_coupon_discount NUMBER DEFAULT 0 NOT NULL,
    cancelled_point           NUMBER DEFAULT 0 NOT NULL
);

-- 🔴 회수된 몫은 **원본을 넘을 수 없다.** 넘으면 환불이 결제금액보다 커진다 —
--    배분식이 어긋나는 순간을 DB 가 마지막으로 잡는 자리다(앱 가드가 먼저 막지만 §2-4-2).
ALTER TABLE orders ADD CONSTRAINT ck_orders_cancelled_within
    CHECK (cancelled_items_total     >= 0 AND cancelled_items_total     <= total_price
       AND cancelled_coupon_discount >= 0 AND cancelled_coupon_discount <= coupon_discount
       AND cancelled_point           >= 0 AND cancelled_point           <= used_point);

-- 감사: 부분 취소는 전체 취소와 **돈이 다르게 움직이는** 관리자 조작이라 별도 행동으로 남긴다.
-- ⚠ enum 값을 늘리면 CHECK 를 손으로 갈아야 한다(ddl-auto=update 가 못 고친다 — 2026-07-16 ORA-02290).
-- ⚠ 'ORDER_ITEM_CANCEL' 은 17자다. action 컬럼이 VARCHAR2(20 CHAR) 라 들어간다
--   (AuditAction 주석: ORDER_RETURN_APPROVE 가 정확히 20자로 상한이다).
ALTER TABLE admin_audit_log DROP CONSTRAINT ck_admin_audit_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('MEMBER_SUSPEND', 'MEMBER_UNSUSPEND', 'MEMBER_ROLE_CHANGE', 'MEMBER_DELETE',
                      'ORDER_CANCEL', 'ORDER_ITEM_CANCEL',
                      'ORDER_SHIP', 'ORDER_DELIVER', 'ORDER_RETURN_APPROVE', 'ORDER_RETURN_REJECT',
                      'REVIEW_HIDE', 'REVIEW_UNHIDE', 'INQUIRY_HIDE', 'INQUIRY_UNHIDE',
                      'PRODUCT_DELETE', 'PRODUCT_RESTORE',
                      'PRODUCT_CREATE', 'PRODUCT_UPDATE',
                      'COUPON_CREATE', 'COUPON_ISSUE', 'COUPON_WELCOME_SET',
                      'DISCOUNT_CREATE', 'DISCOUNT_UPDATE', 'DISCOUNT_DELETE',
                      'CATEGORY_CREATE', 'CATEGORY_DELETE',
                      'NOTICE_CREATE', 'NOTICE_UPDATE', 'NOTICE_DELETE',
                      'INQUIRY_ANSWER'));

COMMENT ON COLUMN order_item.cancelled_quantity IS
    '부분 취소된 수량(G-4). 0 이면 안 취소, quantity 와 같으면 이 품목은 전량 취소됐다';
COMMENT ON COLUMN order_item.cancelled_at IS
    '마지막 부분 취소 시각(G-4). 회차별 이력이 아니다 — 관리자 조작 이력은 admin_audit_log 에 있다';
COMMENT ON COLUMN orders.cancelled_items_total IS
    '부분 취소로 빠진 상품금액 누적(G-4). 남은 상품합계 = total_price - 이 값';
COMMENT ON COLUMN orders.cancelled_coupon_discount IS
    '부분 취소로 회수된 쿠폰 할인 몫 누적(G-4). 금액 비례·내림. 남은 할인 = coupon_discount - 이 값';
COMMENT ON COLUMN orders.cancelled_point IS
    '부분 취소로 되돌린 사용 적립금 몫 누적(G-4). 금액 비례·내림. 남은 사용액 = used_point - 이 값';

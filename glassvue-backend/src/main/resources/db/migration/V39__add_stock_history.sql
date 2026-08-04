-- 재고 변경 이력 (2026-08-04, 백로그 B-19)
--
-- product_variant.stock 은 **현재값 하나**뿐이고 주문·취소·반품·관리자 편집이 전부 같은 컬럼을
-- 덮어쓴다 — "어제 10개였는데 왜 3개지?" 에 답할 수 없다. 적립금 이력(point_history, V21)이
-- 잔액의 **원장** 역할을 하는 것과 같은 구조를 재고에도 둔다.
--
-- ⚠ 원장의 성질: **SUM(quantity) = 현재 재고**. 이게 성립해야 "언제부터 틀어졌는지"를 이력만으로
--    짚을 수 있다. 그래서 관리자가 옵션을 새로 등록하는 것(ADMIN_CREATE)도 첫 줄로 남긴다 —
--    안 남기면 합계가 현재 재고보다 항상 초기재고만큼 모자라 원장이 성립하지 않는다.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- 🔴 왜 variant_id 만으로는 안 되는가 (2026-08-04 착수 실측)
--
-- 백로그는 "point_history 선례를 그대로 따르면 된다" 고 적었지만 **그대로는 안 된다**:
-- point_history.member_id 는 안 바뀌는데 **variant_id 는 바뀐다.**
--   ProductCommandService.update() 가 옵션을 **통째로 교체**하고(deleteAll + saveVariants),
--   ProductVariant.of() 가 새 엔티티를 만들어 PK(UUIDv7)를 새로 발급한다.
--   → 관리자가 상품을 **한 번만 저장해도 모든 옵션의 id 가 바뀐다.**
--
-- variant_id 로만 조회하면 편집 한 번에 이력이 통째로 끊긴다. order_item 이 **이미 같은 문제를
-- 스냅샷으로 풀었으므로**(옵션이 지워져도 과거 주문 표시가 멀쩡하도록 variant_name 을 실어 둔다)
-- 같은 방식으로 간다:
--   * 조회 기준은 **product_id + variant_name**
--   * variant_id 는 **느슨한 참조로만** 남긴다(편집으로 사라질 수 있어 NULL 허용, FK 아님)
--
-- 버린 안: 옵션 교체를 부분 갱신으로 바꿔 variant_id 를 안정시키는 것. 주문·장바구니가 얹혀 있는
-- 자리라 B-19 범위를 넘고, ProductCommandService 가 통째 교체를 고른 이유(단순·어긋날 여지 없음)를
-- B-19 때문에 뒤집지 않는다.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE stock_history (
    id           RAW(16)            NOT NULL,
    -- 조회 기준. 상품이 지워지면 이력도 함께 지운다(옵션과 같은 판단 — V22 의 CASCADE).
    product_id   RAW(16)            NOT NULL,
    -- 옵션명 **스냅샷**. 이력을 잇는 실제 키다(위 설명 참조). order_item.variant_name 과 같은 성격.
    variant_name VARCHAR2(100 CHAR) NOT NULL,
    -- 변동 시점의 옵션 id. **편집으로 사라질 수 있어 NULL 허용**이고 FK 도 걸지 않는다 —
    -- 이력이 옵션보다 오래 살아야 하므로 옵션이 지워질 때 같이 지워지면 안 된다.
    variant_id   RAW(16),
    -- ORDER: 주문 차감 / CANCEL: 주문 취소 복원 / RETURN: 반품 승인 복원
    -- ADMIN_CREATE: 상품 등록 시 초기 재고 / ADMIN_EDIT: 관리자 편집(옵션 추가·삭제 포함)
    -- ⚠ 값을 더하려면 이 CHECK 를 넓히는 마이그레이션이 따로 필요하다(V35 선례) —
    --    ddl-auto 는 CHECK 를 못 고쳐 ORA-02290 이 난다.
    reason       VARCHAR2(20 CHAR)  NOT NULL
                 CONSTRAINT ck_stock_history_reason
                 CHECK (reason IN ('ORDER','CANCEL','RETURN','ADMIN_CREATE','ADMIN_EDIT')),
    -- 부호 있는 값. 차감은 −, 복원·입고는 +. point_history.amount 와 같은 규칙 —
    -- 부호를 reason 으로 유추하지 않고 값에 담으면 합계를 그냥 SUM 으로 낼 수 있다.
    quantity     NUMBER(19,0)       NOT NULL,
    -- 변동 **직후** 재고. 원장이 어긋났을 때 어느 줄부터 틀어졌는지 짚는 용도(balance_after 와 같다).
    stock_after  NUMBER(19,0)       NOT NULL,
    -- 어느 주문 때문인지. 관리자 조작은 NULL. FK 아님(느슨한 참조 — order 는 다른 도메인).
    order_id     RAW(16),
    -- 행위자(관리자). **주문 경로는 NULL 이다** — order_id 로 누구 주문인지 되짚을 수 있어
    -- 같은 정보를 두 번 적지 않는다. 관리자 조작만 여기에 남는다.
    actor_id     RAW(16),
    -- 행위자 닉네임 스냅샷(admin_audit_log.actor_name 과 같은 이유 — 탈퇴·개명 후에도 읽혀야 한다).
    actor_name   VARCHAR2(50 CHAR),
    created_at   TIMESTAMP(9) WITH TIME ZONE,
    updated_at   TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    -- 상품이 지워지면 이력도 간다. 옵션(product_variant)에는 FK 를 걸지 않는다 — 위 설명대로
    -- 이력이 옵션보다 오래 살아야 하기 때문이다.
    CONSTRAINT fk_stock_history_product FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE CASCADE
);

-- 유일한 조회 경로는 "이 상품의 재고 이력"(최신순)이다.
-- 옵션별로 좁혀 보는 것도 같은 인덱스로 커버된다(선두 컬럼이 product_id).
CREATE INDEX idx_stock_history_product ON stock_history (product_id, created_at);

COMMENT ON TABLE stock_history IS
    '재고 변동 원장(append-only). SUM(quantity) = 현재 재고. 조회 기준은 product_id + variant_name';
COMMENT ON COLUMN stock_history.variant_id IS
    '변동 시점의 옵션 id. 관리자 편집으로 옵션이 통째 교체되면 무효해진다(FK 아님) — 이력은 variant_name 으로 잇는다';
COMMENT ON COLUMN stock_history.actor_id IS
    '관리자 조작의 행위자. 주문 경로(ORDER/CANCEL/RETURN)는 NULL 이고 order_id 로 되짚는다';

-- ⚠ 백필하지 않는다. 과거의 변동은 **기록이 없는 게 사실**이고, 현재 재고를 첫 줄로 지어 넣으면
--    "언제 그 숫자가 됐는지" 가 오늘 날짜로 거짓이 된다(V37 이 동의 시각을 백필하지 않은 것과 같은 판단 —
--    "NULL 은 정직하고, 지어낸 값은 근거로 쓰이는 순간 틀린 결정을 만든다").
--    ⚠ 따라서 **기존 상품은 한동안 SUM(quantity) ≠ 현재 재고**다. 화면이 이걸 합계로 검산하면 안 된다.

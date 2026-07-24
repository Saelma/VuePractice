-- 상품 옵션 (2026-07-24, 백로그 C-8)
--
-- ============================================================================
-- "상품 1 = 재고 1" 을 "상품 1 = 옵션 N, 재고는 옵션마다" 로 바꾼다
-- ============================================================================
-- 지금까지 재고는 product.stock 에 있었다. 옵션(검정 M / 흰색 L …)이 들어오면 재고가 조합마다
-- 달라야 하므로, 재고를 **옵션(product_variant)으로 내린다.** 그러면 장바구니·주문·재고 차감/복원이
-- 전부 옵션 단위로 바뀐다 — 이 마이그레이션이 그 파급의 시작점이다.
--
-- 모델은 **단일 옵션 목록**이다(사용자 결정): 옵션그룹(사이즈·색상)을 따로 모델링하지 않고,
-- 구매 가능한 조합을 평판화해 나열한다("검정 / M" 이 한 줄). 실제 시스템의 SKU 방식과 같고,
-- 조합 폭발이 없다.
--
-- ⚠ **모든 상품이 옵션을 최소 1개 갖는다.** 과자처럼 옵션 없는 상품도 "기본" 옵션 하나를 만든다.
--    그러면 재고가 **항상 옵션에 있고**, "옵션 있는 상품 / 없는 상품" 을 코드가 갈라 처리할 필요가 없다
--    (V18 에서 배운 "구조로 막는다" 와 같은 방식). 화면은 옵션이 2개 이상일 때만 선택 UI 를 보여준다.

CREATE TABLE product_variant (
    id          RAW(16)             NOT NULL,
    -- 진짜 FK 를 건다 — catalog 도메인 **안**이다(product ↔ variant). member_address 가 member 에
    -- FK 를 건 것과 같은 판단. 상품이 지워지면 옵션도 함께 지운다.
    product_id  RAW(16)             NOT NULL,
    -- 옵션 표시명. "기본" / "검정 / M" 등. 사람이 고르는 이름이라 자유 문자열이다.
    name        VARCHAR2(100 CHAR)  NOT NULL,
    -- 기본가(product.price) 대비 **가격차**. "L +2000" 이면 2000. 음수도 가능(할인 옵션).
    -- 실제 판매가 = product.price + price_delta. delta 를 두는 이유는 기본가를 바꾸면 옵션들이
    -- 함께 따라오게 하기 위해서다(옵션마다 절대가를 두면 기본가 변경 시 전부 손봐야 한다).
    price_delta NUMBER(19,0)        DEFAULT 0 NOT NULL,
    stock       NUMBER(19,0)        DEFAULT 0 NOT NULL
                CONSTRAINT ck_product_variant_stock CHECK (stock >= 0),
    -- 표시 순서. 관리자가 정한 순서를 유지한다(생성 시각순으로는 재정렬을 표현할 수 없다).
    sort_order  NUMBER(10,0)        DEFAULT 0 NOT NULL,
    created_at  TIMESTAMP(9) WITH TIME ZONE,
    updated_at  TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_variant_product FOREIGN KEY (product_id)
        REFERENCES product (id) ON DELETE CASCADE
);

-- "이 상품의 옵션 목록"(정렬 포함)이 주 조회 경로다.
CREATE INDEX idx_product_variant_product ON product_variant (product_id, sort_order);

-- ---------------------------------------------------------------- 기존 상품 이관
--
-- 상품마다 "기본" 옵션 하나를 만들고 product.stock 을 그 옵션으로 옮긴다.
-- 이게 없으면 기존 상품은 옵션이 0개라 **주문 자체가 불가능**해진다(재고가 옵션에만 있으므로).
--
-- id 는 UUIDv7 레이아웃을 조립한다(V18·V21 과 같은 방식) — SYS_GUID() 는 v7 이 아니라 규칙 위반이다.
INSERT INTO product_variant (id, product_id, name, price_delta, stock, sort_order, created_at, updated_at)
SELECT
    HEXTORAW(
        LPAD(TO_CHAR(ROUND((CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS DATE) - DATE '1970-01-01')
                           * 86400000), 'FMXXXXXXXXXXXX'), 12, '0')
        || '7' || SUBSTR(RAWTOHEX(SYS_GUID()), 1, 3)
        || 'A' || SUBSTR(RAWTOHEX(SYS_GUID()), 4, 15)
    ),
    p.id,
    '기본',
    0,
    p.stock,   -- 현재 재고를 그대로 옮긴다
    0,
    SYSTIMESTAMP,
    SYSTIMESTAMP
FROM product p;

-- ⚠ product.stock 은 **남긴다**(DROP 하지 않는다). 운영 구 jar 의 Product 엔티티가 stock 을 매핑하고
--    재고 차감 JPQL(decreaseStock)도 그 컬럼을 쓴다. 여기서 지우면 배포 전에 운영이 깨진다
--    (V18 의 expand/contract 와 같은 이유). 신 코드는 엔티티 매핑을 걷어내 안 읽고, DROP 은 V23.

-- ---------------------------------------------------------------- 주문 옵션 스냅샷
--
-- order_item 이 어느 옵션이었는지 남긴다.
--   variant_id  : **취소 시 재고를 되돌릴 대상.** 옛 주문은 아래에서 각 상품의 기본 옵션으로 백필한다
--                 — 백필하지 않으면 옛 주문 취소가 "어느 옵션에 복원하지?" 에 답이 없어진다.
--   variant_name: 표시용 스냅샷(쿠폰·배송지와 같은 원칙). 옛 주문은 옵션이라는 개념이 없었으므로 NULL.
--                 화면은 이 값이 있을 때만 옵션명을 보여준다("기본" 노이즈를 옛 주문에 남기지 않는다).
ALTER TABLE order_item ADD (
    variant_id   RAW(16),
    variant_name VARCHAR2(100 CHAR)
);

-- 옛 order_item 을 각 상품의 기본 옵션(방금 만든 유일한 옵션)에 연결한다.
-- 이관 시점엔 상품마다 옵션이 정확히 하나라 서브쿼리가 한 행을 돌려준다.
UPDATE order_item oi
   SET oi.variant_id = (SELECT v.id FROM product_variant v WHERE v.product_id = oi.product_id)
 WHERE oi.variant_id IS NULL
   AND EXISTS (SELECT 1 FROM product_variant v WHERE v.product_id = oi.product_id);
-- variant_name 은 백필하지 않는다 — 옛 주문엔 옵션 선택이 없었다(NULL 이 사실).

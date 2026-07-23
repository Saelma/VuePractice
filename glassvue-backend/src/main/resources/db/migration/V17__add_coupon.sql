-- 쿠폰 (2026-07-23)
--
-- CLAUDE.md 가 도메인 예시로 든 `domain.coupon` 의 자리다. 금액 계산의 토대(배송비 V14 · 정가 V16)를
-- 먼저 깔아 뒀으므로 이제 그 위에 얹는다.
--
-- 주문 금액 계산 순서: **상품합계 → 쿠폰할인 → 배송비 → 결제금액**
--
-- ⚠ **무료배송 기준은 "할인 전" 상품합계**다(사용자 결정, 2026-07-23).
--    쿠폰을 썼다고 배송비가 붙으면 고객이 손해 본 기분이 든다. 실제 커머스도 대부분 그렇게 한다.
--    부수효과로 `ShippingPolicy.feeFor(cart.totalPrice())` 를 **손대지 않아도 된다** —
--    할인 후 기준이었다면 배송비 계산을 쿠폰 이후로 옮겨야 했다.

-- 쿠폰 정의(마스터). 발급받은 것은 member_coupon 이다.
CREATE TABLE coupon (
    id                  RAW(16)             NOT NULL,
    name                VARCHAR2(100 CHAR)  NOT NULL,
    -- FIXED: 정액 할인(원) / PERCENT: 정률 할인(%)
    discount_type       VARCHAR2(20 CHAR)   NOT NULL
                        CONSTRAINT ck_coupon_discount_type CHECK (discount_type IN ('FIXED','PERCENT')),
    discount_value      NUMBER(19,0)        NOT NULL,
    -- 최소 주문금액(상품합계 기준). 0이면 제한 없음
    min_order_amount    NUMBER(19,0)        DEFAULT 0 NOT NULL,
    -- 정률 할인의 상한(원). NULL이면 상한 없음. 정액에는 의미 없다
    max_discount_amount NUMBER(19,0),
    valid_from          TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    valid_until         TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    created_at          TIMESTAMP(9) WITH TIME ZONE,
    updated_at          TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- ⚠ CHECK 제약에 **이름을 붙였다**(ck_coupon_discount_type).
--    V1 이 orders.status 를 인라인 CHECK 로 선언했다가, V13 에서 DELIVERED 를 추가할 때
--    자동 생성 이름(SYS_Cnnnnnn)이 DB 마다 달라 PL/SQL 로 찾아 지워야 했다.
--    처음부터 이름을 붙이면 다음 값 추가는 DROP/ADD 두 줄이면 된다.

-- 회원이 발급받은 쿠폰. 사용 시각을 남긴다(결제·발송·취소와 같은 방식).
CREATE TABLE member_coupon (
    id         RAW(16)  NOT NULL,
    member_id  RAW(16)  NOT NULL,  -- FK 아님(느슨한 참조) — 도메인 경계
    coupon_id  RAW(16)  NOT NULL,
    used_at    TIMESTAMP(9) WITH TIME ZONE,  -- NULL이면 미사용
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_member_coupon_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (id)
);

-- "내 쿠폰 목록"이 주 조회 경로다.
CREATE INDEX idx_member_coupon_member ON member_coupon (member_id, used_at);

-- 주문에 **할인 스냅샷**.
--
-- coupon_id 를 참조로 두지 않고 이름·금액을 복사하는 이유는 오늘 반복해서 적용한 그 원칙이다 —
-- 쿠폰 정의가 나중에 바뀌거나 삭제돼도 주문 내역은 "그때 얼마 할인받았는지"를 그대로 보여줘야 한다
-- (구매자 닉네임 V5 · 상품 이미지 V9 · 배송지 V11 · 배송비 V14 · 정가 V16 과 같은 판단).
--
-- 기존 주문은 쿠폰을 쓴 적이 없으므로 할인액 0 이 사실이고, 쿠폰명은 NULL 이 사실이다.
ALTER TABLE orders ADD (
    coupon_name     VARCHAR2(100 CHAR),
    coupon_discount NUMBER(19,0) DEFAULT 0 NOT NULL
);

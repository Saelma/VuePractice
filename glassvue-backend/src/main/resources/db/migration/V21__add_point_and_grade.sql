-- 적립금 · 회원 등급 (2026-07-24, 백로그 C-10)
--
-- ============================================================================
-- 적립 시점: **배송완료(DELIVERED)** — 회수 로직을 원천적으로 없애는 선택
-- ============================================================================
-- 주문 취소는 ORDERED·PAID 에서만 된다(SHIPPED 이후는 불가). 그래서 DELIVERED 에서 적립하면
-- **적립을 되돌릴 일이 아예 생기지 않는다.** 결제(PAID) 시점에 주면 취소마다 회수해야 하고,
-- 그 사이에 포인트를 이미 써버렸으면 **잔액이 음수**가 되는 경우까지 다뤄야 한다.
-- 실제 커머스의 "구매확정 후 적립"과 같은 자리이기도 하다. (사용자 결정, 2026-07-24)
--
-- 오늘 배운 것을 그대로 적용한 셈이다 — **규율이 아니라 구조로 막는다**(V18 §3-3).

-- ---------------------------------------------------------------- 적립금 계정
--
-- ⚠ 잔액·등급을 `member` 테이블에 두지 않는다. 그러면 point 도메인이 member 테이블을 만져야 해서
--    도메인 경계가 깨진다. **테이블은 소유 도메인이 갖는다** — coupon 이 member_coupon 을 갖는 것과 같다.
--    member_id 는 FK 아님(느슨한 참조) — 도메인 간이라 review·member_coupon·wishlist 와 같은 방식.
CREATE TABLE point_account (
    id             RAW(16)            NOT NULL,
    member_id      RAW(16)            NOT NULL,
    -- 현재 잔액. 이력(point_history)의 합과 항상 같아야 한다 — 이력이 원장, 이건 캐시다.
    balance        NUMBER(19,0)       DEFAULT 0 NOT NULL
                   CONSTRAINT ck_point_account_balance CHECK (balance >= 0),
    -- 등급 산정 기준: **누적 구매확정액**(배송완료된 주문의 상품매출 합). 비정규화해 둔다 —
    -- 매번 orders 를 합산하면 등급 조회가 집계 쿼리가 되고, 이 값은 배송완료 때만 바뀐다.
    total_purchase NUMBER(19,0)       DEFAULT 0 NOT NULL,
    grade          VARCHAR2(20 CHAR)  DEFAULT 'BRONZE' NOT NULL
                   CONSTRAINT ck_point_account_grade CHECK (grade IN ('BRONZE','SILVER','GOLD','VIP')),
    created_at     TIMESTAMP(9) WITH TIME ZONE,
    updated_at     TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    -- 회원당 계정 하나. 동시 요청으로 두 번 만들어지면 잔액이 갈라진다 — 최종 방어선.
    CONSTRAINT uk_point_account_member UNIQUE (member_id)
);

-- ---------------------------------------------------------------- 적립금 이력
--
-- **잔액만 두면 "왜 이 숫자지"를 따질 수 없다.** 돈에 준하는 값이라 이력이 원장이고 잔액은 그 합이다.
-- balance_after 를 함께 남기는 이유: 나중에 잔액이 어긋났을 때 **어느 시점부터 틀어졌는지**를
-- 이력만으로 짚을 수 있다(합계를 다시 계산해 비교).
CREATE TABLE point_history (
    id            RAW(16)            NOT NULL,
    member_id     RAW(16)            NOT NULL,
    -- EARN: 배송완료 적립 / USE: 주문에서 사용 / ADJUST: 관리자 수동 조정(지금은 화면 없음)
    type          VARCHAR2(20 CHAR)  NOT NULL
                  CONSTRAINT ck_point_history_type CHECK (type IN ('EARN','USE','ADJUST')),
    -- 부호 있는 값. 적립은 +, 사용은 −. 부호를 type 으로 유추하지 않고 값에 담는다 —
    -- 합계를 그냥 SUM 으로 낼 수 있고, ADJUST 처럼 양방향인 종류가 생겨도 규칙이 안 바뀐다.
    amount        NUMBER(19,0)       NOT NULL,
    balance_after NUMBER(19,0)       NOT NULL,
    -- 어느 주문 때문인지. 관리자 조정은 NULL. FK 아님(느슨한 참조 — order 는 다른 도메인)
    order_id      RAW(16),
    reason        VARCHAR2(100 CHAR) NOT NULL,
    created_at    TIMESTAMP(9) WITH TIME ZONE,
    updated_at    TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- "내 적립금 이력"이 유일한 조회 경로다(최신순).
CREATE INDEX idx_point_history_member ON point_history (member_id, created_at);

-- ---------------------------------------------------------------- 기존 회원 백필
--
-- 계정이 없는 회원은 조회할 때마다 "없으면 만든다"로 처리해도 되지만, **그러면 조회가 쓰기를 한다.**
-- 읽기 트랜잭션에서 계정이 생기는 건 예측하기 어렵고, 동시 조회에서 유니크 충돌도 난다.
-- 가입 시점에 만드는 게 정석이고, 기존 회원은 여기서 채운다.
--
-- id 는 UUIDv7 레이아웃을 그대로 조립한다(V18 과 같은 방식) — SYS_GUID() 는 v7 이 아니라서
-- 한 번 들어가면 앱이 만든 id 와 구분되지 않는다. CLAUDE.md 의 PK 규칙을 백필에서도 지킨다.
INSERT INTO point_account (id, member_id, balance, total_purchase, grade, created_at, updated_at)
SELECT
    HEXTORAW(
        LPAD(TO_CHAR(ROUND((CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS DATE) - DATE '1970-01-01')
                           * 86400000), 'FMXXXXXXXXXXXX'), 12, '0')
        || '7' || SUBSTR(RAWTOHEX(SYS_GUID()), 1, 3)
        || 'A' || SUBSTR(RAWTOHEX(SYS_GUID()), 4, 15)
    ),
    m.id,
    0,          -- 소급 적립하지 않는다. 과거 주문에 적립률을 지금 정해 소급하면 "언제 정한 규칙인가"가 흐려진다
    0,          -- 누적 구매액도 0에서 시작. 등급은 앞으로의 구매로 올라간다
    'BRONZE',
    SYSTIMESTAMP,
    SYSTIMESTAMP
FROM member m;

-- ---------------------------------------------------------------- 주문 스냅샷
--
-- 쿠폰(V17)·배송비(V14)·정가(V16)와 **같은 원칙**이다 — 정책이 바뀌어도 과거 주문의 숫자는 그대로여야 한다.
-- 적립률이 나중에 바뀌어도 "그때 얼마 받았는지"는 이 값이 사실이다.
--
-- 기존 주문은 포인트를 쓴 적도 받은 적도 없으므로 0 이 사실이다 → DEFAULT 0 NOT NULL 한 문장으로 끝난다
-- (V15 주문번호처럼 행마다 값이 달라야 하는 경우가 아니다 — 3단계 백필이 필요 없다).
ALTER TABLE orders ADD (
    used_point   NUMBER(19,0) DEFAULT 0 NOT NULL,
    earned_point NUMBER(19,0) DEFAULT 0 NOT NULL
);

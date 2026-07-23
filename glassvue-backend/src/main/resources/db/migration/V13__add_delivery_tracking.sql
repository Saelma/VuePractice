-- 배송 추적(운송장) + 배송완료 상태 (2026-07-23)
--
-- 발송 처리는 있었지만 고객이 배송을 추적할 방법이 전혀 없었다 — 관리자가 SHIPPED로 바꿔도
-- 택배사·송장번호가 없어 "보냈다"는 사실만 남고, 주문 진행 스텝(주문접수→결제→발송)도 발송에서 끊겼다.
-- V11로 "어디로 보낼지"가 생겼으니 그 다음 조각이다.
--
-- 전부 nullable: 이전 주문은 운송장을 알 방법이 없어 백필이 불가능하다(그때 안 받았다).
-- 화면은 값이 없으면 배송 추적 영역을 감춘다 — 배송지(V11)·취소시각(V10)과 같은 방식.
--
-- 길이는 CHAR semantics로. 이 DB의 NLS_LENGTH_SEMANTICS는 BYTE라서 그냥 VARCHAR2(30)이라고 쓰면
-- 30바이트(한글 10자)가 된다 — 택배사명이 한글이므로 BYTE로 두면 위험하다(WORKING-AGREEMENTS §2-2-1).
ALTER TABLE orders ADD (
    ship_carrier     VARCHAR2(30 CHAR),
    ship_tracking_no VARCHAR2(50 CHAR),
    delivered_at     TIMESTAMP(9) WITH TIME ZONE
);

-- ─────────────────────────────────────────────────────────────────────────────
-- status에 DELIVERED를 허용하도록 CHECK 제약 교체
--
-- ⚠ V1__init.sql이 status를 인라인 CHECK로 선언했다:
--     status VARCHAR2(20 CHAR) NOT NULL CHECK (status IN ('ORDERED','PAID','SHIPPED','CANCELLED'))
--   인라인 선언이라 제약 **이름이 자동 생성**(SYS_Cnnnnnn)되고, 그 이름은 **DB마다 다르다**
--   (운영 espdb와 검증용 esptest가 서로 다른 이름을 갖는다). 그래서 이름을 하드코딩하면
--   한쪽에서만 동작하고 빈 DB 검증(§2-2)에서 깨진다. → 조건으로 찾아서 지운다.
--
--   이번에는 **이름을 붙여서** 다시 만든다(ck_orders_status). 다음에 상태를 추가할 땐
--   이 동적 탐색이 필요 없이 DROP CONSTRAINT ck_orders_status 한 줄이면 된다.
--
-- 구 jar 영향: **넓히는 방향이라 안전하다.** 구 jar는 DELIVERED를 쓰지 않으므로 동작이 그대로다.
--   (WORKING-AGREEMENTS §5는 "CHECK 변경은 구 jar 동작을 바꾼다"고 경계하는데, 그건 좁히는 경우다.
--    허용값을 늘리는 변경은 기존 값·기존 코드 어느 쪽에도 영향이 없다.)
DECLARE
    v_found NUMBER := 0;
BEGIN
    -- 조건: orders 테이블의 CHECK 제약 중 status 값 목록을 담고 있는 것
    FOR c IN (SELECT constraint_name
                FROM user_constraints
               WHERE table_name = 'ORDERS'
                 AND constraint_type = 'C'
                 AND search_condition_vc LIKE '%ORDERED%'
                 AND search_condition_vc LIKE '%CANCELLED%') LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE orders DROP CONSTRAINT ' || c.constraint_name;
        v_found := v_found + 1;
    END LOOP;

    -- 못 찾아도 실패시키지 않는다: 이미 이 마이그레이션을 손으로 적용했거나
    -- 제약 없이 만들어진 DB일 수 있다. 아래 ADD CONSTRAINT가 최종 상태를 보장한다.
    DBMS_OUTPUT.PUT_LINE('dropped status check constraints: ' || v_found);
END;
/

ALTER TABLE orders ADD CONSTRAINT ck_orders_status
    CHECK (status IN ('ORDERED', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'));

-- ⚠ ship_carrier에는 일부러 CHECK를 걸지 않는다.
--   택배사는 앞으로 늘어날 값인데, CHECK를 걸면 택배사 하나 추가할 때마다 위와 똑같은
--   "제약 찾아서 드롭하고 다시 만들기" 마이그레이션을 써야 한다. 값 검증은 애플리케이션의
--   DeliveryCarrier enum이 한다(요청 역직렬화 단계에서 걸러진다).
--   status는 상태 전이 규칙이 DB 레벨 보호를 받을 값이라 CHECK를 유지하지만, 택배사는 그런 값이 아니다.

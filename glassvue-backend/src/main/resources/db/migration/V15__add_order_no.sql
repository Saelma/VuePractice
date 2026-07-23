-- 사람이 읽는 주문번호 (2026-07-23)
--
-- 주문 식별자가 UUID 뿐이라 화면은 `#019f8d59` 처럼 앞 8자만 잘라 보여주고 있었다.
-- 고객이 CS 에 불러주기 어렵고, 잘린 값이라 중복 가능성도 있다.
--
-- ⚠ PK 를 바꾸는 게 아니다. PK 는 UUIDv7(RAW(16)) 그대로 두고 **표시·검색용 별도 컬럼**을 더한다.
--   CLAUDE.md 의 "Long/SEQUENCE/IDENTITY 금지" 는 **PK 에 대한 규칙**이라 여기엔 해당하지 않는다.
--
-- 형식: yyyyMMdd-NNNN (예: 20260723-0026). 날짜는 주문 시각, 뒤는 전역 일련번호.
--   날짜별로 1부터 리셋하지 않는 이유: 리셋하려면 동시 주문에서 같은 번호를 잡는 걸 막아야 해서
--   카운터 테이블 락이나 유니크 충돌 재시도가 필요하다. 전역 시퀀스는 **충돌이 원천적으로 불가능**하고
--   재시도 로직도 필요 없다. 번호가 이어지는 게 이상해 보일 수 있지만 CS 식별에는 지장이 없다.

-- NOCACHE: 캐시를 쓰면 인스턴스 재시작마다 최대 CACHE 크기만큼 번호가 건너뛴다.
-- 주문번호가 띄엄띄엄하면 "주문이 누락됐나?" 하는 오해를 산다. 이 규모에선 성능 차이가 무의미하다.
CREATE SEQUENCE seq_order_no START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- 1단계: nullable 로 추가.
-- 배송비(V14)는 기존 행에 0 이 사실이라 DEFAULT 로 한 번에 끝났지만, 주문번호는 **행마다 값이 달라야** 해서
-- DEFAULT 로 채울 수 없다. WORKING-AGREEMENTS §2-1 의 3단계(nullable → 백필 → NOT NULL)를 그대로 탄다.
ALTER TABLE orders ADD order_no VARCHAR2(20 CHAR);

-- 2단계: 기존 주문 백필.
-- 시퀀스를 **주문 시각 순서대로** 소진해 과거 주문도 시간순으로 번호가 매겨지게 한다.
-- (ROW_NUMBER 로 계산해 넣으면 시퀀스 현재값과 어긋나 다음 주문이 유니크 충돌을 낸다.)
-- 빈 DB(esptest)에서는 대상이 0건이라 아무 일도 일어나지 않고 시퀀스는 1 에서 시작한다.
--
-- ⚠ 날짜는 **Asia/Seoul 기준**으로 뽑는다. created_at 은 UTC(+00:00)로 저장돼 있어서
--    그냥 TO_CHAR 하면 한국 시간 00:00~09:00 주문이 **전날 날짜**로 찍힌다.
--    앱이 새 주문번호를 만들 때도 같은 기준(Asia/Seoul)을 쓴다 — 안 맞추면 과거/신규 번호의
--    날짜 규칙이 달라진다.
DECLARE
    v_no VARCHAR2(20);
BEGIN
    FOR o IN (SELECT id, created_at FROM orders WHERE order_no IS NULL ORDER BY created_at) LOOP
        SELECT TO_CHAR(o.created_at AT TIME ZONE 'Asia/Seoul', 'YYYYMMDD')
               || '-' || LPAD(seq_order_no.NEXTVAL, 4, '0')
          INTO v_no FROM dual;
        UPDATE orders SET order_no = v_no WHERE id = o.id;
    END LOOP;
END;
/

-- 3단계: NOT NULL + 유니크 확정.
-- 유니크는 앱 채번이 어긋났을 때의 **최종 방어선**이다(닉네임 유니크 V6 와 같은 성격).
ALTER TABLE orders MODIFY order_no NOT NULL;
ALTER TABLE orders ADD CONSTRAINT uk_orders_order_no UNIQUE (order_no);

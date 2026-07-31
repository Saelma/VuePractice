-- 가입 쿠폰 지정을 **설정에서 데이터로** 옮긴다 (2026-07-31, G-2 후속)
--
-- G-2 는 가입 쿠폰을 `coupon.welcome-coupon-id`(=.env WELCOME_COUPON_ID) 설정으로 가리켰다.
-- 쓰다 보니 셋이 불편했다(사용자 지적):
--   ① 쿠폰을 바꾸려면 **서비스를 재시작**해야 한다.
--   ② **무엇이 가입 쿠폰인지 화면에서 안 보인다** — .env 를 열어 봐야 안다.
--   ③ 쿠폰을 지워도 설정에 **죽은 id 가 남는다**(안내 API 가 조용히 꺼져 원인을 모른다).
-- → 쿠폰 자체가 DB 에 있으므로 "어느 것이 가입 쿠폰인가" 도 데이터로 두는 게 맞다.
--
-- 상태가 이진(지정/해제)이라 enum(VARCHAR2 + CHECK) 대신 **boolean(NUMBER(1))** 로 둔다 —
-- member.suspended(V30)와 같은 판단이다. enum CHECK 컬럼은 값을 늘릴 때 제약 교체가 따라온다.
--
-- ⚠ DEFAULT 0 NOT NULL 이라 **기존 쿠폰은 전부 비지정(0)** 으로 백필된다. 순수 추가라 구 jar 무해 —
--    구 jar 는 이 컬럼을 매핑 안 해도 validate 통과, INSERT 도 DEFAULT 로 채워진다.

ALTER TABLE coupon ADD welcome NUMBER(1) DEFAULT 0 NOT NULL;

-- ⚠ **가입 쿠폰은 하나뿐**임을 DB 가 보장한다.
-- 앱에서 "기존 지정 해제 후 새로 지정" 을 한 트랜잭션에 넣어도, 두 관리자가 동시에 지정하면
-- 둘 다 1 이 될 수 있다(각자 상대의 미커밋 변경을 못 본다). 그러면 가입 쿠폰이 둘이 되어
-- 어느 쪽이 나갈지 조회 순서에 달리게 된다.
--
-- 함수기반 유니크 인덱스: welcome=1 인 행만 색인되고(0 은 NULL 이라 색인 제외) 그중 유일해야 한다.
-- 부분 인덱스가 없는 Oracle 에서 "여러 행 중 하나만 참" 을 표현하는 표준 방법이다.
CREATE UNIQUE INDEX ux_coupon_welcome ON coupon (CASE WHEN welcome = 1 THEN 1 END);

-- 회원 정지 컬럼 (2026-07-28, B-11 후속 — 회원 정지/역할변경)
--
-- 관리자가 회원을 정지하면 로그인·토큰갱신·주문이 막힌다(AuthService·OrderService 가드).
-- 상태가 지금은 활성/정지 둘뿐이라 enum(VARCHAR2 + CHECK) 대신 **boolean(NUMBER(1))** 로 둔다 —
-- enum CHECK 컬럼은 값을 늘릴 때 제약 교체 마이그레이션이 따라오는데(orders.status 사고), 정지는
-- 이진 상태라 그 비용을 질 이유가 없다. 나중에 BANNED/DORMANT 같은 상태가 필요해지면 그때 승격한다.
--
-- ⚠ DEFAULT 0 NOT NULL 이라 **기존 회원은 전부 활성(0)** 으로 백필된다(§2-1). 순수 추가라 구 jar 무해 —
--    구 jar 는 이 컬럼을 매핑 안 해도 validate 통과, 신규 가입 INSERT 도 DEFAULT 로 채워져 정상.
--    (닉네임 UNIQUE(V6)처럼 구 jar 동작을 바꾸는 제약이 아니다 — DEFAULT 가 있어 INSERT 가 안 깨진다.)

ALTER TABLE member ADD suspended NUMBER(1) DEFAULT 0 NOT NULL;

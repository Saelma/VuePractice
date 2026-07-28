-- 회원 이메일 컬럼 선행 추가 (2026-07-28, 백로그 D — 비밀번호 재설정 링크 SMTP 발송 대비)
--
-- B-10(비밀번호 재설정)은 토큰 발급·검증까지 끝났지만 링크 "전달"(메일/SMS)이 없어 반쪽이다.
-- 실발송(SMTP)을 붙이려면 보낼 주소가 있어야 하는데 member 에 email 이 없었다. B-11 전에
-- **컬럼만 미리** 심어 둔다(수집 폼·발송 로직은 SMTP 작업에서). 지금은 채우는 경로가 없어 전부 NULL.
--
-- ⚠ 순수 추가 + nullable 이라 구 jar 에 영향 없다. 기존 행은 email 을 안 읽으니 재기동 무해
--    (ddl-auto=validate 는 nullable 컬럼 추가를 문제 삼지 않는다. member.ship_* 여분 컬럼과 반대 방향).
-- ⚠ 계정 이메일은 유일해야 한다(loginId·nickname 과 같은 판단) — UNIQUE. Oracle 은 NULL 을 유니크
--    검사에서 제외하므로 전부 NULL 인 현재도 충돌하지 않는다. 실제 수집 시작 시 검증만 얹으면 된다.
-- 길이 255: 이메일 최대 254자(RFC 5321) 수용.

ALTER TABLE member ADD email VARCHAR2(255);
ALTER TABLE member ADD CONSTRAINT uk_member_email UNIQUE (email);

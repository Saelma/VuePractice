-- 가입 약관 동의 (2026-08-03, 백로그 B-21)
--
-- 개인정보(이메일·닉네임)를 수집·저장하면서 **동의를 받는 절차가 아예 없었다** — SignupRequest 는
-- loginId·password·nickname·email 넷뿐이고 가입 화면에 체크박스가 0개였다(실측).
--
-- ⚠ **둘 다 nullable 이고 백필하지 않는다.** V34(email_verified)는 DEFAULT 0 으로 백필했는데
--    거기선 그게 **사실과 맞았다**("지금까지 아무도 인증한 적이 없다" = 0). 여기는 반대다 —
--    기존 회원에게 sysdate 를 넣으면 **동의한 적이 없는 사람에게 동의 시각이 생긴다.** 그건 거짓이고,
--    하필 이 컬럼은 "동의를 받았다"는 **근거**로 쓸 값이라 거짓이 가장 비싼 자리다.
--    → NULL = "동의 기록 없음". 그게 지금의 사실이다.
--
-- ⚠ 그래서 **소급 적용도 없다** — 기존 회원은 재동의 없이 계속 로그인된다(E-3 비밀번호 정책과 같은 판단,
--    2026-07-30). 정책은 **새로 들어오는 사람부터** 적용하고, 기존 회원 재동의는 별건이다.
--
-- **왜 컬럼 둘인가**: 필수 동의(이용약관+개인정보)와 선택 동의(마케팅)는 **성격이 다르다.**
--    필수는 없으면 가입이 안 되고, 선택은 없어도 가입이 된다 — 한 컬럼에 담으면 그 구분이 사라진다.
--    ⚠ 이용약관과 개인정보 처리방침을 **한 컬럼(terms_agreed_at)으로 합친 것**은 의도적이다.
--    둘 다 필수라 항상 같이 참/거짓이고, 나누면 "약관만 동의한 회원" 이라는 **일어날 수 없는 상태**가
--    표현 가능해진다. 약관 버전 관리가 필요해지면 그때 member_agreement 테이블로 간다(BACKLOG B-21).
--
-- ⚠ marketing_agreed_at 은 **지금 보내는 코드가 없다** — 그래도 받는 이유는, 동의는 나중에
--    소급해서 받을 수 없기 때문이다. 알림 설정(NotificationType)에 MARKETING 을 더하지 **않은** 이유도
--    같다: 그건 아무 일도 안 하는 토글이 되지만(no-op 배선), 이 컬럼은 **근거**라 성격이 다르다.
--
-- 타입은 BaseTimeEntity 의 created_at/updated_at 과 같은 TIMESTAMP(9) WITH TIME ZONE 이다.
-- plain TIMESTAMP 로 만들면 validate 는 통과하고 **읽을 때 ORA-18716** 이 난다(2026-07-20, V26 사고).
--
-- 순수 추가 + nullable 이라 구 jar 무해(INSERT 가 안 깨진다).

ALTER TABLE member ADD (
    terms_agreed_at     TIMESTAMP(9) WITH TIME ZONE,
    marketing_agreed_at TIMESTAMP(9) WITH TIME ZONE
);

COMMENT ON COLUMN member.terms_agreed_at IS
    '이용약관+개인정보 처리방침 동의 시각(필수). NULL=동의 기록 없음(V37 이전 가입자)';
COMMENT ON COLUMN member.marketing_agreed_at IS
    '마케팅 수신 동의 시각(선택). NULL=미동의. 발송 채널은 아직 없다(동의만 먼저 받아 둔다)';

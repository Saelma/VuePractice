-- 닉네임 유니크 강제. 닉네임은 review.author·orders.buyer_nickname에 스냅샷으로 저장되는데,
-- 중복을 허용하면 동명이인이 생겨 관리자 화면에서 누구 주문/리뷰인지 구분할 수 없다.
-- 그래서 표시명이지만 유니크로 둔다(가입·변경 시 애플리케이션에서도 existsByNickname으로 검사).
--
-- 사전 확인: 제약 추가 전 기존 중복이 0건이어야 한다(있으면 이 ALTER가 ORA-02299로 실패).
--   SELECT nickname, COUNT(*) FROM member GROUP BY nickname HAVING COUNT(*) > 1;  → 0건 확인함(2026-07-21).
--
-- UNIQUE 제약은 뒤에 오는 존재 검사(existsByNickname)의 경합(TOCTOU) 상황에서도
-- 최종 방어선이 된다(두 가입이 동시에 같은 닉네임을 통과해도 DB가 하나를 막는다).
ALTER TABLE member ADD CONSTRAINT uk_member_nickname UNIQUE (nickname);

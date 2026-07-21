-- BYTE semantics로 만들어진 스냅샷 컬럼 보정 (2026-07-21 발견).
--
-- 이 DB의 NLS_LENGTH_SEMANTICS는 BYTE다. V1__init.sql은 전부 `VARCHAR2(n CHAR)`로 썼는데
-- V5(buyer_nickname)·V9(product_image_url)이 `CHAR`를 빠뜨려 바이트 길이로 만들어졌다.
--   실측: member.nickname → 50 CHAR(200바이트) / orders.buyer_nickname → 50 BYTE
--
-- 그래서 닉네임이 한글 17자 이상인 회원은 **주문 자체가 실패**한다 — 저장할 땐 통과한 닉네임을
-- 주문에 스냅샷하는 순간 ORA-12899가 난다. 아직 그런 회원이 없어 안 터졌을 뿐이다.
--
-- 넓히는 방향(50바이트 → 50자 = 200바이트)이라 기존 데이터는 그대로 두고 온라인으로 적용된다.
-- 엔티티 @Column(length=...) 값은 그대로다(Hibernate는 char_length로 검증 — member.nickname이 이미 그렇다).
ALTER TABLE orders     MODIFY buyer_nickname    VARCHAR2(50 CHAR);
ALTER TABLE order_item MODIFY product_image_url VARCHAR2(500 CHAR);

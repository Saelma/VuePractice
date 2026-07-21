-- 배송지. 지금까지 주문에 "어디로 보낼지"가 없어서 관리자가 발송 처리를 눌러도 목적지가 없었다.
--
-- orders에는 **스냅샷**으로 둔다(회원 주소를 참조하지 않는다) — 구매자 닉네임(V5)·상품 이미지(V9)와 같은 이유로,
-- 회원이 나중에 주소를 바꿔도 과거 주문은 "그때 보낸 곳"이어야 CS·배송 이력이 맞다.
-- member의 값은 주문서에 자동으로 채워 넣기 위한 편의(현재 값)일 뿐이다.
--
-- 둘 다 nullable: 기존 주문은 배송지를 알 방법이 없어 백필이 불가능하고(그때 주소를 모른다),
-- 회원도 기본 배송지를 안 넣을 수 있다. 신규 주문은 요청 검증(@NotBlank)이 값을 보장한다.
--
-- 길이는 반드시 CHAR semantics로 — 이 DB의 NLS_LENGTH_SEMANTICS는 BYTE라서 그냥 VARCHAR2(200)이라고 쓰면
-- 200바이트(한글 약 66자)가 된다. 주소는 한글이 기본이라 BYTE로 두면 긴 주소에서 ORA-12899가 난다.
-- (V1__init.sql은 CHAR로 썼는데 V5·V9가 빠뜨렸다 — V12에서 보정.)
ALTER TABLE orders ADD (
    ship_recipient VARCHAR2(50 CHAR),
    ship_phone     VARCHAR2(20 CHAR),
    ship_zipcode   VARCHAR2(10 CHAR),
    ship_address1  VARCHAR2(200 CHAR),
    ship_address2  VARCHAR2(200 CHAR)
);

ALTER TABLE member ADD (
    ship_recipient VARCHAR2(50 CHAR),
    ship_phone     VARCHAR2(20 CHAR),
    ship_zipcode   VARCHAR2(10 CHAR),
    ship_address1  VARCHAR2(200 CHAR),
    ship_address2  VARCHAR2(200 CHAR)
);

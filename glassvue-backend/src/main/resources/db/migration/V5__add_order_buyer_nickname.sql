-- 관리자 주문 목록에 "누가 주문했는지"를 띄우기 위한 구매자 닉네임 스냅샷.
-- 조회 시점에 member에서 가져오지 않고 저장해두는 이유: MemberService.withdraw가 하드 삭제라
-- 탈퇴하면 회원 row가 사라진다. 그때 조회 방식이면 과거 주문의 구매자를 영영 알 수 없게 되는데,
-- 주문은 CS·배송 이력이라 시점 기록이 남아야 한다. review.author와 같은 방식.

-- 1) 우선 nullable로 추가 — 기존 행이 있으므로 NOT NULL로 바로 만들 수 없다.
ALTER TABLE orders ADD buyer_nickname VARCHAR2(50);

-- 2) 백필. 아직 남아 있는 회원은 현재 닉네임으로, 이미 탈퇴한 회원은 표식을 남긴다.
--    (member_id는 FK가 아닌 느슨한 참조라 탈퇴해도 주문은 그대로 남아 있다)
UPDATE orders o SET buyer_nickname = NVL(
    (SELECT m.nickname FROM member m WHERE m.id = o.member_id),
    '(탈퇴한 회원)');

-- 3) 이제 전 행이 채워졌으니 NOT NULL 확정. 엔티티가 nullable=false라 이게 없으면 validate 실패.
ALTER TABLE orders MODIFY buyer_nickname VARCHAR2(50) NOT NULL;

-- 관리자 목록의 기본 정렬·상태 필터용 인덱스.
CREATE INDEX idx_orders_status_created ON orders (status, created_at DESC);

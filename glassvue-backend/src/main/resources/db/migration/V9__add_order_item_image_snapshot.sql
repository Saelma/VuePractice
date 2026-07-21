-- 주문 품목에 상품 이미지(썸네일) 스냅샷. 이름·가격과 같은 이유로 참조가 아니라 스냅샷이다
-- — 상품이 바뀌거나 삭제돼도 주문 이력은 "그때 모습"이어야 한다(orders.buyer_nickname과 동일한 판단).
-- nullable: 기존 주문엔 스냅샷이 없고 백필할 원본도 없다(그 시점 이미지를 알 수 없음) → 화면이 대체 표시로 처리.
ALTER TABLE order_item ADD product_image_url VARCHAR2(500);

-- baseline(V1) 이후 첫 마이그레이션 — Flyway 동작 실증 + 실이득.
-- 자주 where/join에 쓰이는 느슨한 FK·필터 컬럼에 인덱스(현재 인덱스 없어 풀스캔).
CREATE INDEX idx_review_product ON review (product_id);        -- ReviewRepository.findByProduct / stats
CREATE INDEX idx_inquiry_product ON inquiry (product_id);      -- InquiryRepository.findByProduct
CREATE INDEX idx_orders_member ON orders (member_id);          -- OrderRepository.findByMemberId...
CREATE INDEX idx_order_item_product ON order_item (product_id); -- 구매 인증(existsPurchase)
CREATE INDEX idx_product_category ON product (category_id);    -- 카테고리 연관 필터

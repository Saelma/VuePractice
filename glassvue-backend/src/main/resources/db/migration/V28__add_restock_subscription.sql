-- 재입고 알림 신청 (2026-07-27, 백로그 B-9)
--
-- 품절 상품에 "입고되면 알려줘"를 저장한다. 인앱 알림 시스템(V26, §11)이 이미 있어 전달·설정·SSE는
-- 그대로 쓰고, 여기선 **신청(구독) 저장소**만 새로 만든다. 위시리스트(V19)와 사실상 같은 모양이다 —
-- "회원이 상품 하나를 지목해 둔다".
--
-- ⚠ 단위가 **옵션이 아니라 상품**이다. 관리자 상품 편집이 옵션(product_variant)을 delete + 재삽입하며
--    variant.id 가 매번 새로 생겨(ProductCommandService.update), 옵션 id 로 걸면 편집 한 번에 구독이
--    전부 고아가 된다. 그래서 위시리스트처럼 (member, product) 한 쌍으로 잡고, 재입고 판정도
--    상품 총재고 0→양수로 한다(StockReplenishedEvent).
--
-- ⚠ 이 마이그레이션은 **순수 추가**다. 새 테이블 하나뿐이라 구 jar 에 아무 영향이 없다.
--    NotificationType 에 RESTOCK 값을 늘렸지만 notification.type 컬럼엔 CHECK 제약이 없어(V26 확인)
--    enum 확장에 별도 ALTER 가 필요 없다.

CREATE TABLE restock_subscription (
    id         RAW(16)                     NOT NULL,
    -- 둘 다 **FK 아님(느슨한 참조)** — 도메인 경계. restock 은 자기 도메인이라 member·catalog 를
    -- 밖에서 가리킨다(wishlist 와 같다). 상품이 삭제돼도 이 행은 남을 수 있으나, 재입고 이벤트는
    -- 살아 있는 상품에서만 나므로 죽은 구독은 발화되지 않는다.
    member_id  RAW(16)                     NOT NULL,
    product_id RAW(16)                     NOT NULL,
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    -- 같은 상품을 두 번 신청할 수 없다(멱등의 최종 방어선 — wishlist 와 같은 이유).
    CONSTRAINT uk_restock_sub_member_product UNIQUE (member_id, product_id)
);

-- ⚠ 위시리스트와 달리 **product_id 인덱스를 따로 만든다.**
-- 조회 경로가 둘이다: ① "내 신청 목록"(member_id = ?) — 위 UNIQUE 선두 컬럼이 덮는다.
-- ② "이 상품 신청자 전원"(product_id = ?) — 재입고가 풀릴 때마다 도는 **핫 경로**인데,
--    UNIQUE 의 선두는 member_id 라 이 쿼리는 못 탄다. 그래서 product_id 전용 인덱스를 둔다.
CREATE INDEX ix_restock_sub_product ON restock_subscription (product_id);

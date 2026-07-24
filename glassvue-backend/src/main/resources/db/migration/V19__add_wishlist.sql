-- 위시리스트(찜) (2026-07-24, 백로그 B-6)
--
-- 장바구니와 구조가 거의 같다 — "회원이 상품을 골라 둔 목록". 그래서 조회 방식(상품 정보를
-- catalog 공개 서비스에서 합성)은 그대로 재활용한다.
--
-- ⚠ **다만 저장소가 다르다.** 장바구니는 Redis 인데 찜은 **테이블**이다.
--    장바구니는 "지금 사려는 것" 이라 세션 수명이면 충분하지만, 찜은 **로그아웃해도 남아야** 한다.
--    Redis 에 두면 TTL·플러시·재시작에 조용히 날아가고, 사용자는 "찜한 게 사라졌다" 로만 겪는다.
--
-- ⚠ 이 마이그레이션은 **순수 추가**다. V18 처럼 기존 값을 옮기거나 컬럼을 지우지 않는다
--    — 새 테이블 하나뿐이라 구 jar 에 아무 영향이 없다.

CREATE TABLE wishlist (
    id         RAW(16)                     NOT NULL,
    -- 둘 다 **FK 아님(느슨한 참조)** — 도메인 경계.
    -- member_address(V18)가 진짜 FK 를 건 것과 반대 경우다: 그건 member 도메인 **안**이었지만
    -- wishlist 는 자기 도메인이라 member·catalog 를 **밖에서** 가리킨다.
    -- review.product_id · member_coupon.member_id 와 같은 방식이다.
    member_id  RAW(16)                     NOT NULL,
    product_id RAW(16)                     NOT NULL,
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    -- **같은 상품을 두 번 찜할 수 없다.** 화면이 토글이라 중복이 생길 일이 없어 보이지만,
    -- 더블클릭·재시도·동시 요청이면 INSERT 가 두 번 나갈 수 있다. 그때 목록에 같은 상품이
    -- 두 줄 뜨고 "해제" 를 눌러도 하나가 남는다 — 조용히 어긋나는 종류의 버그다.
    -- 앱도 존재 확인을 하지만 이건 **최종 방어선**이다(주문번호 유니크 V15 와 같은 성격).
    CONSTRAINT uk_wishlist_member_product UNIQUE (member_id, product_id)
);

-- 조회용 인덱스를 **따로 만들지 않는다.**
-- 유일한 조회 경로가 "내 찜 목록"(member_id = ?)인데, 위 UNIQUE 제약이 만든 인덱스의
-- 선두 컬럼이 이미 member_id 라 그 쿼리가 그대로 탄다. 같은 컬럼으로 인덱스를 하나 더 만들면
-- INSERT/DELETE 비용만 늘고 얻는 게 없다.
--
-- (정렬은 created_at DESC 지만 인덱스로 덮지 않는다 — 회원 한 명의 찜은 정렬 비용이
--  문제가 될 규모가 아니다. 실제로 느려지면 그때 (member_id, created_at) 을 추가한다.)

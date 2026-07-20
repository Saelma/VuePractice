-- ============================================================================
-- V1 — baseline 스키마 (2026-07-16 Flyway 도입 시점의 상태)
--
-- ★ 이 파일은 기존 espdb에서는 실행되지 않는다.
--   기존 DB는 `baseline-on-migrate: true` + `baseline-version: 1`로 이미 V1이
--   "적용됨(BASELINE)"으로 기록돼 있고, Flyway는 baseline 버전 이하의 마이그레이션을
--   건너뛴다. 이 파일은 **빈 DB에서 처음부터 구축할 때만** 실행된다.
--   (개발 DB 분리 · 테스트 격리 · 서버 이전 시 필요 — 그전까지는 V2가 없는 테이블에
--    CREATE INDEX를 걸어 실패했다.)
--
-- ★ 왜 현재 엔티티 그대로가 아닌가
--   Hibernate 덤프를 그대로 쓰면 V3(review.image_group_id) · V4(product.avg_rating,
--   review_count) · V5(orders.buyer_nickname)가 추가하는 컬럼이 이미 들어가 있어,
--   빈 DB에서 V1 뒤에 V3~V5가 "이미 있는 컬럼을 ADD"하려다 실패한다.
--   그래서 **baseline 당시 컬럼만** 남기고 이후 추가분은 뺐다. 이후 변경은 V2~V5가 담당한다.
--
-- ★ 유지보수
--   엔티티가 바뀌면 이 파일이 아니라 **새 버전(V6…)** 을 추가한다. 적용된 마이그레이션은
--   수정 금지(체크섬). 이 파일은 baseline 시점 스냅샷으로 고정된 채 남는다.
-- ============================================================================

-- ---------------------------------------------------------------- 회원 · 공지
CREATE TABLE member (
    id         RAW(16)                     NOT NULL,
    login_id   VARCHAR2(50 CHAR)           NOT NULL UNIQUE,
    password   VARCHAR2(255 CHAR)          NOT NULL,
    nickname   VARCHAR2(50 CHAR)           NOT NULL,
    role       VARCHAR2(20 CHAR)           NOT NULL CHECK (role IN ('USER','ADMIN')),
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE TABLE notice (
    id         RAW(16)                     NOT NULL,
    title      VARCHAR2(200 CHAR)          NOT NULL,
    content    CLOB                        NOT NULL,
    author     VARCHAR2(50 CHAR)           NOT NULL,
    author_id  RAW(16),
    pinned     NUMBER(1,0)                 NOT NULL CHECK (pinned IN (0,1)),
    view_count NUMBER(19,0)                NOT NULL,
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------- 이미지
-- image_group은 id만 가진 앵커. 여러 도메인이 image_group_id만 두면 이미지를 재사용한다.
CREATE TABLE image_group (
    id         RAW(16)                     NOT NULL,
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE TABLE image (
    id             RAW(16)                 NOT NULL,
    image_group_id RAW(16),
    url            VARCHAR2(500 CHAR)      NOT NULL,
    original_name  VARCHAR2(255 CHAR),
    content_type   VARCHAR2(100 CHAR),
    file_size      NUMBER(19,0),
    sort_order     NUMBER(10,0),
    created_at     TIMESTAMP(9) WITH TIME ZONE,
    updated_at     TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_image_group FOREIGN KEY (image_group_id) REFERENCES image_group (id)
);

-- ---------------------------------------------------------------- 카탈로그
CREATE TABLE category (
    id         RAW(16)                     NOT NULL,
    name       VARCHAR2(50 CHAR)           NOT NULL UNIQUE,
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- avg_rating · review_count는 V4에서 추가된다(리뷰 집계 비정규화).
CREATE TABLE product (
    id             RAW(16)                 NOT NULL,
    name           VARCHAR2(200 CHAR)      NOT NULL,
    description    CLOB                    NOT NULL,
    price          NUMBER(19,0)            NOT NULL,
    stock          NUMBER(19,0)            NOT NULL,
    status         VARCHAR2(20 CHAR)       NOT NULL CHECK (status IN ('SELLING','SOLD_OUT','HIDDEN')),
    image_group_id RAW(16),                -- FK 아님(느슨한 참조) — 의도된 설계
    category_id    RAW(16)                 NOT NULL,
    created_at     TIMESTAMP(9) WITH TIME ZONE,
    updated_at     TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id)
);

-- ---------------------------------------------------------------- 주문
-- "order"는 Oracle 예약어라 테이블명은 orders.
-- buyer_nickname은 V5에서 추가된다(구매자 스냅샷).
-- status CHECK에 PAID/SHIPPED가 이미 있는 건 baseline 시점(7/16)에 상태 확장이
-- 끝나 있었기 때문 — 그때 ORA-02290을 겪고 수동 ALTER로 고친 결과가 반영된 상태다.
CREATE TABLE orders (
    id          RAW(16)                    NOT NULL,
    member_id   RAW(16)                    NOT NULL,  -- FK 아님(느슨한 참조)
    status      VARCHAR2(20 CHAR)          NOT NULL CHECK (status IN ('ORDERED','PAID','SHIPPED','CANCELLED')),
    total_price NUMBER(19,0)               NOT NULL,
    paid_at     TIMESTAMP(9) WITH TIME ZONE,
    shipped_at  TIMESTAMP(9) WITH TIME ZONE,
    created_at  TIMESTAMP(9) WITH TIME ZONE,
    updated_at  TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id)
);

-- 상품명·가격은 주문 시점 스냅샷 — 상품이 바뀌거나 삭제돼도 주문 내역은 그대로다.
CREATE TABLE order_item (
    id           RAW(16)                   NOT NULL,
    order_id     RAW(16)                   NOT NULL,
    product_id   RAW(16)                   NOT NULL,  -- FK 아님(느슨한 참조)
    product_name VARCHAR2(200 CHAR)        NOT NULL,
    price        NUMBER(19,0)              NOT NULL,
    quantity     NUMBER(19,0)              NOT NULL,
    line_total   NUMBER(19,0)              NOT NULL,
    created_at   TIMESTAMP(9) WITH TIME ZONE,
    updated_at   TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

-- ---------------------------------------------------------------- 리뷰 · 문의
-- image_group_id는 V3에서 추가된다(포토 리뷰).
CREATE TABLE review (
    id         RAW(16)                     NOT NULL,
    product_id RAW(16)                     NOT NULL,  -- FK 아님(느슨한 참조)
    author_id  RAW(16)                     NOT NULL,
    author     VARCHAR2(50 CHAR)           NOT NULL,  -- 작성 시점 닉네임 스냅샷
    rating     NUMBER(10,0)                NOT NULL,
    content    CLOB                        NOT NULL,
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE TABLE inquiry (
    id          RAW(16)                    NOT NULL,
    product_id  RAW(16)                    NOT NULL,  -- FK 아님(느슨한 참조)
    author_id   RAW(16)                    NOT NULL,
    author      VARCHAR2(50 CHAR)          NOT NULL,
    title       VARCHAR2(200 CHAR)         NOT NULL,
    content     CLOB                       NOT NULL,
    secret      NUMBER(1,0)                NOT NULL CHECK (secret IN (0,1)),
    status      VARCHAR2(20 CHAR)          NOT NULL CHECK (status IN ('WAITING','ANSWERED')),
    answer      CLOB,
    answered_at TIMESTAMP(9) WITH TIME ZONE,
    created_at  TIMESTAMP(9) WITH TIME ZONE,
    updated_at  TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id)
);

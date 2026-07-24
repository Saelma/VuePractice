-- 인앱 알림 (2026-07-24, 백로그 B-9 상위 — 알림 시스템). FCM 등 외부 푸시가 아니라 자체 인앱 알림이다:
-- 사이트를 보고 있는 동안 벨·토스트로 알린다(SSE 로 즉시 푸시). 서버에 알림함을 두는 이유는 stub 이던
-- 알림 핸들러(주문·재고 이벤트)를 실제 "전달"까지 잇기 위함이다.
CREATE TABLE notification (
    id          RAW(16)        NOT NULL,
    member_id   RAW(16)        NOT NULL,
    type        VARCHAR2(30)   NOT NULL,   -- NotificationType (ORDER·STOCK …). 설정 on/off 단위이기도 하다.
    title       VARCHAR2(200)  NOT NULL,
    message     VARCHAR2(1000) NOT NULL,
    link        VARCHAR2(500),             -- 클릭 시 이동 경로(예: /orders/{id}). 없으면 이동 안 함.
    is_read     NUMBER(1,0)    DEFAULT 0 NOT NULL
                CONSTRAINT ck_notification_read CHECK (is_read IN (0,1)),
    created_at  TIMESTAMP(6),
    updated_at  TIMESTAMP(6),
    CONSTRAINT pk_notification PRIMARY KEY (id)
);
-- 내 알림을 최신순으로 읽고 안읽음을 세는 게 유일한 접근 패턴이라 (member_id, created_at) 하나면 충분하다.
CREATE INDEX idx_notification_member ON notification (member_id, created_at);

-- 알림 켜기/끄기 설정 — 타입별 opt-out. 행이 없으면 켜짐(기본 on)으로 본다.
-- 그래서 "끈 것"만 행으로 남는다(모든 회원×타입을 미리 채우지 않는다).
CREATE TABLE member_notification_pref (
    id         RAW(16)      NOT NULL,
    member_id  RAW(16)      NOT NULL,
    type       VARCHAR2(30) NOT NULL,
    enabled    NUMBER(1,0)  DEFAULT 1 NOT NULL
               CONSTRAINT ck_member_notif_pref_enabled CHECK (enabled IN (0,1)),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    CONSTRAINT pk_member_notification_pref PRIMARY KEY (id),
    CONSTRAINT uq_member_notif_pref UNIQUE (member_id, type)
);

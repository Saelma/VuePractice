-- 관리자의 «남의 글 삭제» 와 «마케팅 발송» 을 원장에 남긴다 (2026-09-11, BACKLOG §O-6).
--
-- ---------------------------------------------------------------- 🔴 왜 필요한가
--
-- 쓰기 엔드포인트를 전부 꺼내 «감사를 남기나» 를 대조하니 **빠진 관리자 조작**이 셋 나왔다:
--   ① 관리자가 남의 리뷰 삭제 ② 관리자가 남의 문의 삭제 — 둘 다 **작성자와 같은 경로**라
--      `/api/admin/**` 을 보는 눈에 안 걸렸고, **되돌릴 수 없다**(repository.delete).
--   ③ 마케팅 발송 — **되돌릴 수 없는 방송**인데 «안 남기는 것» 목록에도 없었다(빠진 것이다).
--
-- ---------------------------------------------------------------- ① action 세 값 추가
--
-- ⚠ 이름 길이: REVIEW_DELETE 13 · INQUIRY_DELETE 14 · MARKETING_SEND 14 — 20자 안이다.
-- 🔴 CHECK 를 안 넓히면 **조작이 통째로 롤백된다**(감사는 발행측 트랜잭션에 합류, V60 과 같은 이유).

ALTER TABLE admin_audit_log DROP CONSTRAINT ck_admin_audit_action;

ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('MEMBER_SUSPEND', 'MEMBER_UNSUSPEND', 'MEMBER_ROLE_CHANGE', 'MEMBER_DELETE',
                      'ORDER_CANCEL', 'ORDER_ITEM_CANCEL',
                      'ORDER_SHIP', 'ORDER_DELIVER',
                      'ORDER_RETURN_REQUEST', 'ORDER_RETURN_APPROVE', 'ORDER_RETURN_REJECT',
                      'REVIEW_HIDE', 'REVIEW_UNHIDE', 'INQUIRY_HIDE', 'INQUIRY_UNHIDE',
                      'PRODUCT_DELETE', 'PRODUCT_RESTORE',
                      'PRODUCT_CREATE', 'PRODUCT_UPDATE',
                      'COUPON_CREATE', 'COUPON_ISSUE', 'COUPON_WELCOME_SET',
                      'DISCOUNT_CREATE', 'DISCOUNT_UPDATE', 'DISCOUNT_DELETE',
                      'CATEGORY_CREATE', 'CATEGORY_DELETE',
                      'NOTICE_CREATE', 'NOTICE_UPDATE', 'NOTICE_DELETE',
                      'INQUIRY_ANSWER',
                      'REVIEW_DELETE', 'INQUIRY_DELETE', 'MARKETING_SEND'));

-- ---------------------------------------------------------------- ② target_type 한 값 추가

ALTER TABLE admin_audit_log DROP CONSTRAINT ck_admin_audit_target_type;

ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_target_type
    CHECK (target_type IN ('MEMBER', 'PRODUCT', 'COUPON', 'CATEGORY', 'NOTICE', 'MARKETING'));

-- ---------------------------------------------------------------- ③ target_id 는 MARKETING 일 때만 빈다
--
-- 방송이라 가리킬 한 명이 없다. 행위자 id·새 UUID 로 채우면 **지어낸 값**이다(V37 이후의 판단).
-- 🔴 **NOT NULL 을 푸는 것만으로 끝내지 않는다** — 그러면 다른 종류도 조용히 비워질 수 있다.
--    «비는 것은 MARKETING 뿐» 을 DB 가 쥔다(규율이 아니라 구조로, WA §2-1-1).
-- ⚠ **구 jar 영향 없음** — 구 jar 는 늘 target_id 를 채운다. 기존 행은 전부 NOT NULL 이라 새 CHECK 를 지난다.

ALTER TABLE admin_audit_log MODIFY (target_id NULL);

ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_target_id
    CHECK (target_id IS NOT NULL OR target_type = 'MARKETING');

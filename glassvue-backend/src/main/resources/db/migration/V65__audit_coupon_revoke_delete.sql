-- 쿠폰 발급분 회수 · 쿠폰 정의 삭제를 원장에 남긴다 (2026-09-17, BACKLOG §Q-7).
--
-- ---------------------------------------------------------------- 🔴 왜 필요한가
--
-- 관리자가 쿠폰을 **거둬들일 방법이 없었다** — 쿠폰 API 는 생성·발급·가입 지정뿐이라 잘못 발급한 쿠폰은
-- 만료까지 쓰였고, 검증 잔재(ZZ 쿠폰)도 정상 경로로 못 치웠다. 회수·삭제는 **행을 지운다**(사용자 결정 —
-- revoked 표시가 아니다). 그래서 **이 원장 줄이 유일한 흔적**이다.
--
-- ⚠ 이름 길이: COUPON_REVOKE 13 · COUPON_DELETE 13 — 20자 안이다.
-- 🔴 CHECK 를 안 넓히면 **조작이 통째로 롤백된다**(감사는 발행측 트랜잭션에 합류, V60·V64 와 같은 이유).
-- ⚠ target_type 은 안 넓힌다 — 회수는 MEMBER, 삭제는 COUPON 으로 이미 있는 값이다.
-- ⚠ **구 jar 영향 없음** — 넓히는 방향이다.

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
                      'COUPON_REVOKE', 'COUPON_DELETE',
                      'DISCOUNT_CREATE', 'DISCOUNT_UPDATE', 'DISCOUNT_DELETE',
                      'CATEGORY_CREATE', 'CATEGORY_DELETE',
                      'NOTICE_CREATE', 'NOTICE_UPDATE', 'NOTICE_DELETE',
                      'INQUIRY_ANSWER',
                      'REVIEW_DELETE', 'INQUIRY_DELETE', 'MARKETING_SEND'));

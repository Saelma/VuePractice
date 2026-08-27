-- 관리자 대행 반품 «요청» 을 원장에 남기려고 AuditAction 을 하나 넓힌다
-- (2026-08-27, BACKLOG §I-15 — I-9 결정 4 에서 갈라져 나온 항목).
--
-- ---------------------------------------------------------------- 🔴 왜 필요한가
--
-- 반품 «요청» 은 지금까지 **고객만** 하는 일이라 원장에 남길 이유가 없었다(관리자는 승인·거절만
-- 하고 그 둘은 이미 남는다). I-9 이 7일 기한을 걸면서 **기한을 넘긴 건을 구제할 자리가 아예
-- 없어졌고**, 그래서 관리자 대행 요청 경로를 만든다 — 그 순간 «관리자가 대신 요청했다» 가
-- 원장에 남아야 하는 일이 된다.
--
-- 🔴 **CHECK 제약을 안 넓히면 조작이 통째로 롤백된다** — 감사는 발행측 트랜잭션에 합류하므로
--   `ORA-02290` 이 «원장만 못 남는 것» 이 아니라 **반품 요청 자체가 실패하는 것**이다.
--   ⚠ 그리고 `ddl-auto=validate` 는 CHECK 제약을 안 본다 — **기동은 멀쩡히 되고 누를 때 터진다.**
--
-- ⚠ **컬럼 폭은 안 건드린다.** `action` 은 `VARCHAR2(20 CHAR)` 이고 `ORDER_RETURN_REQUEST` 는
--   **정확히 20자**다(`ORDER_RETURN_APPROVE` 와 같다). 🔴 한 글자만 길었어도 컬럼을 함께 넓혀야
--   했다 — 다음에 action 을 더할 때 **이름 길이부터 센다.**
--
-- ⚠ **기존 데이터 위반 없음** — 값을 «더하는» 변경이라 지금 있는 행은 전부 새 목록에도 들어간다.

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
                      'INQUIRY_ANSWER'));

-- 상품 삭제·복구를 감사 원장에 남긴다 (2026-08-14, BACKLOG 「감사 대상 확대」)
--
-- ⚠ **상품만 빠져 있었다.** 회원 정지·역할변경·삭제, 주문 취소, 리뷰 숨김, 문의 숨김은 다
--   admin_audit_log 에 남는데 상품 조작은 아무 데도 안 남았다(2026-08-12 실측, 세 문서를 건너온 이월).
--   F-7 의 deleted_by_name 은 **복구 화면용 스냅샷**이라 이력이 아니다 — 복구하면 지워진다.
--
-- ---------------------------------------------------------------- 착수 전 결정 (사용자와 확정)
--
-- 🔴 **상품은 «대상에 회원이 있느냐» 를 처음으로 못 넘는 대상이다.**
--   V43(주문 취소)은 대상을 **주문자**로, V44(리뷰·문의 숨김)는 **작성자**로 잡아 통과했다.
--   그 판단은 *"갈리는 기준은 도메인이 아니라 대상에 회원이 있느냐"* 였는데,
--   **상품에는 회원이 없다.** target_id 는 NOT NULL 이라 비워 둘 수도 없다.
--
--   → **target_id 에 상품 id 를 넣고, 열의 뜻을 넓힌다**(2026-08-14 사용자와 확정):
--     «대상의 id — 그게 무엇인지는 action 이 말한다».
--   → target_login 은 NULL(V45 로 이미 nullable). 상품명은 detail 에 **스냅샷**으로 넣는다.
--     상품은 유예가 지나면 진짜로 사라지므로, id 만 남기면 «무엇을 지웠는지» 를 영영 못 읽는다.
--
--   ⚠ **대가를 알고 고른다**: 감사 화면의 검색은 target_login 부분일치뿐이라(AdminAuditLogRepository)
--     상품 행은 **「조작 종류」 필터로만** 걸리고 「대상 아이디」 열은 빈칸이다.
--     회원 아닌 대상이 더 늘면 그때 target_type 열을 세운다 — **지금 만들지 않는다**(미리 만들지 않는다).
--
-- ---------------------------------------------------------------- 남기는 것과 안 남기는 것
--
-- ✅ PRODUCT_DELETE  — 삭제 대기(soft delete). ⚠ **멱등 호출은 안 남는다** — 이미 대기 중이면
--    Product.softDelete() 가 false 를 돌려주고 이벤트를 발행하지 않는다("조작 없이 감사 없다").
-- ✅ PRODUCT_RESTORE — 되돌리기. 같은 규칙이라 복구 버튼을 두 번 눌러도 **한 줄**이다.
-- ❌ 영구 삭제(purge) — **행위자가 사람이 아니다**(ProductPurgeScheduler 가 부른다). actor_id 는
--    NOT NULL 이고 배치를 가리킬 UUID 는 **지어낼 값**이다. 그 시점은 애플리케이션 로그에만 남는다.
-- ❌ 등록·수정 — 이번 범위 밖(사용자와 확정). 빈도가 높아 원장이 빠르게 커지고(F-2 와 같은 압력),
--    «무엇이 바뀌었나» 를 detail 에 어떻게 적을지 따로 정해야 한다.
--
-- 🔴 **과거 삭제는 백필하지 않는다.** 출처가 없다 — deleted_by_name 은 «누가» 만 알고 «언제» 는
--   deleted_at 인데 **복구하면 둘 다 지워진다**. 원장은 오늘부터 시작한다
--   (V44 가 리뷰 숨김에서, V37 이 동의 시각에서 한 판단과 같다 — 모르는 값은 지어내지 않는다).
--
-- ⚠ **넓히는 방향이라 구 jar 는 영향받지 않는다**(구 jar 는 이 값들을 아예 만들지 않는다).
-- ⚠ 적용된 스크립트는 고치지 않는다 — 체크섬이 어긋나면 기동이 통째로 막힌다(V45 주석의 사고).

ALTER TABLE admin_audit_log DROP CONSTRAINT ck_admin_audit_action;

ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('MEMBER_SUSPEND', 'MEMBER_UNSUSPEND', 'MEMBER_ROLE_CHANGE', 'MEMBER_DELETE',
                      'ORDER_CANCEL',
                      'REVIEW_HIDE', 'REVIEW_UNHIDE', 'INQUIRY_HIDE', 'INQUIRY_UNHIDE',
                      'PRODUCT_DELETE', 'PRODUCT_RESTORE'));

COMMENT ON COLUMN admin_audit_log.target_id IS
    '대상의 id. 무엇인지는 action 이 말한다 — 회원(정지·역할변경·삭제) · 주문자(주문 취소) · 작성자(리뷰·문의 숨김) · 상품(상품 삭제·복구)';

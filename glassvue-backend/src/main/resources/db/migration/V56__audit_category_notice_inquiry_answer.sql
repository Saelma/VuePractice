-- 감사 확대 4차 — 카테고리 · 공지 · 문의 답변, 그리고 `target_type` 의 첫 확장
-- (2026-08-21, BACKLOG 「감사 대상 확대 4차」. V50·V51·V53 에 이어 네 번째)
--
-- ---------------------------------------------------------------- 이 스크립트가 답하는 질문
--
-- 🔴 **V53 이 세운 `target_type` 이 확장에 견디는가.** 백로그가 어제 그렇게 적어 뒀다 —
--   *"이 자리가 V53 구조의 첫 시험대다 — 카테고리·공지는 MEMBER·PRODUCT·COUPON 어디에도 안 들어간다."*
--   그전 넷은 전부 기존 값으로 접혔다(할인→상품, 주문·리뷰·문의→회원). **여기서 처음 안 접힌다.**
--
-- ---------------------------------------------------------------- 왜 기존 값으로 접지 않았나
--
-- **카테고리를 PRODUCT 로 접을 수 있었다** — 카테고리는 상품을 담는 그릇이니까.
--   ❌ 접으면 `target_id` 는 카테고리 id 인데 종류는 «상품» 이라, 「대상 종류=상품」으로 훑을 때
--      **상품 이력 사이에 상품 아닌 행이 섞인다.** 대상 종류의 쓸모가 정확히 «회원 아닌 행을
--      갈라 보는 것» 인데, 접는 순간 그 쓸모를 스스로 무너뜨린다.
--
-- **공지를 MEMBER 로 접을 수 있었다** — 공지에는 `author_id`(작성 관리자)가 있다.
--   ❌ 리뷰·문의가 «대상=작성자» 로 통한 이유는 **고객이 쓴 것을 관리자가 조치**해서다.
--      공지는 **관리자가 쓰고 관리자가 고친다** → actor 와 target 이 거의 늘 같은 사람이라
--      원장 한 줄이 아무것도 안 가른다.
--
-- 🔴 **즉 «대상은 id 를 물어볼 값어치가 있는 쪽» 규칙은 그대로 통했고**(V43·V53), 이번엔 그 답이
--   기존 셋 밖에 있었을 뿐이다. **구조를 바꿀 필요가 없었다 — 값만 늘었다.** 그게 시험의 결과다.
--
-- ---------------------------------------------------------------- 문의 답변은 target_type 을 안 건드린다
--
-- 대상이 **질문자(회원)** 라 `MEMBER` 다 — V44 가 문의 숨김에서 한 판단 그대로다.
-- ⚠ 덕분에 같은 회원의 «숨김» 과 «답변» 이 **한 target_id 로 묶인다.**
--
-- ---------------------------------------------------------------- ❌ 이미지 파생은 안 붙인다
--
-- 백로그가 넷 중 *"값어치가 가장 낮다 — 안 붙여도 된다"* 로 적어 둔 항목이다(운영 배치성이고
-- 되돌릴 수 있으며 «누가» 를 물을 일이 없다). **넷을 다 붙이는 것이 목표가 아니다.**
--
-- ---------------------------------------------------------------- 백필 — 여기서는 지어내는 것이 된다
--
-- ⚠ **과거 카테고리·공지·답변 조작은 채우지 않는다.** V53 의 `target_type` 백필은 «action 을 보면
--   결정되는» **계산**이라 채웠지만, 여기서 없는 것은 **«누가·언제»** 이고 그건 아무 데도 없다:
--   - 카테고리: `created_at` 만 있고 행위자 컬럼이 없다. 삭제된 것은 행 자체가 없다.
--   - 공지: `author`·`author_id` 는 **등록자**만 안다 — 수정·삭제는 «누가 언제» 를 안 남겼다.
--   - 답변: `answered_at` 은 있어도 **답변자를 모른다**(V51 이 주문 넷에서 만난 그 모양이다 —
--     «언제는 남고 누가는 안 남았다»).
--   → 원장은 오늘부터 시작한다(V44·V50·V51 과 같은 판단).
--
-- ⚠ **넓히는 방향이라 구 jar 는 영향받지 않는다**(구 jar 는 이 값들을 아예 만들지 않는다).
-- ⚠ 적용된 스크립트는 고치지 않는다 — 체크섬이 어긋나면 기동이 통째로 막힌다.
-- ⚠ 길이 semantics 는 이미 CHAR 다(V54) — 여기서는 열을 안 건드리므로 §2-2-1 이 걸릴 자리가 없다.

-- ---------------------------------------------------------------- ① target_type 두 값 추가

ALTER TABLE admin_audit_log DROP CONSTRAINT ck_admin_audit_target_type;

ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_target_type
    CHECK (target_type IN ('MEMBER', 'PRODUCT', 'COUPON', 'CATEGORY', 'NOTICE'));

-- ---------------------------------------------------------------- ② action 여섯 추가

ALTER TABLE admin_audit_log DROP CONSTRAINT ck_admin_audit_action;

ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('MEMBER_SUSPEND', 'MEMBER_UNSUSPEND', 'MEMBER_ROLE_CHANGE', 'MEMBER_DELETE',
                      'ORDER_CANCEL',
                      'ORDER_SHIP', 'ORDER_DELIVER', 'ORDER_RETURN_APPROVE', 'ORDER_RETURN_REJECT',
                      'REVIEW_HIDE', 'REVIEW_UNHIDE', 'INQUIRY_HIDE', 'INQUIRY_UNHIDE',
                      'PRODUCT_DELETE', 'PRODUCT_RESTORE',
                      'PRODUCT_CREATE', 'PRODUCT_UPDATE',
                      'COUPON_CREATE', 'COUPON_ISSUE', 'COUPON_WELCOME_SET',
                      'DISCOUNT_CREATE', 'DISCOUNT_UPDATE', 'DISCOUNT_DELETE',
                      'CATEGORY_CREATE', 'CATEGORY_DELETE',
                      'NOTICE_CREATE', 'NOTICE_UPDATE', 'NOTICE_DELETE',
                      'INQUIRY_ANSWER'));

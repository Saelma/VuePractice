-- 감사 확대 3차 — 상품 등록·수정 + 쿠폰 + 할인, 그리고 `target_type` 신설
-- (2026-08-20, BACKLOG 「감사 대상 확대」. V50(상품 삭제)·V51(주문 넷)에 이어 세 번째)
--
-- ---------------------------------------------------------------- 왜 지금 target_type 인가
--
-- 🔴 **V50 이 조건을 걸어 뒀다**: *"회원 아닌 감사 대상이 하나 더 늘면 그때가 세울 때다.
--   지금은 상품 하나뿐이라 안 만들었고, 대가는 «감사 검색에 상품 행이 안 걸린다» 하나다."*
--   **오늘 그 조건이 성립한다** — 쿠폰이 들어오면 회원 아닌 대상이 **상품·쿠폰 둘**이 된다.
--
-- ⚠ **지금 안 세우면 이번에 쌓일 행이 전부 «검색으로 못 찾는» 행이 된다.** 실측(2026-08-20):
--   원장 33행 중 target_login 이 빈 것은 **6행**(상품 삭제 4 · 복구 2)뿐인데, 상품 등록·수정과
--   쿠폰·할인이 붙으면 그 몫이 빠르게 커진다. 6행을 참는 것과 절반을 참는 것은 다른 이야기다.
--
-- ---------------------------------------------------------------- 값이 셋인 이유 (DISCOUNT 가 없다)
--
-- `MEMBER` · `PRODUCT` · `COUPON` 셋이다. **할인(DISCOUNT)은 값이 아니다** — 할인 조작의 대상을
-- **상품**으로 잡았기 때문이다(2026-08-20 사용자와 확정):
--   ① 할인 id 는 사람에게 의미가 없다. 궁금한 것은 **«어느 상품의 세일인가»** 다.
--   ② 대상을 상품으로 잡으면 `PRODUCT_UPDATE`·`PRODUCT_DELETE`·`DISCOUNT_CREATE` 가
--      **같은 target_id 로 묶여** «이 상품에 무슨 일이 있었나» 를 한 줄로 훑을 수 있다.
--      🔴 **이건 다른 방법이 없다.** 반대로 «세일 조작만 보기» 는 「조작 종류」 필터로 이미 된다.
--   ⚠ V43(ORDER_CANCEL)이 대상을 «주문» 이 아니라 **«주문자»** 로 잡은 것과 같은 결이다 —
--     대상은 **id 를 물어볼 값어치가 있는 쪽**으로 잡는다.
--
-- ⚠ **COUPON_ISSUE 만 MEMBER 다.** 관리자가 «누구에게» 줬는지가 핵심이라 대상이 회원이고,
--   쿠폰명은 detail 에 넣는다. 쿠폰 정의를 만들거나(COUPON_CREATE) 가입 쿠폰으로 지정하는
--   (COUPON_WELCOME_SET) 것은 대상이 쿠폰이다.
--
-- ---------------------------------------------------------------- 🔴 target_type 은 컬럼이자 **코드의 성질**이다
--
-- 앱에서는 이벤트에 실어 보내지 않고 **`AuditAction` 이 스스로 답한다**(`action.targetType()`).
--   ⚠ 이벤트 파라미터로 받으면 «action 은 PRODUCT_UPDATE 인데 targetType 은 MEMBER» 인 행을
--     만들 수 있다. **그런 행은 뜻이 없고, 만들 수 있게 두면 언젠가 만들어진다.**
--   ⚠ AdminActionEvent 의 기존 주석이 이미 *"무엇인지는 action 이 말한다"* 였다 —
--     **그 말을 사람이 읽는 문장에서 기계가 읽는 값으로 옮긴 것**뿐이다.
--
-- ---------------------------------------------------------------- 백필 — 여기서는 지어내는 것이 아니다
--
-- ⚠ **V44·V50·V51 은 «백필 안 함» 이었는데 여기는 백필한다.** 모순이 아니다:
--   그때 못 채운 것은 **«누가»(행위자)** 였고 그건 **아무 데도 없는 값**이라 지어내는 것이 됐다.
--   `target_type` 은 다르다 — **action 을 보면 결정된다.** 추정이 아니라 **계산**이다.
--
-- ---------------------------------------------------------------- 새 action 여덟
--
-- **상품 등록·수정** — V50 이 *"빈도가 높고 «무엇이 바뀌었나» 를 detail 에 어떻게 적을지 따로
--   정해야 한다"* 로 미뤄 둔 자리다. 오늘 둘 다 답이 나왔다:
--   ① 빈도 걱정은 실측으로 약하다 — **상품 10건**이다(주문 63건에 붙인 V51 보다 훨씬 작다).
--   ② detail 은 **바뀐 것만 «전→후»** 로 적는다(2026-08-20 사용자와 확정).
--      긴 필드(description·tagline)는 전/후를 다 적으면 1000자를 넘기니 **«바뀜» 만** 적고,
--      옵션은 **개수만** 적는다.
--      🔴 **재고는 안 적는다** — `stock_history`(B-19)가 이미 «누가·언제·얼마나» 를 갖고 있다.
--      같은 사실을 두 곳에 적으면 한쪽만 고쳐져 어긋난다.
--   ⚠ **바뀐 것이 하나도 없어도 줄을 남긴다**(2026-08-20 사용자와 확정. detail 은 «변경 없음»).
--     관리 화면이 상품 전체를 다시 보내므로 이 경우가 흔한데, **«누가 언제 손댔나» 자체를
--     접근 기록으로 본다**는 선택이다. 대가는 저장 버튼을 누른 횟수만큼 행이 느는 것이다.
--     ⚠ V50 이 상품 삭제에서 «조용히 통과한 호출은 줄을 만들지 않는다» 로 간 것과 **갈린다** —
--       거기는 조작이 **아무 일도 안 한** 경우였고, 여기는 조작이 **실제로 저장까지 간** 경우다.
--
-- **쿠폰 셋** — 정의 등록 · 관리자 수동 발급 · 가입 쿠폰 지정.
--   ❌ 고객이 배너에서 누르는 이벤트 쿠폰 「받기」(claimEventCoupon)는 **관리자 조작이 아니다.**
--
-- **할인 셋** — 등록 · 수정 · 삭제. 🔴 **세일은 돈을 바꾸는 관리자 조작**이다.
--   실증(2026-08-20): 세일 20% + 쿠폰 5,000 이 겹쳐 정가 12,000 짜리가 7,600 에 나갔는데
--   **원장에 한 줄도 없었다.**
--
-- ⚠ **넓히는 방향이라 구 jar 는 영향받지 않는다**(구 jar 는 이 값들을 아예 만들지 않는다).
-- ⚠ 적용된 스크립트는 고치지 않는다 — 체크섬이 어긋나면 기동이 통째로 막힌다.

-- ---------------------------------------------------------------- ① target_type

-- 우선 nullable 로 더한다 — 기존 33행이 NOT NULL 을 즉시 만족할 수 없다.
ALTER TABLE admin_audit_log ADD target_type VARCHAR2(20);

-- 백필. action 에서 계산한다(추정이 아니다).
UPDATE admin_audit_log
   SET target_type = CASE
       WHEN action IN ('PRODUCT_DELETE', 'PRODUCT_RESTORE') THEN 'PRODUCT'
       ELSE 'MEMBER'
   END;

-- 채운 뒤에 잠근다. ⚠ 순서가 뒤집히면 ORA-02296 으로 기동이 막힌다.
ALTER TABLE admin_audit_log MODIFY target_type VARCHAR2(20) NOT NULL;

ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_target_type
    CHECK (target_type IN ('MEMBER', 'PRODUCT', 'COUPON'));

COMMENT ON COLUMN admin_audit_log.target_type IS
    'target_id 가 무엇의 id 인지. action 이 결정한다(AuditAction.targetType()) — 이벤트로 받지 않는다 (2026-08-20)';

-- ---------------------------------------------------------------- ② action 여덟 추가

ALTER TABLE admin_audit_log DROP CONSTRAINT ck_admin_audit_action;

ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('MEMBER_SUSPEND', 'MEMBER_UNSUSPEND', 'MEMBER_ROLE_CHANGE', 'MEMBER_DELETE',
                      'ORDER_CANCEL',
                      'ORDER_SHIP', 'ORDER_DELIVER', 'ORDER_RETURN_APPROVE', 'ORDER_RETURN_REJECT',
                      'REVIEW_HIDE', 'REVIEW_UNHIDE', 'INQUIRY_HIDE', 'INQUIRY_UNHIDE',
                      'PRODUCT_DELETE', 'PRODUCT_RESTORE',
                      'PRODUCT_CREATE', 'PRODUCT_UPDATE',
                      'COUPON_CREATE', 'COUPON_ISSUE', 'COUPON_WELCOME_SET',
                      'DISCOUNT_CREATE', 'DISCOUNT_UPDATE', 'DISCOUNT_DELETE'));

-- 관리자 대행 주문 취소 (2026-08-10, 백로그 B-25)
--
-- ⚠ **관리자가 주문을 취소할 방법이 없었다.** `/api/admin/orders` 는 GET 3개뿐이고
--   `OrderService.cancel` 은 findByIdAndMemberId 라 **본인 주문만** 취소한다(실측 2026-08-10).
--   CS 로 "취소해 주세요" 가 들어와도 관리자가 대신 해 줄 경로가 없었다.
--
-- ---------------------------------------------------------------- 왜 컬럼이 필요한가
--
-- 취소자가 «항상 본인» 이던 동안에는 행위자를 적을 이유가 없었다(V40 의 cancel_reason 주석에
-- 그렇게 적혀 있다). 관리자 취소가 생기는 순간 그 전제가 깨지고, **누가 취소했는지 모르는 주문**이
-- 생긴다 — 고객은 "내가 안 했는데?" 라 하고 관리자는 되짚을 데가 없다.
--
-- ⚠ **감사 로그(admin_audit_log)만으로는 부족하다** — 감사 조회는 `hasRole('SUPER_ADMIN')` 이라
--   **일반 ADMIN 은 못 본다**. 주문 상세에서 바로 보이려면 주문 쪽에 값이 있어야 한다.
--   그렇다고 컬럼만 두지도 않는다: 회원 정지·역할변경·강제삭제가 전부 감사에 남는데 주문 취소만
--   빠지면 **감사 원장에 구멍이 난다**. 그래서 둘 다 남긴다(2026-08-10 결정).
--
-- ---------------------------------------------------------------- 왜 이름까지 스냅샷인가
--
-- id 만 두면 관리자 계정이 **강제 삭제**되거나(B-24) 개명한 순간 화면에서 «누가» 가 사라진다.
-- 감사 테이블이 actor_name·target_login 을 스냅샷으로 뜨는 것과 같은 이유·같은 방식이다.
-- FK 를 두지 않는 것도 같다 — 대상이 사라져도 읽혀야 하는 값이다.
--
-- ---------------------------------------------------------------- NULL 의 뜻
--
-- ⚠ **NULL = 주문자 본인이 취소했다.** 별도 플래그(is_admin_cancelled 등)를 두지 않았다 —
--   플래그와 id 가 어긋난 행이 생길 수 있고, 그런 행은 **앱이 멀쩡히 돌면서** 화면에만 틀리게 나온다
--   (G-3 의 product_id·inquiry_type 쌍에서 겪은 모양). 값이 하나면 어긋날 자리가 없다.
-- ⚠ **백필하지 않는다** — 관리자 취소가 없던 시절의 주문이라 NULL 이 **사실**이다(V39·V40 과 같은 판단).
--   V41(review.hidden)이 백필한 것은 «아니다» 가 사실이어서였다. "모르는 값" 과 "아니다" 는 다르다.
--
-- ⚠ VARCHAR2 는 **문자 단위**로 잡는다(WA §2-2-1) — member.nickname 과 같은 50 CHAR.
--   바이트로 잡으면 한글 닉네임이 17자쯤에서 ORA-12899 로 취소를 실패시킨다.

ALTER TABLE orders ADD cancelled_by RAW(16);

ALTER TABLE orders ADD cancelled_by_name VARCHAR2(50 CHAR);

COMMENT ON COLUMN orders.cancelled_by IS
    '취소한 관리자 id. NULL 이면 주문자 본인이 취소한 것 (B-25, 2026-08-10)';

COMMENT ON COLUMN orders.cancelled_by_name IS
    '취소한 관리자 닉네임 스냅샷 — 관리자가 삭제·개명돼도 남아야 한다';

-- 인덱스는 두지 않는다. "관리자가 취소한 주문만" 을 찾는 화면이 아직 없고, 있더라도 값 대부분이
-- NULL 이라 일반 인덱스는 그 행들을 아예 안 담는다(Oracle 은 전부 NULL 인 키를 인덱싱하지 않는다) —
-- 오히려 그때는 그 성질이 유리하다. 필요해지면 그때 본다(H-4 와 같은 판단: 지금은 정상, 도구만 있다).

-- ---------------------------------------------------------------- 감사 enum CHECK 확장
--
-- ⚠ ddl-auto 가 validate 라 AuditAction 에 값을 더해도 DB 의 CHECK 는 **절대 자동으로 안 따라온다**.
--   그대로 두면 관리자 취소가 감사를 남기려는 순간 ORA-02290 으로 실패하고, 같은 트랜잭션이라
--   **취소 자체가 롤백**된다 — "기능이 통째로 안 되는" 증상으로 나타난다(V35 와 같은 자리).
-- ⚠ 넓히는 방향이라 구 jar 는 영향받지 않는다(구 jar 는 ORDER_CANCEL 을 아예 만들지 않는다).

ALTER TABLE admin_audit_log DROP CONSTRAINT ck_admin_audit_action;

ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('MEMBER_SUSPEND', 'MEMBER_UNSUSPEND', 'MEMBER_ROLE_CHANGE', 'MEMBER_DELETE',
                      'ORDER_CANCEL'));

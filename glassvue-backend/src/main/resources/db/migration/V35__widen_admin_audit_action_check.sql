-- V35: admin_audit_log.action 의 CHECK 제약에 MEMBER_DELETE 를 더한다 (B-24, 2026-07-30).
--
-- 왜 마이그레이션이 필요한가:
--   ddl-auto 가 validate 라 enum 값을 하나 더해도 DB 의 CHECK 는 절대 자동으로 안 따라온다.
--   그대로 두면 관리자 강제 삭제가 감사 이력을 남기려는 순간 ORA-02290(CHECK 위반)으로 실패하고,
--   같은 트랜잭션이라 삭제 자체가 롤백된다 — "기능이 통째로 안 되는" 증상으로 나타난다.
--   (2026-07-24 orders.status 에서 겪은 그 트랩. 메모리: oracle-enum-check-constraint-trap)
--
-- V31(role) 과 다른 점:
--   그때는 제약 이름이 시스템 생성이라 search_condition_vc 를 조회해 동적으로 DROP 해야 했다.
--   여기는 V32 가 ck_admin_audit_action 으로 **이름을 붙여 뒀으므로** 이름으로 바로 지운다.
--   → 제약에 이름을 주는 습관이 이 마이그레이션을 두 줄로 줄였다.
--
-- 구 jar 영향:
--   ⚠ CHECK 를 **넓히는** 방향이라 구 jar 는 영향받지 않는다(구 jar 는 새 값을 아예 만들지 않는다).
--   좁히는 변경이었다면 배포 순서를 따져야 했다(WA §5 의 UNIQUE 사고).

ALTER TABLE admin_audit_log DROP CONSTRAINT ck_admin_audit_action;

ALTER TABLE admin_audit_log ADD CONSTRAINT ck_admin_audit_action
    CHECK (action IN ('MEMBER_SUSPEND', 'MEMBER_UNSUSPEND', 'MEMBER_ROLE_CHANGE', 'MEMBER_DELETE'));

-- 관리자 감사 이력 (2026-07-28, 회원관리 심화)
--
-- 관리자 조작(회원 정지·해제·역할변경)이 지금은 SLF4J 로그로만 흘러가 조회할 수 없다. 누가 누구를 언제
-- 어떻게 바꿨는지를 append-only 로 남겨, 최상위 관리자(SUPER_ADMIN)가 조회할 수 있게 한다.
--
-- ⚠ 이 마이그레이션은 **순수 추가**다. 새 테이블 하나뿐이라 구 jar 에 아무 영향이 없다.
--
-- 설계 메모:
--  · actor_id / target_id 는 **FK 없는 느슨한 UUID**(도메인 경계 — audit 이 member 를 밖에서 가리킨다).
--    대상이 나중에 탈퇴·개명·강등돼도 이력이 깨지지 않도록 그 시점의 actor_name·target_login 을 스냅샷으로 박는다.
--  · action 은 문자열(enum) + CHECK. 값을 늘리면 이 CHECK 도 함께 넓혀야 한다(Oracle enum CHECK 트랩).
--  · 이름 컬럼은 전부 CHAR semantics(member.login_id·nickname 이 VARCHAR2(50 CHAR) — 맞춘다).
--  · created_at/updated_at 은 TIMESTAMP(9) WITH TIME ZONE(감사 컬럼 규약). append-only 라 updated_at 은 사실상 created_at 과 같다.

CREATE TABLE admin_audit_log (
    id           RAW(16)                     NOT NULL,
    action       VARCHAR2(20 CHAR)           NOT NULL,
    actor_id     RAW(16)                     NOT NULL,
    actor_name   VARCHAR2(50 CHAR)           NOT NULL,
    target_id    RAW(16)                     NOT NULL,
    target_login VARCHAR2(50 CHAR)           NOT NULL,
    detail       VARCHAR2(1000 CHAR),
    created_at   TIMESTAMP(9) WITH TIME ZONE,
    updated_at   TIMESTAMP(9) WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT ck_admin_audit_action
        CHECK (action IN ('MEMBER_SUSPEND', 'MEMBER_UNSUSPEND', 'MEMBER_ROLE_CHANGE'))
);

-- 조회 경로 둘 다 최신순 페이징이다: ① 전체 최신순, ② 특정 대상(target_login) 최신순.
-- 목록 기본 정렬이 created_at DESC 라, 그 정렬을 그대로 타도록 인덱스를 둔다.
CREATE INDEX ix_admin_audit_created ON admin_audit_log (created_at DESC);
CREATE INDEX ix_admin_audit_target ON admin_audit_log (target_id);

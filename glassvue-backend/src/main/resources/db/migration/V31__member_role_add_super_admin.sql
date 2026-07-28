-- 최상위 관리자(SUPER_ADMIN) 역할 추가 (2026-07-28)
--
-- member.role 의 CHECK 제약이 값을 ('USER','ADMIN') 로 고정하고 있다. SUPER_ADMIN 을 허용하려면
-- 이 제약을 교체해야 한다(Oracle enum CHECK 트랩 — ddl-auto=update 로는 안 고쳐진다).
--
-- ⚠ 제약 이름이 **시스템 생성(SYS_C...)** 이라 환경마다 다르다(espdb 와 esptest 가 서로 다른 이름).
--    그래서 이름으로 DROP 하면 빈 DB 검증에서 깨진다 — search_condition 으로 **찾아서** 지운다.
-- ⚠ 값을 **넓히는** 변경이라 구 jar 무해(기존 'USER'/'ADMIN' write 는 새 CHECK 도 통과). 아직 SUPER_ADMIN
--    행은 없다 — 특정 계정 승격은 신 jar 배포 **후** 별도 데이터 작업으로 한다(구 jar 는 SUPER_ADMIN 을
--    enum 으로 못 읽어 그 회원 로딩이 깨지므로, 순서가 반대면 안 된다).

BEGIN
  FOR c IN (SELECT constraint_name FROM user_constraints
            WHERE table_name = 'MEMBER' AND constraint_type = 'C'
              AND search_condition_vc LIKE '%''USER''%') LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE member DROP CONSTRAINT ' || c.constraint_name;
  END LOOP;
END;
/

ALTER TABLE member ADD CONSTRAINT ck_member_role CHECK (role IN ('USER','ADMIN','SUPER_ADMIN'));

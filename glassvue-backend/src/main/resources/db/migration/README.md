# Flyway 마이그레이션

기존 espdb는 `baseline-on-migrate`로 **V1(baseline)** 처리됨 — 이미 있던 테이블(ddl-auto=update로 생성)은
건드리지 않고, **앞으로의 스키마 변경만 여기서 버전관리**한다.

## 규칙
- 파일명: `V<버전>__<설명>.sql` (예: `V2__add_order_paid_index.sql`). 버전은 2부터.
- 한 번 적용된 스크립트는 **수정 금지**(체크섬 불일치). 되돌리려면 새 버전으로 보정.
- Oracle 문법. enum CHECK 제약 변경 등도 이제 여기서 (수동 sqlplus ALTER 대신).

## 예시 (다음에 enum 값 추가 시)
```sql
-- V2__extend_order_status_check.sql
ALTER TABLE orders DROP CONSTRAINT ck_orders_status;
ALTER TABLE orders ADD CONSTRAINT ck_orders_status
  CHECK (status IN ('ORDERED','PAID','SHIPPED','DELIVERED','CANCELLED'));
```

## V1__init.sql (2026-07-20 추가)

빈 DB에서 처음부터 구축할 수 있게 `V1__init.sql`을 추가했다. 그전에는 V1이 **마킹만** 돼 있어
신규 환경에서 V2의 `CREATE INDEX`가 테이블 부재로 실패했다.

- **기존 espdb에서는 실행되지 않는다.** 이미 V1이 `BASELINE` 타입으로 기록돼 있고 Flyway는
  baseline 버전 이하를 건너뛴다. (실측: 기동 시 `Schema "ESP" is up to date. No migration necessary.`,
  히스토리 무변화)
- **현재 엔티티가 아니라 baseline 시점(V2 이전) 스키마다.** Hibernate 덤프를 그대로 쓰면
  V3~V5가 추가하는 컬럼이 이미 들어 있어, 빈 DB에서 "이미 있는 컬럼 ADD"로 실패한다.
- **검증 방법**(2026-07-20 실측): 빈 스키마를 만들어 앱을 띄우면 V1→V5가 순서대로 적용되고
  `ddl-auto=validate`가 통과해야 한다.
  ```bash
  # 1) 스크래치 계정 (DBA 필요)
  sudo -iu oracle bash -c 'sqlplus -S / as sysdba' <<'EOF'
  ALTER SESSION SET CONTAINER=espdb;
  CREATE USER esptest IDENTIFIED BY "TestPw#2026" QUOTA UNLIMITED ON USERS;
  GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO esptest;
  EOF
  # 2) 그 계정으로 기동 (기본 DB를 안 건드리게 자격증명만 덮어쓴다)
  ./gradlew bootRun --args="--server.port=8083 --spring.profiles.active=dev \
      --spring.datasource.username=esptest --spring.datasource.password=TestPw#2026"
  # 3) 끝나면 DROP USER esptest CASCADE;
  ```

> 앞으로 엔티티가 바뀌면 `V1__init.sql`이 아니라 **새 버전**을 추가한다. 적용된 마이그레이션은
> 수정 금지(체크섬)이고, V1은 baseline 시점 스냅샷으로 고정된 채 남는다.

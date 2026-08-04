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
  검증 전용 계정 **`esptest`는 이미 만들어 두었고 상시 유지한다**(지우지 말 것).
  마이그레이션을 추가할 때마다 여기서 한 번 돌려보면 된다.

  ```bash
  # 0) sqlplus는 ORACLE_HOME을 안 잡으면 SP2-0667로 죽는다
  #
  # ⚠ **비밀번호를 이 문서에 적지 않는다**(2026-07-29 정정). 예전엔 여기에 평문으로 적혀 있었는데,
  #    저장소를 공개하면 그대로 나가고 **파일에서 지워도 git 히스토리에 남는다.**
  #    그래서 값은 `.env` 의 `ESPTEST_PASSWORD` 한 곳에만 두고 여기서는 변수로만 참조한다
  #    (DB_PASSWORD·JWT_SECRET 과 같은 취급 — infra/env.example 의 키 목록 참고).
  set -a; . /home/ecstel/work/.env; set +a
  export ORACLE_HOME=/opt/oracle/product/19c/dbhome_1
  export LD_LIBRARY_PATH=$ORACLE_HOME/lib:$LD_LIBRARY_PATH

  # 1) 이전 검증 결과를 비운다 (빈 스키마에서 시작해야 의미가 있다)
  #
  # ⚠ sudo·sysdba가 필요 없다 — esptest로 직접 접속해 **자기 스키마**를 비운다.
  #    (2026-07-24에 바꿨다. 예전엔 `sudo -iu oracle ... as sysdba` + dba_tables였는데,
  #     자기 객체를 DROP 하는 데는 DBA 권한이 필요 없다. sudo가 필요한 절차는 사람 손을
  #     기다리게 만들어 검증을 건너뛰게 한다.)
  #
  # ⚠ 시퀀스를 빼먹지 말 것. 테이블만 지우면 V15가 만든 seq_order_no가 살아남아
  #    다음 검증에서 CREATE SEQUENCE가 ORA-00955(이미 사용 중인 이름)로 실패한다.
  #    2026-07-23 V16 검증에서 실제로 걸렸다.
  sqlplus -s "esptest/$ESPTEST_PASSWORD@//$DB_HOST:$DB_PORT/${DB_SERVICE:-espdb}" <<'EOF'
  BEGIN
    FOR t IN (SELECT table_name FROM user_tables) LOOP
      EXECUTE IMMEDIATE 'DROP TABLE "'||t.table_name||'" CASCADE CONSTRAINTS PURGE';
    END LOOP;
    FOR s IN (SELECT sequence_name FROM user_sequences) LOOP
      EXECUTE IMMEDIATE 'DROP SEQUENCE "'||s.sequence_name||'"';
    END LOOP;
  END;
  /
  select count(*) as tables_left from user_tables;   -- 0 이어야 한다
  select count(*) as seqs_left   from user_sequences; -- 0 이어야 한다
  exit
  EOF

  # 2) 그 계정으로 기동 (기본 DB를 안 건드리게 자격증명만 덮어쓴다)
  #
  # ⚠ **자격증명을 `--args` 로 넘기지 않는다**(2026-08-04 확정). 커맨드라인 인자는
  #    `/proc/<pid>/cmdline` 이 **누구에게나 읽히므로** 같은 호스트의 다른 사용자에게 평문으로 보인다
  #    (2026-08-03 V37 검증 때 실제로 프로세스 목록에서 확인했다).
  #    스프링이 읽는 **환경변수**로 넘기면 인자에 값이 남지 않는다(relaxed binding).
  export SPRING_DATASOURCE_USERNAME=esptest
  export SPRING_DATASOURCE_PASSWORD="$ESPTEST_PASSWORD"
  ./gradlew bootRun --args="--server.port=8083 --spring.profiles.active=dev"
  # 로그에 V1→…→Vn이 순서대로 applied 되고 앱이 뜨면 성공(ddl-auto=validate 통과 = 엔티티와 일치).
  # 확인 후 반드시 내린다 — 8083이 떠 있으면 다음 검증이 포트 충돌로 죽는다.

  # 2-1) 노출이 실제로 사라졌는지 **떠 있는 동안** 확인한다 (내려간 뒤엔 볼 수 없다)
  PID=$(ss -ltnp | sed -n 's/.*:8083 .*pid=\([0-9]*\).*/\1/p' | head -1)
  tr '\0' '\n' < /proc/$PID/cmdline | grep -i password   # 아무것도 안 나와야 한다
  ps -ef | grep -v grep | grep -cF "$ESPTEST_PASSWORD"   # 0 이어야 한다
  # 2026-08-04 V39 실측: cmdline 인자는 --server.port=8083 · --spring.profiles.active=dev **둘뿐**,
  # ps 전수에서 값 0건.
  #
  # ⚠ 다만 값이 **사라진 게 아니라 옮겨간 것**이다 — 환경변수는 `/proc/<pid>/environ` 에 남는다.
  #    그쪽은 **프로세스 소유자만** 읽을 수 있어 cmdline 보다 낫지만, 같은 계정으로 들어온 사람에겐
  #    여전히 보인다. 검증 전용 로컬 계정이라 여기까지로 둔다(운영 자격증명은 이 경로로 넘기지 않는다).
  ```

  > 계정이 없어졌다면 다시 만든다(DBA 필요):
  > ```sql
  > ALTER SESSION SET CONTAINER=espdb;
  > CREATE USER esptest IDENTIFIED BY "<비밀번호>" QUOTA UNLIMITED ON USERS;   -- 값은 .env 의 ESPTEST_PASSWORD 와 맞춘다
  > GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO esptest;
  > ```

> 앞으로 엔티티가 바뀌면 `V1__init.sql`이 아니라 **새 버전**을 추가한다. 적용된 마이그레이션은
> 수정 금지(체크섬)이고, V1은 baseline 시점 스냅샷으로 고정된 채 남는다.

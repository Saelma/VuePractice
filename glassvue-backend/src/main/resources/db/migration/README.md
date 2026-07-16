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

> 참고: V1 전체 스키마 baseline 스크립트는 아직 없음(기존 DB는 baseline-on-migrate로 마킹). 신규 환경에서
> 처음부터 Flyway로 구축하려면 Hibernate 스키마 생성(`jakarta.persistence.schema-generation`)으로 V1을 뽑아 추가.

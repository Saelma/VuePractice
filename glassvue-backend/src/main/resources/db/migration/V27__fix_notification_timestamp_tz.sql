-- V26 이 created_at/updated_at 을 plain TIMESTAMP(6) 로 만들었는데, 기존 모든 테이블과
-- BaseTimeEntity(Instant) 는 TIMESTAMP(9) WITH TIME ZONE 을 쓴다. plain TIMESTAMP 라
-- 읽을 때 ORA-18716("시간대에 없습니다") 가 나 알림 조회가 500 으로 터졌다(2026-07-24 배포 직후 실측).
-- ⚠ ddl-auto=validate 가 이 차이를 못 걸렀다 — WITH TIME ZONE 유무는 검증이 통과시킨다(교훈: 새 테이블의
--    감사 컬럼은 반드시 TIMESTAMP(9) WITH TIME ZONE 으로 만든다).
--
-- 컬럼 데이터타입 변경(MODIFY)은 컬럼이 비어 있어야 한다(ORA-01439). 방금 만든 테이블이라 내용은
-- 배포 직후 검증분(알림 몇 건·설정 토글 1개)뿐이라 버려도 된다 → TRUNCATE 후 타입을 관례에 맞춘다.
TRUNCATE TABLE notification;
TRUNCATE TABLE member_notification_pref;

ALTER TABLE notification MODIFY (
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE
);

ALTER TABLE member_notification_pref MODIFY (
    created_at TIMESTAMP(9) WITH TIME ZONE,
    updated_at TIMESTAMP(9) WITH TIME ZONE
);

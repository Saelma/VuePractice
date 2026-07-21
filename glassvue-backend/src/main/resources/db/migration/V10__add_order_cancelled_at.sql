-- 주문 취소 시각. 결제(paid_at)·발송(shipped_at)과 같은 성격의 기록으로, "언제 취소됐는지"는
-- CS·정산에서 필요하다. updated_at으로는 대체할 수 없다 — 다른 변경에도 갱신되므로 취소 시각이라 단정 못 함.
-- nullable: 기존 취소 주문은 그 시각을 알 방법이 없어 백필하지 않는다(화면은 값이 없으면 시각을 감춘다).
ALTER TABLE orders ADD cancelled_at TIMESTAMP(9) WITH TIME ZONE;

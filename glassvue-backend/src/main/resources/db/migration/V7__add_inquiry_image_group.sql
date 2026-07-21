-- 문의(inquiry) 첨부 이미지 지원. 리뷰 포토(V3, review.image_group_id)와 동일한 구조 —
-- image 도메인 엔티티를 직접 참조하지 않고 느슨한 UUID(image_group_id)로만 연결한다.
-- nullable: 기존 문의는 첨부가 없으므로 백필 불필요. ddl-auto=validate라 이 컬럼이 없으면 앱이 안 뜬다.
ALTER TABLE inquiry ADD image_group_id RAW(16);

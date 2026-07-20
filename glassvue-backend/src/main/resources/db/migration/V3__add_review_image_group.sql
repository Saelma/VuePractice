-- 포토 리뷰 — review에 image_group_id 추가.
-- ImageGroup이 설계한 "여러 도메인이 image_group_id만 두면 이미지 재사용" 구조를 review가 두 번째로 사용.
-- product.image_group_id와 동일하게 FK 없는 느슨한 UUID 참조(RAW(16), nullable = 이미지 없는 리뷰 허용).
ALTER TABLE review ADD image_group_id RAW(16);

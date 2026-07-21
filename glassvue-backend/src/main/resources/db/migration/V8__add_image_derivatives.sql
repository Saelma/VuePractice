-- 이미지 파생본(표시용 리사이즈 WebP) URL 컬럼. 원본(url)은 그대로 두고 medium/thumb를 따로 둔다.
-- nullable: 기존 이미지는 파생본이 없어(생성 전) null → 응답이 원본으로 폴백한다. 백필 불필요.
ALTER TABLE image ADD (
    medium_url VARCHAR2(500),
    thumb_url  VARCHAR2(500)
);

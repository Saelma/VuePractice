package com.glassvue.domain.image.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이미지 묶음 앵커. 여러 도메인(상품·공지·리뷰…)이 image_group_id만 두면 이미지를 재사용할 수 있다
 * (polymorphic FK 회피). 그룹 자체는 id만 갖고, 이미지들이 이 그룹을 가리킨다.
 */
@Entity
@Getter
@Table(name = "image_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageGroup extends BaseTimeEntity {

    public static ImageGroup create() {
        return new ImageGroup();
    }
}

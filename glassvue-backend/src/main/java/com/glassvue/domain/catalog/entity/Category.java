package com.glassvue.domain.catalog.entity;

import com.glassvue.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Builder
    private Category(String name) {
        this.name = name;
    }

    public void updateName(String name) {
        this.name = name;
    }
}

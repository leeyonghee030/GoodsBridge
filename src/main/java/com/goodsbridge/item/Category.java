package com.goodsbridge.item;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category_tb")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Builder
    public Category(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("카테고리 이름은 필수입니다.");
        }
        if (name.length() > 50) {
            throw new IllegalArgumentException("카테고리 이름은 50자를 초과할 수 없습니다.");
        }
        this.name = name;
    }
}

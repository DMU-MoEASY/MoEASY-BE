package com.moeasy.moeasybe.domain.region.entity;

import com.moeasy.moeasybe.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "region",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_region_code", columnNames = "code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 5, columnDefinition = "CHAR(5)")
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "province_name", nullable = false, length = 100)
    private String provinceName;

    @Builder
    private Region(String code, String name, String provinceName) {
        this.code = code;
        this.name = name;
        this.provinceName = provinceName;
    }
}

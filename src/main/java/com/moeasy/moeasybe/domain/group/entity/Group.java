package com.moeasy.moeasybe.domain.group.entity;

import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "cover_image_key", length = 500)
    private String coverImageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "region_code", nullable = false, length = 32)
    private String regionCode;

    @Column(name = "place_name", nullable = false, length = 100)
    private String placeName;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "max_members", nullable = false)
    private Integer maxMembers;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_policy", nullable = false, length = 32)
    private GroupJoinPolicy joinPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GroupStatus status;

    @Builder
    private Group(Category category, String name, String coverImageKey, String description,
            String regionCode, String placeName, String address, BigDecimal latitude,
            BigDecimal longitude, Integer maxMembers, GroupJoinPolicy joinPolicy) {
        this.category = category;
        this.name = name;
        this.coverImageKey = coverImageKey;
        this.description = description;
        this.regionCode = regionCode;
        this.placeName = placeName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxMembers = maxMembers;
        this.joinPolicy = joinPolicy;
        this.status = GroupStatus.ACTIVE;
    }

    public void changeCategory(Category category) { this.category = category; }
    public void changeName(String name) { this.name = name; }
    public void changeCoverImageKey(String coverImageKey) { this.coverImageKey = coverImageKey; }
    public void changeDescription(String description) { this.description = description; }
    public void changeRegionCode(String regionCode) { this.regionCode = regionCode; }
    public void changePlaceName(String placeName) { this.placeName = placeName; }
    public void changeAddress(String address) { this.address = address; }
    public void changeCoordinates(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
    public void close(LocalDateTime closedAt) {
        this.status = GroupStatus.CLOSED;
        markDeletedAt(closedAt);
    }
}

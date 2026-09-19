package com.moeasy.moeasybe.domain.member.entity;

import com.moeasy.moeasybe.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "member",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_social_type_social_id",
                        columnNames = {"social_type", "social_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "social_id", nullable = false, length = 255)
    private String socialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_type", nullable = false, length = 20)
    private SocialType socialType;

    @Column(length = 50)
    private String nickname;

    @Column(name = "status_message", length = 255)
    private String statusMessage;

    @Column(name = "profile_image_key", length = 500)
    private String profileImageKey;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    @Column(name = "manner_temp", nullable = false, precision = 4, scale = 1)
    private BigDecimal mannerTemp = new BigDecimal("36.5");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Builder
    private Member(
            SocialType socialType,
            String socialId,
            String nickname,
            String statusMessage,
            String profileImageKey
    ) {
        this.socialType = socialType;
        this.socialId = socialId;
        this.nickname = nickname;
        this.statusMessage = statusMessage;
        this.profileImageKey = profileImageKey;
    }
}

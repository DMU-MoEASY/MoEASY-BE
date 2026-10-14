package com.moeasy.moeasybe.domain.group.entity;

import com.moeasy.moeasybe.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "group_cover_upload")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupCoverUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false, length = 500, unique = true)
    private String objectKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Builder
    private GroupCoverUpload(String objectKey, Member member, String contentType, LocalDateTime issuedAt) {
        this.objectKey = objectKey;
        this.member = member;
        this.contentType = contentType;
        this.issuedAt = issuedAt;
    }
}

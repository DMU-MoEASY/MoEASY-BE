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
@Table(name = "group_deletion_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupDeletionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, unique = true)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deleted_by_member_id", nullable = false)
    private Member deletedByMember;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "confirmation_text", nullable = false, length = 100)
    private String confirmationText;

    @Column(name = "archive_content", nullable = false)
    private boolean archiveContent;

    @Column(name = "closed_at", nullable = false)
    private LocalDateTime closedAt;

    @Builder
    private GroupDeletionHistory(Group group, Member deletedByMember, String reason,
            String confirmationText, boolean archiveContent, LocalDateTime closedAt) {
        this.group = group;
        this.deletedByMember = deletedByMember;
        this.reason = reason;
        this.confirmationText = confirmationText;
        this.archiveContent = archiveContent;
        this.closedAt = closedAt;
    }
}

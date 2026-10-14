package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.storage.S3StorageService;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GroupCoverUploadIntegrationTest {
    @Autowired private GroupCoverImageService coverImageService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private S3StorageService storageService;

    @Test
    @DisplayName("커버 키를 발급하면 인증 회원의 소유권 이력을 저장한다")
    void issueUpload_validMember_persistsOwnership() {
        // given
        String socialId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO member (social_id, social_type, nickname) VALUES (?, 'KAKAO', ?)",
                socialId, socialId.substring(0, 12));
        Long memberId = jdbcTemplate.queryForObject("SELECT id FROM member WHERE social_id = ?", Long.class,
                socialId);
        when(storageService.createPresignedPutUrl(any(), any()))
                .thenReturn(URI.create("https://example.test/upload"));

        // when
        GroupCoverImageService.UploadIssue issue = coverImageService.issueUpload(memberId, "image/jpeg");

        // then
        assertThat(issue.coverImageKey()).startsWith("group-covers/" + memberId + "/");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM group_cover_upload
                WHERE object_key = ? AND member_id = ? AND content_type = 'image/jpeg'
                """, Integer.class, issue.coverImageKey(), memberId)).isEqualTo(1);
    }
}

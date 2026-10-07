package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.global.config.AwsS3Properties;
import com.moeasy.moeasybe.domain.group.entity.GroupCoverUpload;
import com.moeasy.moeasybe.domain.group.repository.GroupCoverUploadRepository;
import com.moeasy.moeasybe.domain.group.validate.GroupCoverImageValidator;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.storage.S3StorageService;
import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class GroupCoverImageServiceTest {
    @Mock private S3StorageService storageService;
    @Mock private S3Client s3Client;
    @Mock private GroupCoverUploadRepository uploadRepository;
    @Mock private MemberRepository memberRepository;
    private GroupCoverImageService service;
    private GroupCoverImageValidator validator;

    @BeforeEach
    void setUp() {
        AwsS3Properties properties = new AwsS3Properties();
        AwsS3Properties.S3 s3 = new AwsS3Properties.S3();
        s3.setBucket("test-bucket");
        properties.setS3(s3);
        validator = new GroupCoverImageValidator(s3Client, properties, uploadRepository);
        service = new GroupCoverImageService(storageService, uploadRepository,
                memberRepository, validator, Clock.systemUTC());
    }

    @Test
    @DisplayName("커버 업로드 키는 회원 경로로 발급된다")
    void issueUpload_validImage_returnsOwnedKey() {
        // given
        when(storageService.createPresignedPutUrl(any(), any())).thenReturn(URI.create("https://example.test/upload"));
        when(memberRepository.findById(12L)).thenReturn(Optional.of(Member.builder().build()));

        // when
        GroupCoverImageService.UploadIssue issue = service.issueUpload(12L, "image/jpeg");

        // then
        assertThat(issue.coverImageKey()).matches("group-covers/12/[0-9a-f-]{36}");
        assertThat(issue.uploadUrl()).isEqualTo("https://example.test/upload");
    }

    @Test
    @DisplayName("다른 회원 경로의 커버 키는 S3 요청 없이 거부한다")
    void verifyOwnedAndExists_otherOwner_rejectsKey() {
        // given
        String key = "group-covers/13/123e4567-e89b-12d3-a456-426614174000";

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
    }

    @Test
    @DisplayName("회원 경로의 이미지 객체가 존재하면 키를 허용한다")
    void verifyOwnedAndExists_presentImage_acceptsKey() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentType("image/png").build());
        ArgumentCaptor<HeadObjectRequest> request = ArgumentCaptor.forClass(HeadObjectRequest.class);

        // when
        validator.verifyOwnedAndExists(12L, key);

        // then
        verify(s3Client).headObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo(key);
    }

    @Test
    @DisplayName("존재하지 않는 객체는 거부한다")
    void verifyOwnedAndExists_missingObject_rejectsKey() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("missing").build());

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
    }

    @Test
    @DisplayName("발급 이력이 없는 키는 S3 객체 조회 전에 거부한다")
    void verifyOwnedAndExists_unissuedKey_rejectsKey() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
    }

    private GroupCoverUpload issuance(String key) {
        return GroupCoverUpload.builder().objectKey(key).contentType("image/png").build();
    }
}

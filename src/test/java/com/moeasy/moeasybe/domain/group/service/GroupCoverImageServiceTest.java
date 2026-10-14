package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.domain.group.entity.GroupCoverUpload;
import com.moeasy.moeasybe.domain.group.repository.GroupCoverUploadRepository;
import com.moeasy.moeasybe.domain.group.validate.GroupCoverImageValidator;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.global.apiPayload.handler.GeneralExceptionAdvice;
import com.moeasy.moeasybe.global.config.AwsS3Properties;
import com.moeasy.moeasybe.storage.S3StorageService;
import com.moeasy.moeasybe.storage.exception.StorageOperationException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import tools.jackson.databind.ObjectMapper;

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
    @DisplayName("회원이 없으면 인증 오류를 반환하고 업로드 URL을 서명하거나 이력을 저장하지 않는다")
    void issueUpload_missingMember_doesNotSignOrSave() {
        // given
        when(memberRepository.findById(12L)).thenReturn(Optional.empty());

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.issueUpload(12L, "image/jpeg"));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("AUTH401_1");
        verify(storageService, never()).createPresignedPutUrl(any(), any());
        verify(uploadRepository, never()).save(any());
    }

    @Test
    @DisplayName("서비스를 직접 호출할 때 유효하지 않은 회원 ID는 조회와 서명 전에 거부한다")
    void issueUpload_invalidMemberId_rejectsBeforeLookupOrSign() {
        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> service.issueUpload(0L, "image/jpeg"));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
        verify(memberRepository, never()).findById(any());
        verify(storageService, never()).createPresignedPutUrl(any(), any());
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
    @DisplayName("발급 이력과 S3 객체의 Content-Type이 다르면 거부한다")
    void verifyOwnedAndExists_contentTypeMismatch_rejectsKey() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentType("image/jpeg").build());

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
    }

    @Test
    @DisplayName("일반 404 뒤 정확한 키가 없음을 목록으로 확인하면 거부한다")
    void verifyOwnedAndExists_missingObject_rejectsKey() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("missing").build());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());
        ArgumentCaptor<ListObjectsV2Request> listRequest = ArgumentCaptor.forClass(ListObjectsV2Request.class);

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
        verify(s3Client).listObjectsV2(listRequest.capture());
        assertThat(listRequest.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(listRequest.getValue().prefix()).isEqualTo(key);
        assertThat(listRequest.getValue().maxKeys()).isEqualTo(1);
    }

    @Test
    @DisplayName("NoSuchKey 응답도 목록에서 키 부재를 확인한 뒤 거부한다")
    void verifyOwnedAndExists_noSuchKey_rejectsKey() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("missing").build());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
    }

    @Test
    @DisplayName("403 뒤 정확한 키가 없음을 목록으로 확인하면 잘못된 커버 키로 거부한다")
    void verifyOwnedAndExists_forbiddenMissingObject_rejectsKey() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("access denied").build());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
    }

    @Test
    @DisplayName("403 뒤 키가 목록에 있으면 접근 실패를 저장소 오류로 처리한다")
    void verifyOwnedAndExists_forbiddenExistingObject_returnsStorageUnavailable() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("access denied").build());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key(key).build()).isTruncated(false).build());

        // when
        StorageOperationException exception = assertThrows(StorageOperationException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("STORAGE503_1");
    }

    @Test
    @DisplayName("403 뒤 목록 조회가 거부되면 객체 부재를 추정하지 않는다")
    void verifyOwnedAndExists_forbiddenListing_returnsStorageUnavailable() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("access denied").build());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("list denied").build());

        // when
        StorageOperationException exception = assertThrows(StorageOperationException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("STORAGE503_1");
    }

    @Test
    @DisplayName("일반 404 뒤 목록 조회도 실패하면 버킷 오류를 객체 부재로 오인하지 않는다")
    void verifyOwnedAndExists_notFoundListingFailure_returnsStorageUnavailable() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(NoSuchBucketException.builder().statusCode(404).message("missing bucket").build());

        // when
        StorageOperationException exception = assertThrows(StorageOperationException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("STORAGE503_1");
    }

    @Test
    @DisplayName("목록이 한 건에서 잘리면 정확한 키가 없다고 확정하지 않는다")
    void verifyOwnedAndExists_truncatedListing_returnsStorageUnavailable() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key(key + "-suffix").build())
                        .isTruncated(true).build());

        // when
        StorageOperationException exception = assertThrows(StorageOperationException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("STORAGE503_1");
    }

    @Test
    @DisplayName("S3 장애는 저장소 오류로 응답하고 AWS 상세 내용을 숨긴다")
    void verifyOwnedAndExists_s3Failure_returnsStorageUnavailable() throws Exception {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("AWS internal detail").build());

        // when
        StorageOperationException exception = assertThrows(StorageOperationException.class,
                () -> validator.verifyOwnedAndExists(12L, key));
        ResponseEntity<ApiResponse<Void>> response = new GeneralExceptionAdvice().handleGeneralException(exception);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("STORAGE503_1");
        assertThat(response.getBody().getMessage()).isEqualTo("저장소를 일시적으로 사용할 수 없습니다.");
        assertThat(new ObjectMapper().writeValueAsString(response.getBody()))
                .doesNotContain("AWS internal detail");
    }

    @Test
    @DisplayName("S3 연결 실패도 저장소 장애로 응답한다")
    void verifyOwnedAndExists_clientFailure_returnsStorageUnavailable() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("connection failed").build());

        // when
        StorageOperationException exception = assertThrows(StorageOperationException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("STORAGE503_1");
    }

    @Test
    @DisplayName("버킷이 없으면 객체가 없는 경우와 구분해 저장소 장애로 처리한다")
    void verifyOwnedAndExists_missingBucket_returnsStorageUnavailable() {
        // given
        String key = "group-covers/12/123e4567-e89b-12d3-a456-426614174000";
        when(uploadRepository.findByObjectKeyAndMemberId(key, 12L))
                .thenReturn(Optional.of(issuance(key)));
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchBucketException.builder().statusCode(404).message("missing bucket").build());

        // when
        StorageOperationException exception = assertThrows(StorageOperationException.class,
                () -> validator.verifyOwnedAndExists(12L, key));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("STORAGE503_1");
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

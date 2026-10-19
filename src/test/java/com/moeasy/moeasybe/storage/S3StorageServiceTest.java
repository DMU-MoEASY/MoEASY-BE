package com.moeasy.moeasybe.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.moeasy.moeasybe.global.config.AwsS3Properties;
import com.moeasy.moeasybe.storage.code.StorageErrorCode;
import com.moeasy.moeasybe.storage.exception.StorageValidationException;
import com.moeasy.moeasybe.storage.exception.StorageOperationException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    @Captor
    private ArgumentCaptor<PutObjectPresignRequest> putRequestCaptor;

    @Captor
    private ArgumentCaptor<GetObjectPresignRequest> getRequestCaptor;

    @Captor
    private ArgumentCaptor<DeleteObjectRequest> deleteRequestCaptor;

    private S3StorageService service;

    @BeforeEach
    void setUp() {
        AwsS3Properties properties = new AwsS3Properties();
        properties.setRegion("ap-northeast-2");

        AwsS3Properties.S3 s3 = new AwsS3Properties.S3();
        s3.setBucket("test-bucket");
        s3.setUploadPresignedUrlExpiration(Duration.ofMinutes(5));
        s3.setDownloadPresignedUrlExpiration(Duration.ofMinutes(15));
        properties.setS3(s3);

        service = new S3StorageService(s3Client, s3Presigner, properties);
    }

    @Test
    void createsPresignedPutUrlWithContentTypeAndExpiration() throws Exception {
        Instant expiration = Instant.parse("2026-01-01T00:05:00Z");
        URL expectedUrl = URI.create("https://example.com/upload").toURL();
        when(presignedPutObjectRequest.url()).thenReturn(expectedUrl);
        when(presignedPutObjectRequest.expiration()).thenReturn(expiration);
        when(presignedPutObjectRequest.signedHeaders()).thenReturn(Map.of(
                "host", List.of("example.com"),
                "content-type", List.of("image/jpeg")
        ));
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPutObjectRequest);

        S3StorageService.PresignedUpload upload = service.createPresignedPutUrl("media/test.jpg", "image/jpeg");

        assertEquals(expectedUrl.toURI(), upload.url());
        assertEquals(expiration, upload.expiresAt());
        assertEquals(Map.of("Content-Type", "image/jpeg"), upload.requiredHeaders());
        assertThat(upload.requiredHeaders()).doesNotContainKey("Host");
        verify(s3Presigner).presignPutObject(putRequestCaptor.capture());
        assertEquals(Duration.ofMinutes(5), putRequestCaptor.getValue().signatureDuration());
        assertEquals("test-bucket", putRequestCaptor.getValue().putObjectRequest().bucket());
        assertEquals("media/test.jpg", putRequestCaptor.getValue().putObjectRequest().key());
        assertEquals("image/jpeg", putRequestCaptor.getValue().putObjectRequest().contentType());
    }

    @Test
    void createsPresignedGetUrlWithDownloadExpiration() throws Exception {
        URL expectedUrl = URI.create("https://example.com/download").toURL();
        when(presignedGetObjectRequest.url()).thenReturn(expectedUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedGetObjectRequest);

        URI actualUrl = service.createPresignedGetUrl("media/test.jpg");

        assertEquals(expectedUrl.toURI(), actualUrl);
        verify(s3Presigner).presignGetObject(getRequestCaptor.capture());
        assertEquals(Duration.ofMinutes(15), getRequestCaptor.getValue().signatureDuration());
        assertEquals("test-bucket", getRequestCaptor.getValue().getObjectRequest().bucket());
        assertEquals("media/test.jpg", getRequestCaptor.getValue().getObjectRequest().key());
    }

    @Test
    void deletesObjectByBucketAndKey() {
        service.deleteObject("media/test.jpg");

        verify(s3Client).deleteObject(deleteRequestCaptor.capture());
        assertEquals("test-bucket", deleteRequestCaptor.getValue().bucket());
        assertEquals("media/test.jpg", deleteRequestCaptor.getValue().key());
    }

    @Test
    void returnsObjectMetadataWhenHeadSucceeds() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentType("image/jpeg").build());

        var metadata = service.getObjectMetadata("group-covers/1/key");

        assertThat(metadata).contains(new S3StorageService.ObjectMetadata("image/jpeg"));
    }

    @Test
    void confirmsMissingObjectAfterHeadFailureAndCompleteListing() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("missing").build());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());

        assertThat(service.getObjectMetadata("group-covers/1/key")).isEmpty();
    }

    @Test
    void mapsAmbiguousHeadFailureToStorageUnavailable() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("denied").build());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("list denied").build());

        var exception = assertThrows(StorageOperationException.class,
                () -> service.getObjectMetadata("group-covers/1/key"));

        assertThat(exception.getCode().getCode()).isEqualTo("STORAGE503_1");
    }

    @Test
    void rejectsNullObjectKey() {
        assertStorageValidationFailure(
                () -> service.createPresignedPutUrl(null, "image/jpeg"),
                StorageErrorCode.INVALID_OBJECT_KEY);
    }

    @Test
    void rejectsBlankObjectKey() {
        assertStorageValidationFailure(
                () -> service.createPresignedPutUrl("   ", "image/jpeg"),
                StorageErrorCode.INVALID_OBJECT_KEY);
    }

    @Test
    void rejectsNullContentType() {
        assertStorageValidationFailure(
                () -> service.createPresignedPutUrl("media/test.jpg", null),
                StorageErrorCode.INVALID_CONTENT_TYPE);
    }

    @Test
    void rejectsBlankContentType() {
        assertStorageValidationFailure(
                () -> service.createPresignedPutUrl("media/test.jpg", "   "),
                StorageErrorCode.INVALID_CONTENT_TYPE);
    }

    private void assertStorageValidationFailure(Executable executable, StorageErrorCode expectedCode) {
        StorageValidationException exception = assertThrows(
                StorageValidationException.class,
                executable
        );

        assertEquals(expectedCode, exception.getCode());
    }
}

package com.moeasy.moeasybe.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.moeasy.moeasybe.global.config.AwsS3Properties;
import com.moeasy.moeasybe.storage.exception.StorageValidationException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
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
        URL expectedUrl = URI.create("https://example.com/upload").toURL();
        when(presignedPutObjectRequest.url()).thenReturn(expectedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presignedPutObjectRequest);

        URI actualUrl = service.createPresignedPutUrl("media/test.jpg", "image/jpeg");

        assertEquals(expectedUrl.toURI(), actualUrl);
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
    void rejectsNullObjectKey() {
        assertThrows(StorageValidationException.class,
                () -> service.createPresignedPutUrl(null, "image/jpeg"));
    }

    @Test
    void rejectsBlankObjectKey() {
        assertThrows(StorageValidationException.class,
                () -> service.createPresignedPutUrl("   ", "image/jpeg"));
    }

    @Test
    void rejectsNullContentType() {
        assertThrows(StorageValidationException.class,
                () -> service.createPresignedPutUrl("media/test.jpg", null));
    }

    @Test
    void rejectsBlankContentType() {
        assertThrows(StorageValidationException.class,
                () -> service.createPresignedPutUrl("media/test.jpg", "   "));
    }
}

package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.group.service.command.GroupCommandService;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.storage.exception.StorageOperationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class GroupTransactionBoundaryIntegrationTest {
    @Autowired private GroupCommandService commandService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private S3Client s3Client;
    @MockitoBean private GroupCoverImageService coverImageService;
    @MockitoSpyBean private GroupRepository groupRepository;

    private final List<Long> groupIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long groupId : groupIds) {
            jdbcTemplate.update("DELETE FROM group_deletion_history WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM group_member WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM member_group WHERE id = ?", groupId);
        }
        for (Long memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM group_cover_upload WHERE member_id = ?", memberId);
        }
        for (Long categoryId : categoryIds) {
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
        }
        for (Long memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
        }
    }

    @Test
    @DisplayName("생성·수정의 S3 조회는 쓰기 트랜잭션 밖에서 수행하고 DB 변경을 저장한다")
    void createAndUpdate_coverVerifiedOutsideTransaction_persistChanges() throws Exception {
        // given
        Long memberId = insertMember();
        Long categoryId = insertCategory();
        String createKey = issueCover(memberId);
        String updateKey = issueCover(memberId);
        AtomicInteger headCalls = new AtomicInteger();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            headCalls.incrementAndGet();
            return HeadObjectResponse.builder().contentType("image/png").build();
        });

        // when
        Long groupId = commandService.createGroup(createRequest(categoryId, createKey), memberId).groupId();
        groupIds.add(groupId);
        commandService.updateGroup(groupId, updateRequest("{\"name\":\"변경된 모임\",\"coverImageKey\":\""
                + updateKey + "\"}"), memberId);

        // then
        assertThat(headCalls.get()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM member_group WHERE id = ?", String.class, groupId))
                .isEqualTo("변경된 모임");
        assertThat(jdbcTemplate.queryForObject("SELECT cover_image_key FROM member_group WHERE id = ?", String.class, groupId))
                .isEqualTo(updateKey);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id = ? AND member_id = ? AND role = 'OWNER' AND status = 'ACTIVE'",
                Integer.class, groupId, memberId)).isEqualTo(1);
    }

    @Test
    @DisplayName("생성 시 S3 검증 실패는 Group과 멤버십을 저장하지 않는다")
    void createGroup_s3Failure_doesNotWrite() {
        // given
        Long memberId = insertMember();
        Long categoryId = insertCategory();
        String key = issueCover(memberId);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("S3 unavailable").build());

        // when
        StorageOperationException exception = assertThrows(StorageOperationException.class,
                () -> commandService.createGroup(createRequest(categoryId, key), memberId));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("STORAGE503_1");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_group WHERE category_id = ? AND name = '원래 모임'",
                Integer.class, categoryId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_member WHERE member_id = ?", Integer.class,
                memberId)).isZero();
    }

    @Test
    @DisplayName("생성 시 HEAD 403 뒤 목록에서 커버 객체 부재가 확인되면 DB에 저장하지 않는다")
    void createGroup_missingCoverAfterForbiddenHead_doesNotWrite() {
        // given
        Long memberId = insertMember();
        Long categoryId = insertCategory();
        String key = issueCover(memberId);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("access denied").build());
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return ListObjectsV2Response.builder().isTruncated(false).build();
        });

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> commandService.createGroup(createRequest(categoryId, key), memberId));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("COMMON400_1");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member_group WHERE category_id = ? AND name = '원래 모임'",
                Integer.class, categoryId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_member WHERE member_id = ?", Integer.class,
                memberId)).isZero();
    }

    @Test
    @DisplayName("수정 시 S3 검증 실패는 Group을 변경하지 않는다")
    void updateGroup_s3Failure_doesNotWrite() throws Exception {
        // given
        Long memberId = insertMember();
        Long groupId = createGroup(memberId);
        String key = issueCover(memberId);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("S3 unavailable").build());

        // when
        StorageOperationException exception = assertThrows(StorageOperationException.class,
                () -> commandService.updateGroup(groupId,
                        updateRequest("{\"name\":\"변경된 모임\",\"coverImageKey\":\"" + key + "\"}"), memberId));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("STORAGE503_1");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM member_group WHERE id = ?", String.class, groupId))
                .isEqualTo("원래 모임");
        assertThat(jdbcTemplate.queryForObject("SELECT cover_image_key FROM member_group WHERE id = ?", String.class, groupId))
                .isNull();
    }

    @Test
    @DisplayName("OWNER가 아니면 커버 객체를 조회하기 전에 수정을 거부한다")
    void updateGroup_nonOwner_rejectsBeforeS3Validation() throws Exception {
        // given
        Long ownerId = insertMember();
        Long otherId = insertMember();
        Long groupId = createGroup(ownerId);
        String key = issueCover(otherId);

        // when
        GeneralException exception = assertThrows(GeneralException.class,
                () -> commandService.updateGroup(groupId,
                        updateRequest("{\"coverImageKey\":\"" + key + "\"}"), otherId));

        // then
        assertThat(exception.getCode().getCode()).isEqualTo("AUTH403_1");
        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    @DisplayName("S3 검증 중에는 Group 락을 잡지 않으며 폐쇄 후 수정은 거부한다")
    void updateGroup_closeDuringS3Validation_rechecksClosedState() throws Exception {
        // given
        Long memberId = insertMember();
        Long groupId = createGroup(memberId);
        String key = issueCover(memberId);
        CountDownLatch headEntered = new CountDownLatch(1);
        CountDownLatch releaseHead = new CountDownLatch(1);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            headEntered.countDown();
            assertThat(releaseHead.await(10, TimeUnit.SECONDS)).isTrue();
            return HeadObjectResponse.builder().contentType("image/png").build();
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> update = executor.submit(() -> commandService.updateGroup(groupId,
                    updateRequestUnchecked("{\"name\":\"변경된 모임\",\"coverImageKey\":\"" + key + "\"}"), memberId));
            assertThat(headEntered.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            Future<?> close = executor.submit(() -> commandService.closeGroup(groupId,
                    new GroupReqDTO.Close("운영 종료", "원래 모임", false), memberId));
            close.get(5, TimeUnit.SECONDS);
            releaseHead.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> update.get(5, TimeUnit.SECONDS));

            // then
            assertThat(failure.getCause()).isInstanceOf(GroupException.class);
            assertThat(((GroupException) failure.getCause()).getCode().getCode()).isEqualTo("GROUP404_1");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM member_group WHERE id = ?", String.class, groupId))
                    .isEqualTo("CLOSED");
            assertThat(jdbcTemplate.queryForObject("SELECT cover_image_key FROM member_group WHERE id = ?", String.class, groupId))
                    .isNull();
        } finally {
            releaseHead.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("S3 검증 후 OWNER 권한이 사라지면 수정은 거부한다")
    void updateGroup_ownerRevokedDuringS3Validation_rechecksPermission() throws Exception {
        // given
        Long memberId = insertMember();
        Long groupId = createGroup(memberId);
        String key = issueCover(memberId);
        CountDownLatch headEntered = new CountDownLatch(1);
        CountDownLatch releaseHead = new CountDownLatch(1);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            headEntered.countDown();
            assertThat(releaseHead.await(10, TimeUnit.SECONDS)).isTrue();
            return HeadObjectResponse.builder().contentType("image/png").build();
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> update = executor.submit(() -> commandService.updateGroup(groupId,
                    updateRequestUnchecked("{\"name\":\"변경된 모임\",\"coverImageKey\":\"" + key + "\"}"), memberId));
            assertThat(headEntered.await(5, TimeUnit.SECONDS)).isTrue();

            // when
            jdbcTemplate.update("UPDATE group_member SET status = 'LEFT' WHERE group_id = ? AND member_id = ?",
                    groupId, memberId);
            releaseHead.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> update.get(5, TimeUnit.SECONDS));

            // then
            assertThat(failure.getCause()).isInstanceOf(GeneralException.class);
            assertThat(((GeneralException) failure.getCause()).getCode().getCode()).isEqualTo("AUTH403_1");
            assertThat(jdbcTemplate.queryForObject("SELECT name FROM member_group WHERE id = ?", String.class, groupId))
                    .isEqualTo("원래 모임");
        } finally {
            releaseHead.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("동시 수정은 비관적 락으로 직렬화하고 한 요청의 필드를 함께 저장한다")
    void updateGroup_concurrentUpdates_preserveConsistentFields() throws Exception {
        // given
        Long memberId = insertMember();
        Long groupId = createGroup(memberId);
        CountDownLatch firstInsideTransaction = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger viewCalls = new AtomicInteger();
        when(coverImageService.createViewUrl(null)).thenAnswer(invocation -> {
            if (viewCalls.incrementAndGet() == 1) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                firstInsideTransaction.countDown();
                assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> commandService.updateGroup(groupId,
                    updateRequestUnchecked("{\"name\":\"첫 수정\",\"description\":\"첫 설명\"}"), memberId));
            assertThat(firstInsideTransaction.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbcTemplate.queryForObject("SELECT name FROM member_group WHERE id = ?", String.class, groupId))
                    .isEqualTo("원래 모임");
            clearInvocations(groupRepository);

            // when
            Future<?> second = executor.submit(() -> commandService.updateGroup(groupId,
                    updateRequestUnchecked("{\"name\":\"둘째 수정\",\"description\":\"둘째 설명\"}"), memberId));
            verify(groupRepository, org.mockito.Mockito.timeout(5000)).findByIdForUpdate(groupId);
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            // then
            assertThat(jdbcTemplate.queryForObject("SELECT name FROM member_group WHERE id = ?", String.class, groupId))
                    .isEqualTo("둘째 수정");
            assertThat(jdbcTemplate.queryForObject("SELECT description FROM member_group WHERE id = ?", String.class, groupId))
                    .isEqualTo("둘째 설명");
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("수정이 락을 잡은 동안 폐쇄는 대기하고 변경된 이름으로 확인 문구를 재검증한다")
    void closeGroup_duringUpdate_rechecksConfirmationAfterLock() throws Exception {
        // given
        Long memberId = insertMember();
        Long groupId = createGroup(memberId);
        CountDownLatch updateInsideTransaction = new CountDownLatch(1);
        CountDownLatch releaseUpdate = new CountDownLatch(1);
        when(coverImageService.createViewUrl(null)).thenAnswer(invocation -> {
            updateInsideTransaction.countDown();
            assertThat(releaseUpdate.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> update = executor.submit(() -> commandService.updateGroup(groupId,
                    updateRequestUnchecked("{\"name\":\"변경된 모임\"}"), memberId));
            assertThat(updateInsideTransaction.await(5, TimeUnit.SECONDS)).isTrue();
            clearInvocations(groupRepository);

            // when
            Future<?> close = executor.submit(() -> commandService.closeGroup(groupId,
                    new GroupReqDTO.Close("운영 종료", "원래 모임", false), memberId));
            verify(groupRepository, org.mockito.Mockito.timeout(5000)).findByIdForUpdate(groupId);
            assertThat(close.isDone()).isFalse();
            releaseUpdate.countDown();
            update.get(5, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> close.get(5, TimeUnit.SECONDS));

            // then
            assertThat(failure.getCause()).isInstanceOf(GroupException.class);
            assertThat(((GroupException) failure.getCause()).getCode().getCode()).isEqualTo("GROUP400_1");
            assertThat(jdbcTemplate.queryForObject("SELECT name FROM member_group WHERE id = ?", String.class, groupId))
                    .isEqualTo("변경된 모임");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM member_group WHERE id = ?", String.class, groupId))
                    .isEqualTo("ACTIVE");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_deletion_history WHERE group_id = ?",
                    Integer.class, groupId)).isZero();
        } finally {
            releaseUpdate.countDown();
            executor.shutdownNow();
        }
    }

    private Long createGroup(Long memberId) {
        Long groupId = commandService.createGroup(createRequest(insertCategory(), null), memberId).groupId();
        groupIds.add(groupId);
        return groupId;
    }

    private GroupReqDTO.Create createRequest(Long categoryId, String key) {
        return new GroupReqDTO.Create("원래 모임", "설명", categoryId, "11680", "한강공원", "서울",
                null, null, 30, GroupJoinPolicy.APPROVAL, key);
    }

    private GroupReqDTO.Update updateRequest(String json) throws Exception {
        return objectMapper.readValue(json, GroupReqDTO.Update.class);
    }

    private GroupReqDTO.Update updateRequestUnchecked(String json) {
        try {
            return updateRequest(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Long insertMember() {
        String socialId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO member (social_id, social_type, nickname) VALUES (?, 'KAKAO', ?)",
                socialId, "test-" + socialId.substring(0, 8));
        Long memberId = jdbcTemplate.queryForObject("SELECT id FROM member WHERE social_id = ?", Long.class, socialId);
        memberIds.add(memberId);
        return memberId;
    }

    private Long insertCategory() {
        String code = UUID.randomUUID().toString().substring(0, 12);
        jdbcTemplate.update("INSERT INTO category (code, name) VALUES (?, ?)", code, code);
        Long categoryId = jdbcTemplate.queryForObject("SELECT id FROM category WHERE code = ?", Long.class, code);
        categoryIds.add(categoryId);
        return categoryId;
    }

    private String issueCover(Long memberId) {
        String key = "group-covers/" + memberId + "/" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO group_cover_upload (object_key, member_id, content_type) VALUES (?, ?, 'image/png')",
                key, memberId);
        return key;
    }
}

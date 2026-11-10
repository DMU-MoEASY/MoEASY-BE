package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.validate.GroupAccessValidator;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class GroupJoinRequestConcurrencyIntegrationTest extends JoinRequestIntegrationSupport {
    @MockitoSpyBean private GroupAccessValidator accessValidator;

    @Test
    @DisplayName("동시 중복 신청은 하나만 생성하고 후속 신청은 409를 반환한다")
    void create_concurrentDuplicate_onePending() throws Exception {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);

        // when
        var outcomes = runOrdered(() -> apply(group, applicant), () -> apply(group, applicant));

        // then
        assertThat(outcomes).containsExactly("OK", "GROUP409_3");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_join_request WHERE group_id = ?",
                Integer.class, group)).isEqualTo(1);
    }

    @Test
    @DisplayName("마지막 한 자리의 동시 승인은 한 명만 가입시킨다")
    void approve_lastSlot_onlyOneMembershipCreated() throws Exception {
        // given
        Long owner = member();
        Long group = group(owner, 2);
        Long first = apply(group, member());
        Long second = apply(group, member());

        // when
        var outcomes = runOrdered(() -> commands.approve(group, first, owner),
                () -> commands.approve(group, second, owner));

        // then
        assertThat(outcomes).containsExactly("OK", "GROUP409_4");
        assertThat(requestStatus(first)).isEqualTo("APPROVED");
        assertThat(requestStatus(second)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id = ? AND status = 'ACTIVE' AND deleted_at IS NULL",
                Integer.class, group)).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"approve,reject", "reject,approve", "approve,cancel", "cancel,approve", "reject,cancel", "cancel,reject",
            "close,create", "create,close", "close,approve", "approve,close", "close,reject", "reject,close", "close,cancel", "cancel,close"})
    @DisplayName("가입 처리와 그룹 폐쇄는 동일 그룹 락으로 직렬화한다")
    void process_competingTransitions_firstCommittedStateWins(String first, String second) throws Exception {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 5);
        Long request = first.equals("create") || second.equals("create") ? null : apply(group, applicant);

        // when
        var outcomes = runOrdered(() -> act(first, group, request, owner, applicant),
                () -> act(second, group, request, owner, applicant));

        // then
        String expectedSecond = second.equals("close") ? "OK" : first.equals("close") ? "GROUP404_1"
                : second.equals("cancel") ? "GROUP409_7" : "GROUP409_6";
        assertThat(outcomes).containsExactly("OK", expectedSecond);
        if (request != null) {
            String status = first.equals("close") ? "PENDING" : switch (first) {
                case "approve" -> "APPROVED";
                case "reject" -> "REJECTED";
                default -> "CANCELLED";
            };
            assertThat(requestStatus(request)).isEqualTo(status);
        }
        if (first.equals("close") || second.equals("close")) {
            assertThat(jdbc.queryForObject("SELECT status FROM member_group WHERE id = ?", String.class, group))
                    .isEqualTo("CLOSED");
        }
    }

    private void act(String action, Long group, Long request, Long owner, Long applicant) {
        switch (action) {
            case "create" -> apply(group, applicant);
            case "approve" -> commands.approve(group, request, owner);
            case "reject" -> commands.reject(group, request, new GroupJoinRequestReqDTO.Reject(null), owner);
            case "cancel" -> commands.cancel(group, applicant);
            case "close" -> groups.closeGroup(group, new GroupReqDTO.Close("종료", "가입 테스트", false), owner);
            default -> throw new IllegalArgumentException(action);
        }
    }

    private List<String> runOrdered(Runnable first, Runnable second) throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondAttempt = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = calls.incrementAndGet();
            invocation.callRealMethod();
            if (call == 1) {
                firstLocked.countDown();
                assertThat(releaseFirst.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(accessValidator).requireNoDeletionHistory(anyLong());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(outcome(first));
            assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var secondResult = executor.submit(outcome(() -> {
                secondAttempt.countDown();
                second.run();
            }));
            assertThat(secondAttempt.await(10, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> secondResult.get(150, TimeUnit.MILLISECONDS));
            assertThat(calls.get()).isEqualTo(1);
            releaseFirst.countDown();
            return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Callable<String> outcome(Runnable action) {
        return () -> {
            try {
                action.run();
                return "OK";
            } catch (GeneralException ex) {
                return ex.getCode().getCode();
            }
        };
    }
}

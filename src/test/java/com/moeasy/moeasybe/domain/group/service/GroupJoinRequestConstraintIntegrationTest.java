package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.moeasy.moeasybe.domain.group.converter.GroupJoinRequestConverter;
import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinRequestStatus;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.repository.GroupJoinRequestRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.global.apiPayload.handler.GeneralExceptionAdvice;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class GroupJoinRequestConstraintIntegrationTest extends JoinRequestIntegrationSupport {
    @MockitoSpyBean private GroupJoinRequestRepository requests;
    @Autowired private GroupRepository groupRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("실제 MySQL 대기 신청 제약 예외를 포착해 중복 신청 409로 변환한다")
    void create_realUniqueViolation_translatesAndRollsBack() {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);
        Long first = apply(group, applicant);
        doReturn(false).when(requests).existsByGroupIdAndMemberIdAndStatus(group, applicant, GroupJoinRequestStatus.PENDING);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        var databaseFailure = assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(status -> {
            var duplicate = GroupJoinRequestConverter.toEntity(groupRepository.findById(group).orElseThrow(),
                    memberRepository.findById(applicant).orElseThrow(), new GroupJoinRequestReqDTO.Create("중복"));
            requests.saveAndFlush(duplicate);
        }));

        // when
        var error = assertThrows(GroupException.class, () -> apply(group, applicant));
        var response = new GeneralExceptionAdvice().handleGeneralException(error);

        // then
        assertAll(
                () -> assertThat(databaseFailure).isNotNull(),
                () -> assertThat(constraintName(databaseFailure)).endsWith("uq_group_join_request_pending"),
                () -> assertThat(error.getCode().getCode()).isEqualTo("GROUP409_3"),
                () -> assertThat(response.getStatusCode().value()).isEqualTo(409),
                () -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_join_request WHERE group_id = ?",
                        Integer.class, group)).isEqualTo(1),
                () -> assertThat(requestStatus(first)).isEqualTo("PENDING"));
    }

    @Test
    @DisplayName("실제 FK 무결성 오류는 중복 신청으로 오인하지 않는다")
    void create_realForeignKeyViolation_doesNotTranslate() {
        // given
        Long applicant = member();
        Long group = group(member(), 3);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        var databaseError = assertThrows(DataIntegrityViolationException.class, () -> tx.executeWithoutResult(status -> {
            var invalid = GroupJoinRequestConverter.toEntity(groupRepository.findById(group).orElseThrow(),
                    entityManager.getReference(Member.class, Long.MAX_VALUE), new GroupJoinRequestReqDTO.Create("소개"));
            requests.saveAndFlush(invalid);
        }));
        doThrow(databaseError).when(requests).saveAndFlush(any());

        // when
        var propagated = assertThrows(DataIntegrityViolationException.class, () -> apply(group, applicant));

        // then
        assertThat(constraintName(databaseError)).endsWith("fk_group_join_request_member");
        assertThat(propagated).isSameAs(databaseError);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_join_request WHERE group_id = ?",
                Integer.class, group)).isZero();
    }

    @Test
    @DisplayName("대기 신청 이외의 실제 유일 제약 오류도 중복 신청으로 오인하지 않는다")
    void create_otherUniqueViolation_doesNotTranslate() {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);
        var databaseError = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO group_member (group_id, member_id, role, status) VALUES (?, ?, 'MEMBER', 'ACTIVE')",
                        group, owner));
        doThrow(databaseError).when(requests).saveAndFlush(any());

        // when
        var propagated = assertThrows(DataIntegrityViolationException.class, () -> apply(group, applicant));

        // then
        assertAll(
                () -> assertThat(databaseError.getMostSpecificCause()).isInstanceOf(SQLException.class),
                () -> assertThat(((SQLException) databaseError.getMostSpecificCause()).getErrorCode()).isEqualTo(1062));
        assertThat(propagated).isSameAs(databaseError);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_join_request WHERE group_id = ?",
                Integer.class, group)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("승인 도중 실패하면 신규·복구 멤버십과 신청 상태가 함께 롤백된다")
    void approve_membershipSaveFails_rollsBackAll(boolean rejoin) {
        // given
        Long owner = member();
        Long applicant = member();
        Long group = group(owner, 3);
        Long existing = rejoin ? membership(group, applicant, "MANAGER", "LEFT", true) : null;
        Long request = apply(group, applicant);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // when
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            commands.approve(group, request, owner);
            requests.flush();
            throw new IllegalStateException("승인 저장 후 트랜잭션 실패 주입");
        }));

        // then
        assertThat(requestStatus(request)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT processed_at FROM group_join_request WHERE id = ?",
                java.time.LocalDateTime.class, request)).isNull();
        if (rejoin) {
            var row = jdbc.queryForMap("SELECT * FROM group_member WHERE id = ?", existing);
            assertThat(row.get("role")).isEqualTo("MANAGER");
            assertThat(row.get("status")).isEqualTo("LEFT");
            assertThat(row.get("deleted_at")).isNotNull();
        } else {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_member WHERE group_id = ? AND member_id = ?",
                    Integer.class, group, applicant)).isZero();
        }
    }

    private String constraintName(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) return violation.getConstraintName();
        }
        throw new AssertionError("Hibernate 제약 예외가 필요합니다.");
    }
}

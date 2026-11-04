package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import com.moeasy.moeasybe.domain.group.entity.*;
import com.moeasy.moeasybe.domain.group.enums.*;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.service.command.GroupWriteService;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.schedule.dto.request.ScheduleReqDTO;
import com.moeasy.moeasybe.domain.schedule.repository.ScheduleRepository;
import com.moeasy.moeasybe.domain.schedule.service.command.ScheduleCommandService;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class ScheduleConcurrencyIntegrationTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ScheduleCommandService service;
    @Autowired GroupWriteService groups;
    @Autowired ScheduleRepository repository;
    @Autowired ObjectMapper mapper;
    private Long groupId;
    private Long memberId;
    private Long scheduleId;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            Category category = em.createQuery("select c from Category c where c.code='RUNNING'", Category.class)
                    .getSingleResult();
            Group g = Group.builder().category(category).name("경합").description("설명").regionCode("11680")
                    .placeName("장소").address("주소").maxMembers(10).joinPolicy(GroupJoinPolicy.APPROVAL).build();
            em.persist(g);
            Member m = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString())
                    .nickname("경합" + UUID.randomUUID()).build();
            em.persist(m);
            em.persist(GroupMember.builder().group(g).member(m).role(GroupMemberRole.OWNER)
                    .status(GroupMemberStatus.ACTIVE).joinedAt(LocalDateTime.now()).build());
            groupId = g.getId();
            memberId = m.getId();
        });
        scheduleId = service.createSchedule(groupId, new ScheduleReqDTO.Create("제목", "설명",
                LocalDateTime.of(2099, 1, 1, 12, 0), LocalDateTime.of(2099, 1, 1, 13, 0),
                "장소", "주소", null, null, null, null), memberId).scheduleId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from schedule where group_id=?", groupId);
        jdbc.update("delete from group_deletion_history where group_id=?", groupId);
        jdbc.update("delete from group_member where group_id=?", groupId);
        jdbc.update("delete from member_group where id=?", groupId);
        jdbc.update("delete from member where id=?", memberId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCEL", "DELETE", "CLOSE"})
    @DisplayName("수정과 취소·삭제·모임 폐쇄가 경합해도 최종 상태가 보존된다")
    void update_competingTerminalChange_preservesState(String action) throws Exception {
        // given
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> update = () -> race(ready, start, () -> service.updateSchedule(scheduleId,
                    mapper.readValue("{\"title\":\"수정\"}", ScheduleReqDTO.Update.class), memberId));
            Callable<Integer> terminal = () -> race(ready, start, () -> {
                switch (action) {
                    case "CANCEL" -> service.cancelSchedule(scheduleId, memberId);
                    case "DELETE" -> service.deleteSchedule(scheduleId, memberId);
                    case "CLOSE" -> groups.closeGroup(groupId, new GroupReqDTO.Close("사유", "경합", true), memberId);
                    default -> throw new IllegalArgumentException();
                }
            });
            // when
            Future<Integer> a = pool.submit(update);
            Future<Integer> b = pool.submit(terminal);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int updateStatus = a.get(15, TimeUnit.SECONDS);
            int terminalStatus = b.get(15, TimeUnit.SECONDS);
            // then
            assertAll(() -> assertEquals(200, terminalStatus),
                    () -> assertTrue(updateStatus == 200 || updateStatus == (action.equals("CANCEL") ? 409 : 404)));
            if (action.equals("CANCEL")) {
                assertEquals("CANCELLED", jdbc.queryForObject("select status from schedule where id=?", String.class, scheduleId));
            } else if (action.equals("DELETE")) {
                assertFalse(repository.existsByIdAndGroupIdAndDeletedAtIsNull(scheduleId, groupId));
            } else {
                assertEquals("CLOSED", jdbc.queryForObject("select status from member_group where id=?", String.class, groupId));
            }
        }
    }

    private int race(CountDownLatch ready, CountDownLatch start, Runnable work) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("시작 신호 시간 초과");
        try {
            work.run();
            return 200;
        } catch (GeneralException exception) {
            return exception.getCode().getStatus().value();
        }
    }
}

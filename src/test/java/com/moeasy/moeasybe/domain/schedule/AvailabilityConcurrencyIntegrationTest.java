package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import com.moeasy.moeasybe.domain.group.entity.*;
import com.moeasy.moeasybe.domain.group.enums.*;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.schedule.dto.request.*;
import com.moeasy.moeasybe.domain.schedule.service.command.*;
import com.moeasy.moeasybe.domain.schedule.service.query.AvailabilityQueryService;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AvailabilityConcurrencyIntegrationTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ScheduleCommandService schedules;
    @Autowired AvailabilityCommandService command;
    @Autowired AvailabilityQueryService query;
    private Long groupId, memberId, scheduleId;
    private static final LocalDateTime SLOT = LocalDateTime.of(2099,1,10,9,0);
    @BeforeEach void setUp() {
        tx.executeWithoutResult(status -> {
            Category c = em.createQuery("select c from Category c where c.code='RUNNING'", Category.class).getSingleResult();
            Group g = Group.builder().category(c).name("가용 경합").description("설명").regionCode("11680")
                    .placeName("장소").address("주소").maxMembers(10).joinPolicy(GroupJoinPolicy.APPROVAL).build(); em.persist(g);
            Member m = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).nickname("경합"+UUID.randomUUID()).build(); em.persist(m);
            em.persist(GroupMember.builder().group(g).member(m).role(GroupMemberRole.OWNER).status(GroupMemberStatus.ACTIVE)
                    .joinedAt(LocalDateTime.of(2026,1,1,0,0)).build()); groupId=g.getId(); memberId=m.getId();
        });
        scheduleId = schedules.createSchedule(groupId, new ScheduleReqDTO.Create("제목","설명",SLOT.plusDays(10),SLOT.plusDays(10).plusHours(1),
                "장소","주소",null,null,null,null), memberId).scheduleId();
    }
    @AfterEach void cleanUp() {
        jdbc.update("delete v from availability_vote v join availability_slot s on s.id=v.availability_slot_id join availability_poll p on p.id=s.availability_poll_id where p.schedule_id=?",scheduleId);
        jdbc.update("delete r from availability_response r join availability_poll p on p.id=r.availability_poll_id where p.schedule_id=?",scheduleId);
        jdbc.update("delete s from availability_slot s join availability_poll p on p.id=s.availability_poll_id where p.schedule_id=?",scheduleId);
        jdbc.update("delete from availability_poll where schedule_id=?",scheduleId);
        jdbc.update("delete from schedule where id=?",scheduleId); jdbc.update("delete from group_member where group_id=?",groupId);
        jdbc.update("delete from member_group where id=?",groupId); jdbc.update("delete from member where id=?",memberId);
    }
    @Test @DisplayName("동시 생성은 하나만 성공하고 나머지는 409이다")
    void create_concurrentRequests_enforcesSinglePoll() throws Exception {
        // given
        Runnable create = () -> command.create(scheduleId, config(), memberId);
        // when
        var result = race(create,create);
        // then
        assertEquals(List.of(200,409),result.stream().sorted().toList());
        assertEquals(1,jdbc.queryForObject("select count(*) from availability_poll where schedule_id=?",Integer.class,scheduleId));
    }
    @Test @DisplayName("동시 응답 교체는 선택을 섞지 않고 제출 기록을 하나만 만든다")
    void submit_concurrentReplacements_keepsOneAtomicSelection() throws Exception {
        // given
        command.create(scheduleId,config(),memberId);
        // when
        var result = race(() -> command.submit(scheduleId,new AvailabilityReqDTO.Submit(List.of(SLOT)),memberId),
                () -> command.submit(scheduleId,new AvailabilityReqDTO.Submit(List.of(SLOT.plusMinutes(30))),memberId));
        var me = query.getMe(scheduleId,memberId);
        // then
        assertAll(() -> assertEquals(List.of(200,200),result), () -> assertEquals(1,me.slots().size()),
                () -> assertTrue(me.slots().equals(List.of(SLOT)) || me.slots().equals(List.of(SLOT.plusMinutes(30)))));
    }
    @Test @DisplayName("같은 확정 요청의 동시 재전송은 모두 성공한다")
    void confirm_concurrentSameRequest_isIdempotent() throws Exception {
        // given
        command.create(scheduleId,config(),memberId);
        command.submit(scheduleId,new AvailabilityReqDTO.Submit(List.of(SLOT,SLOT.plusMinutes(30))),memberId);
        var request = new AvailabilityReqDTO.Confirm(SLOT,query.getPoll(scheduleId,memberId).token());
        // when
        var result = race(() -> command.confirm(scheduleId,request,memberId), () -> command.confirm(scheduleId,request,memberId));
        // then
        assertAll(() -> assertEquals(List.of(200,200),result), () -> assertTrue(query.getPoll(scheduleId,memberId).confirmed()));
    }
    @Test @DisplayName("확정과 철회 경합은 오래된 추천 확정이나 확정 후 응답 변경을 허용하지 않는다")
    void confirm_competingWithdrawal_rejectsOneStaleOperation() throws Exception {
        // given
        command.create(scheduleId,config(),memberId);
        command.submit(scheduleId,new AvailabilityReqDTO.Submit(List.of(SLOT,SLOT.plusMinutes(30))),memberId);
        var request = new AvailabilityReqDTO.Confirm(SLOT,query.getPoll(scheduleId,memberId).token());
        // when
        var result = race(() -> command.confirm(scheduleId,request,memberId), () -> command.withdraw(scheduleId,memberId));
        var detail = query.getAvailability(scheduleId,memberId);
        // then
        assertAll(() -> assertEquals(List.of(200,409),result.stream().sorted().toList()),
                () -> assertEquals(detail.poll().confirmed(), detail.me().responded()));
    }
    @Test @DisplayName("마감 작업과 재개방 경합은 최신 미래 마감을 유지한다")
    void reopen_competingClose_keepsFutureDeadlineOpen() throws Exception {
        // given
        command.create(scheduleId,config(),memberId);
        jdbc.update("update availability_poll set vote_deadline_at='2020-01-01' where schedule_id=?",scheduleId);
        // when
        var result = race(() -> command.closeDue(scheduleId), () -> command.configure(scheduleId,config(),memberId));
        // then
        assertAll(() -> assertEquals(List.of(200,200),result), () -> assertEquals("OPEN",query.getPoll(scheduleId,memberId).status().name()));
    }
    @Test @DisplayName("삭제와 응답 저장 경합에서 자식 행은 남지 않는다")
    void delete_competingSubmit_keepsReferentialIntegrity() throws Exception {
        // given
        command.create(scheduleId,config(),memberId); Long pollId=query.getPoll(scheduleId,memberId).pollId();
        // when
        var result=race(() -> command.delete(scheduleId,memberId), () -> command.submit(scheduleId,new AvailabilityReqDTO.Submit(List.of(SLOT)),memberId));
        // then
        assertAll(() -> assertEquals(200,result.getFirst()), () -> assertTrue(Set.of(200,404).contains(result.getLast())),
                () -> assertEquals(0,jdbc.queryForObject("select count(*) from availability_slot where availability_poll_id=?",Integer.class,pollId)),
                () -> assertEquals(0,jdbc.queryForObject("select count(*) from availability_response where availability_poll_id=?",Integer.class,pollId)));
    }
    private AvailabilityReqDTO.Configure config() { return new AvailabilityReqDTO.Configure(SLOT.toLocalDate(),3,9,12,SLOT.minusDays(1)); }
    private List<Integer> race(Runnable a,Runnable b) throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try (ExecutorService pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> run(ready,start,a)); var second=pool.submit(() -> run(ready,start,b));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            return List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
        }
    }
    private int run(CountDownLatch ready,CountDownLatch start,Runnable work) throws Exception {
        ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("시작 시간 초과");
        try { work.run(); return 200; } catch(GeneralException ex) { return ex.getCode().getStatus().value(); }
    }
}

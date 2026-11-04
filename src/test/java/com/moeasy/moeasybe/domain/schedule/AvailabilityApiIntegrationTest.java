package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moeasy.moeasybe.domain.group.entity.*;
import com.moeasy.moeasybe.domain.group.enums.*;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.schedule.dto.request.*;
import com.moeasy.moeasybe.domain.schedule.dto.response.AvailabilityResDTO;
import com.moeasy.moeasybe.domain.schedule.entity.*;
import com.moeasy.moeasybe.domain.schedule.exception.AvailabilityException;
import com.moeasy.moeasybe.domain.schedule.exception.code.AvailabilityErrorCode;
import com.moeasy.moeasybe.domain.schedule.service.command.*;
import com.moeasy.moeasybe.domain.schedule.service.query.AvailabilityQueryService;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class AvailabilityApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider jwt;
    @Autowired ScheduleCommandService schedules;
    @Autowired AvailabilityCommandService command;
    @Autowired AvailabilityQueryService query;
    @MockitoBean(name = "clock") Clock clock;
    private Group group;
    private Long owner, manager, member, scheduleId;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 20, 12, 0);
    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 11, 10, 9, 0);
    private static final String CONFIG = """
            {"startDate":"2026-11-10","dayCount":3,"startHour":9,"endHour":12,"voteDeadlineAt":"2026-11-09T12:00:00"}
            """;
    @BeforeEach void setUp() {
        setClock(NOW);
        Category category = em.createQuery("select c from Category c where c.code='RUNNING'", Category.class).getSingleResult();
        group = Group.builder().category(category).name("가용시간 테스트").description("설명").regionCode("11680")
                .placeName("장소").address("주소").maxMembers(20).joinPolicy(GroupJoinPolicy.APPROVAL).build();
        em.persist(group);
        owner = membership(GroupMemberRole.OWNER); manager = membership(GroupMemberRole.MANAGER); member = membership(GroupMemberRole.MEMBER);
        scheduleId = schedules.createSchedule(group.getId(), new ScheduleReqDTO.Create("모임", "설명", START,
                START.plusMinutes(45), "장소", "주소", null, null, null, null), owner).scheduleId();
    }
    @Test @DisplayName("9개 API가 쿠키·CSRF 인증으로 동작하고 빈 응답을 미응답과 구분한다")
    void lifecycle_httpContracts_preservesEmptyResponsesAndOriginalSchedule() throws Exception {
        // given
        var creation = post(path("availability-poll")).contentType(MediaType.APPLICATION_JSON).content(CONFIG);
        // when
        var result = mvc.perform(auth(creation, owner));
        // then
        result.andExpect(status().isCreated()).andExpect(jsonPath("$.result.status").value("OPEN"));
        mvc.perform(auth(get(path("availability-poll")), member)).andExpect(status().isOk());
        mvc.perform(auth(put(path("availability/me")).contentType(MediaType.APPLICATION_JSON).content("{\"slots\":[]}"), member))
                .andExpect(status().isOk());
        mvc.perform(auth(get(path("availability/me")), member)).andExpect(jsonPath("$.result.responded").value(true));
        mvc.perform(auth(get(path("availability")), member)).andExpect(jsonPath("$.result.respondedCount").value(1))
                .andExpect(jsonPath("$.result.pendingCount").value(2)).andExpect(jsonPath("$.result.slots.length()").value(18));
        command.submit(scheduleId, new AvailabilityReqDTO.Submit(List.of(SLOT, SLOT.plusMinutes(30))), member);
        var recommendation = query.getAvailability(scheduleId, owner);
        String confirm = mapper.writeValueAsString(new AvailabilityReqDTO.Confirm(SLOT, recommendation.poll().token()));
        mvc.perform(auth(post(path("availability-poll/confirm")).contentType(MediaType.APPLICATION_JSON).content(confirm), manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("CONFIRMED"));
        mvc.perform(auth(post(path("availability-poll/confirm")).contentType(MediaType.APPLICATION_JSON).content(confirm), manager))
                .andExpect(status().isOk());
        mvc.perform(auth(patch(path("availability-poll")).contentType(MediaType.APPLICATION_JSON).content(CONFIG), manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.confirmed").value(false));
        assertEquals(START, em.find(Schedule.class, scheduleId).getStartsAt());
        mvc.perform(auth(delete(path("availability/me")), member)).andExpect(status().isOk());
        mvc.perform(auth(get(path("availability/me")), member)).andExpect(jsonPath("$.result.responded").value(false));
        mvc.perform(auth(delete(path("availability-poll")), owner)).andExpect(status().isOk());
        mvc.perform(auth(get(path("availability-poll")), owner)).andExpect(status().isNotFound());
        assertAll(() -> assertEquals(1, jdbc.queryForObject("select count(*) from schedule where id=?", Integer.class, scheduleId)),
                () -> assertEquals(0, jdbc.queryForObject("select count(*) from availability_poll where schedule_id=?", Integer.class, scheduleId)));
        createPoll();
    }
    @Test @DisplayName("동일 PUT은 응답·선택 수를 증가시키지 않으며 철회는 미응답으로 바꾼다")
    void submit_repeatAndWithdraw_isIdempotent() {
        // given
        createPoll();
        var request = new AvailabilityReqDTO.Submit(List.of(SLOT));
        // when
        command.submit(scheduleId, request, member); command.submit(scheduleId, request, member);
        var detail = query.getAvailability(scheduleId, member);
        // then
        assertAll(() -> assertEquals(1, detail.respondedCount()), () -> assertEquals(1, detail.me().slots().size()),
                () -> assertEquals(1, detail.slots().getFirst().availableCount()));
        command.withdraw(scheduleId, member); command.withdraw(scheduleId, member);
        assertFalse(query.getMe(scheduleId, member).responded());
    }
    @Test @DisplayName("후보 변경 시 겹치는 선택과 빈 응답 기록은 보존한다")
    void configure_afterResponse_preservesCompletionAndIntersection() {
        // given
        createPoll(); command.submit(scheduleId, new AvailabilityReqDTO.Submit(List.of(SLOT, SLOT.plusHours(2))), member);
        // when
        command.configure(scheduleId, config(10, 14), manager);
        var detail = query.getAvailability(scheduleId, member);
        // then
        assertAll(() -> assertTrue(detail.me().responded()), () -> assertEquals(List.of(SLOT.plusHours(2)), detail.me().slots()),
                () -> assertEquals(1, detail.respondedCount()));
        command.configure(scheduleId, config(12, 14), manager);
        assertAll(() -> assertTrue(query.getMe(scheduleId, member).responded()), () -> assertTrue(query.getMe(scheduleId, member).slots().isEmpty()));
    }
    @Test @DisplayName("탈퇴자는 집계에서 제외하고 재가입자는 다시 제출해야 한다")
    void roster_leaveAndRejoin_requiresResubmission() {
        // given
        createPoll(); command.submit(scheduleId, new AvailabilityReqDTO.Submit(List.of(SLOT, SLOT.plusMinutes(30))), member);
        String token = query.getPoll(scheduleId, owner).token();
        // when
        em.flush(); jdbc.update("update group_member set status='LEFT' where member_id=?", member); em.clear();
        var left = query.getAvailability(scheduleId, owner);
        jdbc.update("update group_member set status='ACTIVE', joined_at=? where member_id=?", NOW.plusMinutes(1), member); em.clear();
        var rejoined = query.getAvailability(scheduleId, member);
        // then
        assertAll(() -> assertEquals(2, left.targetCount()), () -> assertEquals(0, left.respondedCount()),
                () -> assertTrue(left.recommendations().isEmpty()), () -> assertFalse(rejoined.me().responded()),
                () -> assertTrue(rejoined.me().slots().isEmpty()), () -> assertNotEquals(token, rejoined.poll().token()));
        command.submit(scheduleId, new AvailabilityReqDTO.Submit(List.of()), member);
        assertTrue(query.getMe(scheduleId, member).responded());
    }
    @Test @DisplayName("구성원과 일정 길이 변경으로 오래된 추천은 409가 된다")
    void confirm_changedScheduleOrRoster_rejectsStaleToken() {
        // given
        createPoll(); command.submit(scheduleId, new AvailabilityReqDTO.Submit(List.of(SLOT, SLOT.plusMinutes(30))), member);
        String token = query.getPoll(scheduleId, owner).token();
        // when
        em.find(Schedule.class, scheduleId).reschedule(START, START.plusMinutes(30));
        AvailabilityException ex = assertThrows(AvailabilityException.class,
                () -> command.confirm(scheduleId, new AvailabilityReqDTO.Confirm(SLOT, token), owner));
        // then
        assertEquals(AvailabilityErrorCode.STALE_RESULT, ex.getCode());
    }
    @Test @DisplayName("확정 이후 별도 시간 수정은 복원을 거부하며 A→B→A도 감지한다")
    void restore_separateScheduleEdit_rejectsConflict() {
        // given
        confirm();
        Schedule s = em.find(Schedule.class, scheduleId);
        var start = s.getStartsAt(); var end = s.getEndsAt();
        s.reschedule(start.plusHours(1), end.plusHours(1)); s.reschedule(start, end);
        // when
        AvailabilityException ex = assertThrows(AvailabilityException.class, () -> command.configure(scheduleId, config(9, 12), owner));
        // then
        assertAll(() -> assertEquals(AvailabilityErrorCode.RESTORE_CONFLICT, ex.getCode()),
                () -> assertTrue(query.getPoll(scheduleId, owner).confirmed()));
    }
    @Test @DisplayName("원래 시간이 과거이면 확정을 유지하고 복원을 거부한다")
    void restore_pastOriginalStart_rejectsWithoutMutation() {
        // given: original Nov 6, confirmed Nov 10, now advances to Nov 7.
        em.find(Schedule.class, scheduleId).reschedule(NOW.plusDays(2), NOW.plusDays(2).plusMinutes(45));
        confirm(); setClock(NOW.plusDays(3));
        // when
        AvailabilityException ex = assertThrows(AvailabilityException.class, () -> command.configure(scheduleId, config(9, 12), owner));
        // then
        assertAll(() -> assertEquals(AvailabilityErrorCode.RESTORE_CONFLICT, ex.getCode()),
                () -> assertEquals(SLOT, em.find(Schedule.class, scheduleId).getStartsAt()),
                () -> assertTrue(query.getPoll(scheduleId, owner).confirmed()));
    }
    @Test @DisplayName("마감 시각부터 응답을 차단하고 CLOSED에서도 확정할 수 있다")
    void deadline_boundary_closesAndStillAllowsConfirmation() {
        // given
        createPoll(); command.submit(scheduleId, new AvailabilityReqDTO.Submit(List.of(SLOT, SLOT.plusMinutes(30))), member);
        setClock(LocalDateTime.of(2026,11,9,12,0));
        // when
        AvailabilityException ex = assertThrows(AvailabilityException.class,
                () -> command.submit(scheduleId, new AvailabilityReqDTO.Submit(List.of()), member));
        command.closeDue(scheduleId);
        var poll = query.getPoll(scheduleId, owner);
        // then
        assertAll(() -> assertEquals(AvailabilityErrorCode.NOT_OPEN, ex.getCode()), () -> assertEquals("CLOSED", poll.status().name()));
        command.confirm(scheduleId, new AvailabilityReqDTO.Confirm(SLOT, poll.token()), manager);
        command.closeDue(scheduleId);
        assertTrue(query.getPoll(scheduleId, owner).confirmed());
    }
    @Test @DisplayName("재개방 후 이전 마감 작업은 새로운 미래 마감을 닫지 않는다")
    void deadline_reopened_doesNotCloseNewDeadline() {
        // given
        createPoll(); setClock(LocalDateTime.of(2026,11,9,12,0)); command.closeDue(scheduleId);
        command.configure(scheduleId, new AvailabilityReqDTO.Configure(LocalDate.of(2026,11,10),3,9,12,START.minusDays(1)), owner);
        // when
        command.closeDue(scheduleId);
        // then
        assertEquals("OPEN", query.getPoll(scheduleId, owner).status().name());
    }
    @Test @DisplayName("확정된 투표 삭제는 확정 시간을 유지하고 종속 행은 모두 지운다")
    void delete_confirmedPoll_preservesScheduleAndRemovesChildren() {
        // given
        confirm(); Long pollId = query.getPoll(scheduleId, owner).pollId();
        // when
        command.delete(scheduleId, owner); em.flush(); em.clear();
        // then
        assertAll(() -> assertEquals(SLOT, em.find(Schedule.class, scheduleId).getStartsAt()),
                () -> assertEquals(0, jdbc.queryForObject("select count(*) from availability_response where availability_poll_id=?", Integer.class, pollId)),
                () -> assertEquals(0, jdbc.queryForObject("select count(*) from availability_slot where availability_poll_id=?", Integer.class, pollId)),
                () -> assertEquals(0, jdbc.queryForObject("select count(*) from availability_vote v join availability_slot s on v.availability_slot_id=s.id where s.availability_poll_id=?", Integer.class, pollId)));
    }
    @ParameterizedTest @ValueSource(strings = {"{\"slots\":[\"2026-11-10T09:00:00\",\"2026-11-10T09:00:00\"]}",
            "{\"slots\":[\"2026-11-10T09:15:00\"]}", "{\"slots\":[\"2026-11-10T09:00:00.123\"]}",
            "{\"slots\":[null]}", "{\"slots\":null}", "{\"slots\":[\"2026-11-10T13:00:00\"]}"})
    @DisplayName("슬롯 오류는 HTTP 400으로 거부하고 기존 응답을 보존한다")
    void submit_invalidSlots_rejectsAtomically(String body) throws Exception {
        // given
        createPoll(); command.submit(scheduleId, new AvailabilityReqDTO.Submit(List.of(SLOT)), member);
        // when
        var result = mvc.perform(auth(put(path("availability/me")).contentType(MediaType.APPLICATION_JSON).content(body), member));
        // then
        result.andExpect(status().isBadRequest()); assertEquals(List.of(SLOT), query.getMe(scheduleId, member).slots());
    }
    @Test @DisplayName("관리자와 일반 구성원 권한 및 JWT·CSRF를 구분한다")
    void security_roleCookieAndCsrf_enforcesContract() throws Exception {
        // given
        createPoll();
        // when
        var result = mvc.perform(get(path("availability")));
        // then
        result.andExpect(status().isUnauthorized());
        mvc.perform(put(path("availability/me")).cookie(cookie(member)).contentType(MediaType.APPLICATION_JSON).content("{\"slots\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(auth(patch(path("availability-poll")).contentType(MediaType.APPLICATION_JSON).content(CONFIG), member)).andExpect(status().isForbidden());
        mvc.perform(auth(delete(path("availability-poll")), manager)).andExpect(status().isForbidden());
        mvc.perform(auth(post(path("availability-poll")).contentType(MediaType.APPLICATION_JSON).content(CONFIG), owner)).andExpect(status().isConflict());
    }
    @ParameterizedTest
    @ValueSource(strings = {"{\"dayCount\":4}", "{\"startHour\":9,\"endHour\":9}",
            "{\"endHour\":25}", "{\"startDate\":\"2026-02-30\"}",
            "{\"voteDeadlineAt\":\"2026-11-04T12:00:00\"}", "{\"dayCount\":null}"})
    @DisplayName("후보 설정의 날짜·일수·시간·미래 마감 제약을 HTTP 400으로 검증한다")
    void configure_invalidFields_rejectsInput(String patchBody) throws Exception {
        // given
        var node=(tools.jackson.databind.node.ObjectNode)mapper.readTree(CONFIG);
        mapper.readTree(patchBody).properties().forEach(e -> node.set(e.getKey(),e.getValue()));
        // when
        var result=mvc.perform(auth(post(path("availability-poll")).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(node)),owner));
        // then
        result.andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings = {"ARCHIVED","CLOSED","CANCELLED","STARTED","DELETED"})
    @DisplayName("모임·일정 상태에 따른 기존 접근·변경 제한을 개인 응답에도 적용한다")
    void submit_restrictedParentState_rejectsChanges(String state) throws Exception {
        // given
        createPoll(); em.flush();
        switch(state) {
            case "ARCHIVED", "CLOSED" -> jdbc.update("update member_group set status=? where id=?",state,group.getId());
            case "CANCELLED" -> jdbc.update("update schedule set status='CANCELLED' where id=?",scheduleId);
            case "STARTED" -> jdbc.update("update schedule set starts_at=? where id=?",NOW,scheduleId);
            case "DELETED" -> jdbc.update("update schedule set deleted_at=? where id=?",NOW,scheduleId);
            default -> throw new IllegalStateException();
        }
        em.clear();
        // when
        var result=mvc.perform(auth(put(path("availability/me")).contentType(MediaType.APPLICATION_JSON).content("{\"slots\":[]}"),member));
        // then
        result.andExpect(status().is(state.equals("CLOSED") || state.equals("DELETED") ? 404 : 409));
    }
    @Test @DisplayName("재개방·재확정 후에도 직전 원래 시간을 복원하고 제목 수정은 보존한다")
    void restore_reconfirmedAndTitleEdited_restoresLatestOriginalTimes() {
        // given
        confirm(); command.configure(scheduleId,config(9,12),owner);
        var original=START.plusDays(1);
        em.find(Schedule.class,scheduleId).reschedule(original,original.plusMinutes(45));
        command.confirm(scheduleId,new AvailabilityReqDTO.Confirm(SLOT,query.getPoll(scheduleId,owner).token()),owner);
        Schedule s=em.find(Schedule.class,scheduleId);
        s.update("변경 제목",s.getDescription(),s.getStartsAt(),s.getEndsAt(),s.getLocationName(),s.getLocationAddress(),null,null,null,null);
        // when
        command.configure(scheduleId,config(9,12),owner);
        // then
        assertAll(() -> assertEquals(original,s.getStartsAt()), () -> assertEquals("변경 제목",s.getTitle()),
                () -> assertEquals(List.of(SLOT,SLOT.plusMinutes(30)),query.getMe(scheduleId,member).slots()));
    }
    @Test @DisplayName("Swagger에 가용시간 9개 API와 분리된 응답 스키마를 제공한다")
    void docs_allAvailabilityOperations_exposesContract() throws Exception {
        // given
        var request=get("/v3/api-docs");
        // when
        var result=mvc.perform(request);
        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/schedules/{scheduleId}/availability-poll'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedules/{scheduleId}/availability-poll'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedules/{scheduleId}/availability-poll'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedules/{scheduleId}/availability-poll'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedules/{scheduleId}/availability/me'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedules/{scheduleId}/availability/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedules/{scheduleId}/availability/me'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedules/{scheduleId}/availability'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/schedules/{scheduleId}/availability-poll/confirm'].post").exists())
                .andExpect(jsonPath("$.components.schemas.AvailabilityDetail.properties.recommendations").exists());
    }
    private void confirm() {
        createPoll(); command.submit(scheduleId, new AvailabilityReqDTO.Submit(List.of(SLOT, SLOT.plusMinutes(30))), member);
        command.confirm(scheduleId, new AvailabilityReqDTO.Confirm(SLOT, query.getPoll(scheduleId, owner).token()), owner);
    }
    private void createPoll() { command.create(scheduleId, config(9, 12), owner); }
    private AvailabilityReqDTO.Configure config(int start, int end) {
        return new AvailabilityReqDTO.Configure(LocalDate.of(2026,11,10),3,start,end,LocalDateTime.of(2026,11,9,12,0));
    }
    private void setClock(LocalDateTime now) {
        Clock fixed = Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
        when(clock.withZone(SEOUL)).thenReturn(fixed); when(clock.instant()).thenReturn(fixed.instant()); when(clock.getZone()).thenReturn(SEOUL);
    }
    private Long membership(GroupMemberRole role) {
        Member m = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).nickname("가용"+UUID.randomUUID()).build();
        em.persist(m); em.persist(GroupMember.builder().group(group).member(m).role(role).status(GroupMemberStatus.ACTIVE).joinedAt(NOW).build());
        return m.getId();
    }
    private String path(String suffix) { return "/api/v1/schedules/" + scheduleId + "/" + suffix; }
    private Cookie cookie(Long id) { return new Cookie("moeasy_access_token", jwt.issueAccessToken(id).value()); }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder auth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Long id) {
        return request.cookie(cookie(id), new Cookie("XSRF-TOKEN", "test-csrf")).header("X-XSRF-TOKEN", "test-csrf");
    }
}

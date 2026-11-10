package com.moeasy.moeasybe.domain.group.service;

import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static com.moeasy.moeasybe.domain.schedule.dto.request.ParticipationReqDTO.ResponseStatus.*;
import static com.moeasy.moeasybe.domain.schedule.dto.request.ParticipationReqDTO.Mark.*;

import com.moeasy.moeasybe.domain.group.repository.*;
import com.moeasy.moeasybe.domain.group.repository.GroupTierRepository.Job;
import com.moeasy.moeasybe.domain.group.service.command.GroupTierWorker;
import com.moeasy.moeasybe.domain.group.service.query.GroupQueryService;
import com.moeasy.moeasybe.domain.schedule.dto.request.ParticipationReqDTO.*;
import com.moeasy.moeasybe.domain.schedule.service.command.ParticipationCommandService;
import com.moeasy.moeasybe.domain.schedule.service.query.ParticipationQueryService;
import com.moeasy.moeasybe.domain.schedule.repository.ParticipationRepository;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.time.*;
import java.util.concurrent.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
class ParticipationIntegrationTest extends JoinRequestIntegrationSupport {
    @Autowired ParticipationCommandService attendance;
    @Autowired ParticipationQueryService query;
    @Autowired ParticipationRepository data;
    @Autowired GroupTierRepository jobs;
    @Autowired GroupTierWorker worker;
    @Autowired GroupQueryService detail;
    @Autowired GroupMemberRepository memberships;
    @Autowired TransactionTemplate tx;
    @MockitoBean(name="clock") Clock clock;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private LocalDateTime end;
    private long owner, group, schedule;
    private final List<Long> members = new ArrayList<>();

    @BeforeEach
    void setup() {
        end = LocalDate.now(SEOUL).plusDays(3).atStartOfDay();
        at(end.minusDays(3));
        owner = member();
        group = group(owner, 20);
        for (int i=0;i<4;i++) {
            long member = member();
            commands.approve(group, apply(group, member), owner);
            members.add(member);
        }
        schedule = schedule(end);
    }

    private void at(LocalDateTime time) {
        when(clock.getZone()).thenReturn(SEOUL);
        when(clock.instant()).thenReturn(time.atZone(SEOUL).toInstant());
        when(clock.withZone(SEOUL)).thenReturn(Clock.fixed(time.atZone(SEOUL).toInstant(), SEOUL));
    }

    private long schedule(LocalDateTime endsAt) {
        String title = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO schedule(group_id,title,description,starts_at,ends_at,location_name,location_address)
                VALUES (?,?,'test',?,?,'test','test')
                """, group, title, endsAt.minusHours(1), endsAt);
        return jdbc.queryForObject("SELECT id FROM schedule WHERE group_id=? AND title=?", Long.class, group, title);
    }

    private long membership(long member) {
        return jdbc.queryForObject("SELECT id FROM group_member WHERE group_id=? AND member_id=?", Long.class, group, member);
    }

    @Override
    @AfterEach
    void cleanJoinFixtures() {
        jdbc.update("DELETE FROM group_tier_job WHERE group_id=?", group);
        jdbc.update("DELETE a FROM attendance a JOIN attendance_session x ON x.id=a.attendance_session_id JOIN schedule s ON s.id=x.schedule_id WHERE s.group_id=?", group);
        for (String table : List.of("attendance_session","schedule_rsvp","schedule_end_member","schedule_end_snapshot")) {
            jdbc.update("DELETE child FROM "+table+" child JOIN schedule s ON s.id=child.schedule_id WHERE s.group_id=?", group);
        }
        jdbc.update("DELETE FROM schedule WHERE group_id=?", group);
        super.cleanJoinFixtures();
    }

    @Test
    void permissionsRejoinAndDelayedRoster() {
        long attendee = members.getFirst();
        assertThatThrownBy(() -> attendance.respond(schedule, member(), ATTENDING)).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> attendance.prepareRoster(schedule, attendee)).isInstanceOf(GeneralException.class);
        attendance.respond(schedule, attendee, ATTENDING);
        attendance.respond(schedule, attendee, NOT_ATTENDING);
        attendance.respond(schedule, attendee, null);
        assertThat(query.rsvp(schedule, attendee).myStatus()).isEqualTo("UNDECIDED");
        attendance.respond(schedule, attendee, ATTENDING);
        attendance.respond(schedule, attendee, ATTENDING);
        assertThat(query.rsvp(schedule, owner).counts().get("ATTENDING")).isOne();

        long id = membership(attendee);
        endMembership(id, end.minusHours(4));
        at(end.minusHours(2));
        commands.approve(group, apply(group, attendee), owner);
        assertThat(query.rsvp(schedule, attendee).myStatus()).isEqualTo("UNDECIDED");
        attendance.respond(schedule, owner, ATTENDING);
        at(end.minusHours(1));
        assertThatThrownBy(() -> attendance.respond(schedule, owner, null)).isInstanceOf(GeneralException.class);
        var provisional = attendance.replace(schedule, owner, new Replace(0L, List.of(
                new Entry(membership(owner), LATE), new Entry(id, PRESENT))));
        assertThat(provisional.rosterFixed()).isFalse();
        assertThatThrownBy(() -> attendance.setCompleted(schedule, owner, 1, true)).isInstanceOf(GeneralException.class);
        // Exact end is excluded. A member who loses eligibility after end stays in the denominator.
        endMembership(membership(members.get(1)), end);
        endMembership(membership(members.get(2)), end.plusMinutes(1));
        at(end.plusDays(1));
        var done = attendance.setCompleted(schedule, owner, 1, true);
        assertThat(done.members()).hasSize(4);
        assertThat(done.members()).filteredOn(r -> r.credited()).singleElement().satisfies(r -> assertThat(r.status()).isEqualTo("LATE"));
        assertThat(done.members()).filteredOn(r -> r.groupMemberId() == id).singleElement().satisfies(r -> assertThat(r.preRegistered()).isFalse());
        assertThat(query.attendance(schedule, owner, attendance.prepareRoster(schedule, owner))).isEqualTo(done);
        assertThat(attendance.setCompleted(schedule, owner, done.version(), true)).isEqualTo(done);
    }

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired ObjectMapper mapper;

    private ResultActions call(String verb, String path, long member, String body)
            throws Exception {
        var request = MockMvcRequestBuilders.request(
                HttpMethod.valueOf(verb), "/api/v1/schedules/" + schedule + path);
        request.cookie(new Cookie("moeasy_access_token", jwt.issueAccessToken(member).value()),
                new Cookie("XSRF-TOKEN", "attendance-test"));
        request.header("X-XSRF-TOKEN", "attendance-test");
        if (body != null) request.contentType("application/json").content(body);
        return mvc.perform(request);
    }

    @Test
    void httpContractUsesAuthenticatedActorAndValidatesBeforeReplacement() throws Exception {
        var status = MockMvcResultMatchers.status();
        call("PUT", "/rsvp/me", owner, "{\"status\":\"ATTENDING\"}").andExpect(status.isOk());
        call("GET", "/rsvp", owner, null).andExpect(MockMvcResultMatchers
                .jsonPath("$.result.myStatus").value("ATTENDING"));
        call("DELETE", "/rsvp/me", owner, null).andExpect(status.isOk());
        call("PUT", "/rsvp/me", member(), "{\"status\":\"ATTENDING\"}").andExpect(status.isForbidden());
        call("GET", "/attendance", members.getFirst(), null).andExpect(status.isForbidden());
        call("GET", "/attendance", owner, null).andExpect(status.isOk());
        call("PUT", "/attendance", owner, "{\"version\":0,\"entries\":[]}").andExpect(status.isConflict());
        at(end);
        call("PUT", "/rsvp/me", owner, "{\"status\":\"ATTENDING\"}").andExpect(status.isConflict());
        call("PUT", "/attendance", owner, "{\"version\":0,\"entries\":[null]}").andExpect(status.isBadRequest());
        call("PUT", "/attendance", owner, "{\"version\":0,\"entries\":[{\"groupMemberId\":1,\"status\":\"UNKNOWN\"}]}")
                .andExpect(status.isBadRequest());
        call("PUT", "/attendance", owner, "{\"version\":0,\"entries\":[]}").andExpect(status.isOk());
        call("POST", "/attendance/complete", owner, "{\"version\":1}").andExpect(status.isOk());
        call("POST", "/attendance/reopen", owner, "{\"version\":1}").andExpect(status.isConflict());
        call("POST", "/attendance/reopen", owner, "{\"version\":2}").andExpect(status.isOk());
    }

    @Test
    void weightedTotalsUseEndRosterAndArchivedGroupsCanBeDemoted() {
        var ids = new ArrayList<>(members);
        ids.add(owner);
        List<Long> schedules = new ArrayList<>();
        schedules.add(schedule);
        for (int i = 1; i < 8; i++) schedules.add(schedule(end));
        List<Entry> late = ids.subList(0, 4).stream().map(id -> new Entry(membership(id), LATE)).toList();
        for (long id : schedules) {
            for (long actor : ids) attendance.respond(id, actor, ATTENDING);
        }
        at(end);
        for (long id : schedules) {
            attendance.replace(id, owner, new Replace(0L, late));
            attendance.setCompleted(id, owner, 1, true);
        }
        commitTier(end);
        assertThat(detail.getGroup(group, owner).tier()).isEqualTo("gold");
        var patch = mapper.readValue("{\"name\":\"changed\"}", GroupReqDTO.Update.class);
        assertThat(groups.updateGroup(group, patch, owner).tier()).isEqualTo("gold");

        at(end.plusMinutes(1));
        for (int i = 0; i < 5; i++) {
            long newMember = member();
            commands.approve(group, apply(group, newMember), owner);
        }
        long larger = schedule(end.plusDays(1));
        for (long actor : ids) attendance.respond(larger, actor, ATTENDING);
        at(end.plusDays(1));
        attendance.replace(larger, owner, new Replace(0L, ids.stream().map(id -> new Entry(membership(id), PRESENT)).toList()));
        attendance.setCompleted(larger, owner, 1, true);
        for (int i = 1; i < schedules.size(); i++) {
            jdbc.update("UPDATE schedule SET deleted_at=? WHERE id=?", end.plusDays(1), schedules.get(i));
        }
        assertThat(data.totals(group, end.minusDays(55), end.plusDays(1)))
                .isEqualTo(new ParticipationRepository.Totals(2, 2, 9, 15));
        jdbc.update("UPDATE member_group SET status='ARCHIVED' WHERE id=?", group);
        commitTier(end.plusDays(1));
        assertThat(detail.getGroup(group, owner).tier()).isEqualTo("bronze");
        commitTier(end.plusDays(58));
        assertThat(jdbc.queryForObject("SELECT activities FROM group_tier_job WHERE group_id=? AND basis=?",
                Long.class, group, end.plusDays(58).toLocalDate())).isZero();
    }

    @Test
    void competingWorkersRecoverFailureBeforeFreezeAndSkipClosedGroup() throws Exception {
        var basis = end.toLocalDate();
        jobs.enqueue(basis, List.of(group), "PENDING");
        var start = new CyclicBarrier(2);
        var first = new Job(basis, group, "one");
        var second = new Job(basis, group, "two");
        Job winner;
        try (var threads = Executors.newFixedThreadPool(2)) {
            var a = threads.submit(() -> { start.await(5, TimeUnit.SECONDS); return jobs.claim(first, end); });
            var b = threads.submit(() -> { start.await(5, TimeUnit.SECONDS); return jobs.claim(second, end); });
            boolean firstWon = a.get(10, TimeUnit.SECONDS);
            assertThat(b.get(10, TimeUnit.SECONDS)).isNotEqualTo(firstWon);
            winner = firstWon ? first : second;
        }
        jobs.fail(winner, "TransientDataAccessException", end.plusMinutes(1));
        var recovered = new Job(basis, group, "restarted");
        assertThat(jobs.ready(basis, end, group - 1)).doesNotContain(group);
        assertThat(jobs.ready(basis, end.plusMinutes(1), group - 1)).contains(group);
        assertThat(jobs.claim(recovered, end.plusMinutes(1))).isTrue();
        worker.freeze(recovered);
        groups.closeGroup(group, new GroupReqDTO.Close("closed", "가입 테스트", true), owner);
        worker.apply(recovered);
        assertThat(jdbc.queryForObject("SELECT state FROM group_tier_job WHERE basis=? AND group_id=?",
                String.class, basis, group)).isEqualTo("SKIPPED");
        assertThat(jdbc.queryForObject("SELECT tier_as_of FROM member_group WHERE id=?", LocalDate.class, group)).isNull();
    }

    @Test
    void legacyResponseRequiresReconfirmationAndPreCutoverScheduleOnlyCountsAsActivity() {
        long id = membership(owner);
        jdbc.update("INSERT INTO schedule_rsvp(schedule_id,group_member_id,status) VALUES (?,?,'ATTENDING')", schedule, id);
        at(end);
        var done = attendance.replace(schedule, owner, new Replace(0L, List.of(new Entry(id, PRESENT))));
        assertThat(done.members()).noneMatch(m -> m.credited());
        attendance.setCompleted(schedule, owner, 1, true);
        LocalDateTime cutover = jdbc.queryForObject("SELECT established_at FROM attendance_cutover", LocalDateTime.class);
        long legacy = schedule(cutover.minusHours(1));
        var historic = attendance.setCompleted(legacy, owner, 0, true);
        assertThat(historic.recoverable()).isFalse();
        assertThat(data.totals(group, cutover.minusDays(1), cutover))
                .isEqualTo(new ParticipationRepository.Totals(1, 0, 0, 0));
    }

    @Test
    void accountInactivationAndLaterJoinsCannotRewriteEndRoster() {
        long departing = members.getFirst();
        long afterEnd = members.get(1);
        for (long actor : List.of(departing, afterEnd)) attendance.respond(schedule, actor, ATTENDING);
        deactivate(departing, end.minusSeconds(1));
        deactivate(afterEnd, end.plusSeconds(1));
        at(end.plusDays(1));
        long newcomer = member();
        commands.approve(group, apply(group, newcomer), owner);
        long retained = membership(afterEnd);
        var result = attendance.replace(schedule, owner, new Replace(0L, List.of(new Entry(retained, PRESENT))));
        assertThat(result.members()).hasSize(4);
        assertThat(result.members()).noneMatch(m -> m.groupMemberId() == membership(departing)
                || m.groupMemberId() == membership(newcomer));
        assertThat(result.members()).filteredOn(m -> m.groupMemberId() == retained)
                .singleElement().satisfies(m -> assertThat(m.credited()).isTrue());
    }

    private void deactivate(long actor, LocalDateTime at) {
        tx.executeWithoutResult(status -> {
            memberships.closePeriod(membership(actor), at);
            jdbc.update("UPDATE member SET status='BANNED' WHERE id=?", actor);
        });
    }

    private void commitTier(LocalDateTime basis) {
        Job job = new Job(basis.toLocalDate(), group, UUID.randomUUID().toString());
        jobs.enqueue(job.basis(), List.of(group),"PENDING");
        assertThat(jobs.claim(job, basis)).isTrue();
        worker.freeze(job);
        worker.apply(job);
    }

    private void endMembership(long membership, LocalDateTime at) {
        tx.executeWithoutResult(status -> {
            memberships.closePeriod(membership, at);
            jdbc.update("UPDATE group_member SET status='LEFT',deleted_at=? WHERE id=?", at, membership);
        });
    }

    @Test
    void correctionAndFrozenJobSurviveRetryAndOlderWorker() {
        attendance.respond(schedule, owner, ATTENDING);
        at(end.plusMinutes(1));
        var begin = end.minusDays(56);
        schedule(begin);
        assertThat(data.totals(group, begin, end)).isEqualTo(new ParticipationRepository.Totals(1, 0, 0, 0));
        var done = attendance.setCompleted(schedule, owner, 0, true);
        assertThat(done.members()).allMatch(r -> r.status().equals("ABSENT"));
        assertThat(data.totals(group, begin, end)).isEqualTo(new ParticipationRepository.Totals(1, 1, 0, 5));
        assertThatThrownBy(() -> attendance.replace(schedule, owner, new Replace(1L, List.of()))).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> attendance.setCompleted(schedule, owner, 0, false)).isInstanceOf(GeneralException.class);
        attendance.setCompleted(schedule, owner, 1, false);
        assertThat(data.totals(group, begin, end).evaluated()).isZero();
        long id = membership(owner);
        var entry = new Entry(id, PRESENT);
        assertThatThrownBy(() -> attendance.replace(schedule, owner, new Replace(2L, List.of(entry, entry)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attendance.replace(schedule, owner, new Replace(2L, List.of(new Entry(Long.MAX_VALUE, LATE))))).isInstanceOf(IllegalArgumentException.class);
        attendance.replace(schedule, owner, new Replace(2L, List.of(entry)));
        attendance.replace(schedule, owner, new Replace(3L, List.of()));
        attendance.replace(schedule, owner, new Replace(4L, List.of(entry)));
        attendance.setCompleted(schedule, owner, 5, true);
        Job old = new Job(end.toLocalDate(), group,"first");
        jobs.enqueue(old.basis(), List.of(group),"PENDING");
        assertThat(jobs.claim(old, end)).isTrue();
        worker.freeze(old);
        attendance.setCompleted(schedule, owner, 6, false);
        Job retry = new Job(old.basis(), group,"second");
        assertThat(jobs.claim(retry, end)).isFalse();
        assertThat(jobs.claim(retry, end.plusMinutes(1))).isTrue();
        worker.apply(old);
        worker.freeze(retry);
        assertThat(jobs.owned(retry).numerator()).isOne();
        Job newer = new Job(old.basis().plusDays(1), group,"latest");
        jobs.enqueue(newer.basis(), List.of(group),"PENDING");
        assertThat(jobs.claim(newer, end.plusDays(1))).isTrue();
        worker.freeze(newer);
        assertThat(jobs.owned(newer).evaluated()).isZero();
        worker.apply(newer);
        worker.apply(retry);
        assertThat(jdbc.queryForObject("SELECT state FROM group_tier_job WHERE basis=? AND group_id=?", String.class, old.basis(), group)).isEqualTo("SKIPPED");
        assertThat(detail.getGroup(group, owner).tier()).isEqualTo("bronze");
        assertThat(jobs.claim(newer, end.plusDays(2))).isFalse();
    }
}

package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moeasy.moeasybe.domain.group.entity.*;
import com.moeasy.moeasybe.domain.group.enums.*;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.schedule.dto.request.ScheduleReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.ScheduleResDTO;
import com.moeasy.moeasybe.domain.schedule.service.command.ScheduleCommandService;
import com.moeasy.moeasybe.domain.schedule.service.query.ScheduleQueryService;
import com.moeasy.moeasybe.domain.schedule.repository.ScheduleRepository;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScheduleApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider jwt;
    @Autowired ScheduleCommandService command;
    @Autowired ScheduleQueryService query;
    @Autowired ScheduleRepository repository;
    @MockitoBean(name = "clock") Clock clock;
    private Group group;
    private Long owner;
    private Long manager;
    private Long member;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);
    private static final String BODY = """
            {"title":"모임", "description":"설명", "startsAt":"2026-11-10T12:00:00",
             "endsAt":"2026-11-10T13:00:00", "locationName":"장소", "locationAddress":"주소"}
            """;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        when(clock.withZone(ZoneId.of("Asia/Seoul"))).thenReturn(fixed);
        Category category = em.createQuery("select c from Category c where c.code = 'RUNNING'", Category.class)
                .getSingleResult();
        group = Group.builder().category(category).name("일정 테스트").description("설명")
                .regionCode("11680").placeName("장소").address("주소").maxMembers(20)
                .joinPolicy(GroupJoinPolicy.APPROVAL).build();
        em.persist(group);
        owner = membership(GroupMemberRole.OWNER);
        manager = membership(GroupMemberRole.MANAGER);
        member = membership(GroupMemberRole.MEMBER);
    }

    @Test
    @DisplayName("생성·조회·수정·취소·소프트 삭제와 재요청 계약을 유지한다")
    void lifecycle_validRequests_preservesSoftDeletedRow() throws Exception {
        // given
        var request = post(listPath()).contentType(MediaType.APPLICATION_JSON).content(BODY);
        // when
        String response = mvc.perform(auth(request, owner)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = mapper.readTree(response).path("result").path("scheduleId").longValue();
        // then
        mvc.perform(auth(get(path(id)), member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("SCHEDULED"));
        mvc.perform(auth(patch(path(id)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정\"}"), manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.description").value("설명"));
        mvc.perform(auth(post(path(id) + "/cancel"), manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").isEmpty());
        mvc.perform(auth(get(path(id)), member)).andExpect(jsonPath("$.result.status").value("CANCELLED"));
        mvc.perform(auth(post(path(id) + "/cancel"), owner)).andExpect(status().isConflict());
        mvc.perform(auth(patch(path(id)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정\"}"), owner)).andExpect(status().isConflict());
        mvc.perform(auth(delete(path(id)), manager)).andExpect(status().isForbidden());
        mvc.perform(auth(delete(path(id)), owner)).andExpect(status().isOk());
        mvc.perform(auth(delete(path(id)), owner)).andExpect(status().isNotFound());
        mvc.perform(auth(get(path(id)), member)).andExpect(status().isNotFound());
        assertAll(() -> assertFalse(repository.existsByIdAndGroupIdAndDeletedAtIsNull(id, group.getId())),
                () -> assertEquals(1, jdbc.queryForObject("select count(*) from schedule where id=?", Integer.class, id)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":null}", "{\"title\":\" \"}", "{\"title\":12}",
            "{\"latitude\":37}", "{\"latitude\":null,\"longitude\":127}",
            "{\"status\":\"CANCELLED\"}", "{\"endsAt\":\"2026-11-10T11:00:00\"}",
            "{\"startsAt\":\"2026-11-02T12:00:00\"}", "{\"startsAt\":\"2026-02-30T12:00:00\"}",
            "{\"placeProvider\":\"KAKAO\"}", "{\"latitude\":91,\"longitude\":127}",
            "{\"placeProvider\":\"KAKAO\",\"placeId\":\" \"}"})
    @DisplayName("잘못된 PATCH 입력과 금지 필드를 400으로 거부한다")
    void patch_invalidInput_returns400(String body) throws Exception {
        // given
        Long id = create();
        var request = patch(path(id)).contentType(MediaType.APPLICATION_JSON).content(body);
        // when
        var result = mvc.perform(auth(request, owner));
        // then
        result.andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("선택 필드 쌍을 설정한 후 null로 제거하며 나머지는 유지한다")
    void patch_optionalPair_clearsValues() throws Exception {
        // given
        Long id = create();
        command.updateSchedule(id, mapper.readValue("{\"latitude\":37,\"longitude\":127}", ScheduleReqDTO.Update.class), owner);
        // when
        ScheduleResDTO.Detail result = command.updateSchedule(id,
                mapper.readValue("{\"latitude\":null,\"longitude\":null}", ScheduleReqDTO.Update.class), owner);
        // then
        assertAll(() -> assertNull(result.latitude()), () -> assertNull(result.longitude()),
                () -> assertEquals("모임", result.title()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-02T12:00:00", "2026-11-01T12:00:00"})
    @DisplayName("시작 시각부터 수정·취소는 거부하지만 OWNER 삭제는 허용한다")
    void change_startedSchedule_rejectsExceptDelete(String start) throws Exception {
        // given
        Long id = create();
        jdbc.update("update schedule set starts_at=?, ends_at='2026-11-03 12:00:00' where id=?", start, id);
        em.clear();
        // when
        var cancelResult = mvc.perform(auth(post(path(id) + "/cancel"), owner));
        // then
        cancelResult.andExpect(status().isConflict());
        mvc.perform(auth(patch(path(id)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정\"}"), owner)).andExpect(status().isConflict());
        mvc.perform(auth(delete(path(id)), owner)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARCHIVED", "CLOSED", "DELETED"})
    @DisplayName("보관 모임은 읽기만 가능하고 폐쇄·삭제 모임은 조회할 수 없다")
    void access_groupState_appliesPolicy(String state) throws Exception {
        // given
        Long id = create();
        if (state.equals("DELETED")) jdbc.update("update member_group set deleted_at=now() where id=?", group.getId());
        else jdbc.update("update member_group set status=? where id=?", state, group.getId());
        em.clear();
        int read = state.equals("ARCHIVED") ? 200 : 404;
        int write = state.equals("ARCHIVED") ? 409 : 404;
        // when
        var result = mvc.perform(auth(get(path(id)), owner));
        // then
        result.andExpect(status().is(read));
        mvc.perform(auth(get(listPath()), owner)).andExpect(status().is(read));
        mvc.perform(auth(post(listPath()).contentType(MediaType.APPLICATION_JSON).content(BODY), owner))
                .andExpect(status().is(write));
        mvc.perform(auth(patch(path(id)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"수정\"}"), owner))
                .andExpect(status().is(write));
        mvc.perform(auth(post(path(id) + "/cancel"), owner)).andExpect(status().is(write));
        mvc.perform(auth(delete(path(id)), owner)).andExpect(status().is(write));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEFT", "KICKED", "DELETED", "OUTSIDER"})
    @DisplayName("유효하지 않은 구성원은 일정을 읽거나 변경할 수 없다")
    void access_invalidMembership_returns403(String state) throws Exception {
        // given
        Long id = create();
        if (state.equals("DELETED")) jdbc.update("update group_member set deleted_at=now() where member_id=?", member);
        else if (!state.equals("OUTSIDER")) jdbc.update("update group_member set status=? where member_id=?", state, member);
        Long requester = state.equals("OUTSIDER") ? Long.MAX_VALUE : member;
        em.clear();
        // when
        var result = mvc.perform(auth(get(path(id)), requester));
        // then
        result.andExpect(status().isForbidden());
        mvc.perform(auth(get(listPath()), requester)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("쿠키 인증 쓰기는 CSRF가 필요하고 일반 구성원은 생성할 수 없다")
    void security_unauthorizedOrMissingCsrf_rejectsRequest() throws Exception {
        // given
        var anonymous = get(listPath());
        // when
        var result = mvc.perform(anonymous);
        // then
        result.andExpect(status().isUnauthorized());
        mvc.perform(post(listPath()).cookie(cookie(owner)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post(listPath()).contentType(MediaType.APPLICATION_JSON).content(BODY), member))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("기간 겹침·동일 생성 시각 커서·조건 불일치·빈 목록을 검증한다")
    void list_overlappingPeriodsAndCursor_returnsStablePages() {
        // given
        Long first = create();
        Long second = create();
        jdbc.update("update schedule set created_at='2026-10-01 12:00:00' where group_id=?", group.getId());
        jdbc.update("update schedule set starts_at='2026-10-31 23:00:00', ends_at='2026-11-01 01:00:00' where id=?", second);
        em.flush();
        em.clear();
        // when
        var page = query.getSchedules(group.getId(), null, null, 1, null, member);
        var next = query.getSchedules(group.getId(), null, null, 1, page.nextCursor(), member);
        // then
        assertAll(() -> assertEquals(second, page.items().getFirst().scheduleId()),
                () -> assertEquals(first, next.items().getFirst().scheduleId()),
                () -> assertTrue(page.hasNext()), () -> assertFalse(next.hasNext()), () -> assertNull(next.nextCursor()),
                () -> assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), page.from()),
                () -> assertThrows(IllegalArgumentException.class, () -> query.getSchedules(group.getId(),
                        NOW, NOW.plusDays(1), 1, page.nextCursor(), member)),
                () -> assertThrows(IllegalArgumentException.class, () -> query.getSchedules(group.getId(), null, null, 1, "bad", member)));
        var october = query.getSchedules(group.getId(), LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0), 20, null, member);
        assertEquals(List.of(second), october.items().stream().map(ScheduleResDTO.Summary::scheduleId).toList());
        var empty = query.getSchedules(group.getId(), NOW.plusYears(1), NOW.plusYears(2), 20, null, member);
        assertTrue(empty.items().isEmpty());
    }

    @Test
    @DisplayName("취소·삭제 후에도 기존 정산 연결은 보존되고 신규 연결 검증만 실패한다")
    void delete_linkedSettlement_preservesExistingSettlement() {
        // given
        Long id = create();
        GroupMember membership = em.createQuery("select m from GroupMember m where m.member.id=:id", GroupMember.class)
                .setParameter("id", owner).getSingleResult();
        var settlement = com.moeasy.moeasybe.domain.settlement.entity.Settlement.builder()
                .group(group).schedule(em.getReference(com.moeasy.moeasybe.domain.schedule.entity.Schedule.class, id)).createdByGroupMember(membership).title("연결 정산")
                .totalAmount(100L).splitMode(com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode.EQUAL).build();
        em.persist(settlement);
        em.flush();
        // when
        command.cancelSchedule(id, owner);
        assertTrue(repository.existsByIdAndGroupIdAndDeletedAtIsNull(id, group.getId()));
        command.deleteSchedule(id, owner);
        em.flush();
        em.clear();
        // then
        assertAll(() -> assertFalse(repository.existsByIdAndGroupIdAndDeletedAtIsNull(id, group.getId())),
                () -> assertEquals(id, jdbc.queryForObject("select schedule_id from settlement where id=?", Long.class, settlement.getId())),
                () -> assertEquals("OPEN", jdbc.queryForObject("select status from settlement where id=?", String.class, settlement.getId())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"from=2026-11-01T00:00:00", "from=2026-11-02T00:00:00&to=2026-11-01T00:00:00",
            "from=2026-11-01T00:00&to=2026-12-01T00:00",
            "from=2026-11-01T00:00:00&to=2026-12-01T00:00",
            "from=2026-11-01T00:00:00.000&to=2026-12-01T00:00:00",
            "from=2026-11-01T00:00:00&to=2026-12-01T00:00:00Z",
            "from=2026-11-01T00:00:00Z&to=2026-12-01T00:00:00",
            "from=2026-02-29T00:00:00&to=2026-03-01T00:00:00",
            "from=0999-11-01T00:00:00&to=2026-12-01T00:00:00",
            "size=0", "size=101", "cursor=bad", "from=2026-02-30T00:00:00&to=2026-03-01T00:00:00"})
    @DisplayName("잘못된 목록 조회 조건을 HTTP 400으로 거부한다")
    void list_invalidQuery_returns400(String params) throws Exception {
        // given
        var request = get(listPath() + "?" + params);
        // when
        var result = mvc.perform(auth(request, owner));
        // then
        result.andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"title\":null}", "{\"startsAt\":\"2026-11-02T12:00:00\"}",
            "{\"endsAt\":\"2026-11-10T11:00:00\"}", "{\"startsAt\":\"2026-11-10T12:00:00.123\"}",
            "{\"latitude\":90,\"longitude\":181}", "{\"placeProvider\":\"UNKNOWN\",\"placeId\":\"1\"}"})
    @DisplayName("잘못된 생성 입력과 현재 시각 시작을 거부한다")
    void create_invalidFields_returns400(String patch) throws Exception {
        // given
        var node = (tools.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
        mapper.readTree(patch).properties().forEach(entry -> node.set(entry.getKey(), entry.getValue()));
        var request = post(listPath()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(node));
        // when
        var result = mvc.perform(auth(request, owner));
        // then
        result.andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "-1", "0.0", "true", "[]", "{}", "\"0\"", "\"UNKNOWN\""})
    @DisplayName("생성과 PATCH는 숫자·숫자 문자열·잘못된 타입의 장소 제공자를 거부한다")
    void write_invalidPlaceProvider_returns400(String provider) throws Exception {
        // given
        Long id = create();
        String fields = "{\"placeProvider\":" + provider + ",\"placeId\":\"123\"}";
        var body = (tools.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
        mapper.readTree(fields).properties().forEach(entry -> body.set(entry.getKey(), entry.getValue()));

        // when
        var result = mvc.perform(auth(post(listPath()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)), owner));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400_1"));
        mvc.perform(auth(patch(path(id)).contentType(MediaType.APPLICATION_JSON).content(fields), owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400_1"));
        assertAll(
                () -> assertEquals(1, jdbc.queryForObject("select count(*) from schedule where group_id=?", Integer.class, group.getId())),
                () -> assertNull(repository.findById(id).orElseThrow().getPlaceProvider()),
                () -> assertNull(repository.findById(id).orElseThrow().getPlaceId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"KAKAO", "GOOGLE"})
    @DisplayName("문자열 장소 제공자는 생성·PATCH에 허용하고 선택 필드 쌍은 null로 제거한다")
    void write_stringPlaceProvider_supportsCreateUpdateAndClear(String provider) throws Exception {
        // given
        var body = (tools.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
        body.put("placeProvider", provider);
        body.put("placeId", "123");

        // when
        String response = mvc.perform(auth(post(listPath()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)), owner))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long id = mapper.readTree(response).path("result").path("scheduleId").longValue();

        // then
        mvc.perform(auth(get(path(id)), owner)).andExpect(jsonPath("$.result.placeProvider").value(provider));
        mvc.perform(auth(patch(path(id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeProvider\":\"" + provider + "\",\"placeId\":\"456\"}"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.placeId").value("456"));
        mvc.perform(auth(patch(path(id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeProvider\":null,\"placeId\":null}"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.placeProvider").isEmpty())
                .andExpect(jsonPath("$.result.placeId").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 10000})
    @DisplayName("DB 날짜 범위 밖의 커서는 HTTP 400으로 응답한다")
    void list_cursorDateOutsideDatabaseRange_returns400(int year) throws Exception {
        // given
        String cursor = new com.moeasy.moeasybe.domain.schedule.service.support.ScheduleCursor(group.getId(),
                NOW.withDayOfMonth(1), NOW.plusMonths(1).withDayOfMonth(1), NOW.withYear(year), 1L).encode();

        // when
        var result = mvc.perform(auth(get(listPath()).param("cursor", cursor), owner));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("초까지 지정한 기간과 마이크로초 커서는 HTTP 후속 조회에서도 유지된다")
    void list_strictTimesAndCursor_preservesInitialWindow() throws Exception {
        // given
        create();
        create();
        jdbc.update("update schedule set created_at='2026-10-01 12:00:00.123456' where group_id=?", group.getId());
        em.flush();
        em.clear();

        // when
        String response = mvc.perform(auth(get(listPath()).param("from", "2026-11-01T00:00:00")
                        .param("to", "2026-12-01T00:00:00").param("size", "1"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = mapper.readTree(response).path("result").path("nextCursor").asText();

        // then
        mvc.perform(auth(get(listPath()).param("cursor", cursor).param("size", "1"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.from").value("2026-11-01T00:00:00"))
                .andExpect(jsonPath("$.result.to").value("2026-12-01T00:00:00"))
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @ParameterizedTest
    @CsvSource({
            "BANNED, MEMBER, DETAIL", "BANNED, MEMBER, LIST",
            "BANNED, OWNER, CREATE", "BANNED, OWNER, UPDATE",
            "BANNED, OWNER, CANCEL", "BANNED, OWNER, DELETE", "BANNED, MANAGER, UPDATE",
            "DELETED, MEMBER, DETAIL", "DELETED, MEMBER, LIST",
            "DELETED, OWNER, CREATE", "DELETED, OWNER, UPDATE",
            "DELETED, OWNER, CANCEL", "DELETED, OWNER, DELETE", "DELETED, MANAGER, UPDATE"
    })
    @DisplayName("활성 멤버십이 남아 있어도 정지·삭제 상태의 계정은 일정에 접근할 수 없다")
    void access_inactiveAccount_returns403(String accountStatus, GroupMemberRole role, String operation) throws Exception {
        // given
        Long scheduleId = create();
        Long requester = switch (role) {
            case OWNER -> owner;
            case MANAGER -> manager;
            case MEMBER -> member;
        };
        em.flush();
        jdbc.update("update member set status=? where id=?", accountStatus, requester);
        em.clear();
        var request = switch (operation) {
            case "DETAIL" -> get(path(scheduleId));
            case "LIST" -> get(listPath());
            case "CREATE" -> post(listPath()).contentType(MediaType.APPLICATION_JSON).content(BODY);
            case "UPDATE" -> patch(path(scheduleId)).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"수정\"}");
            case "CANCEL" -> post(path(scheduleId) + "/cancel");
            case "DELETE" -> delete(path(scheduleId));
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };

        // when
        var result = mvc.perform(auth(request, requester));

        // then
        result.andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({
            "from,2026-11-01T00:00", "to,2026-12-01T00:00",
            "from,2026-11-01T00:00:00+09:00", "to,2026-12-01T00:00:00Z",
            "from,2026-11-01T00:00:00.000", "to,2026-12-01T00:00:00.001",
            "from,2026-11-31T00:00:00", "to,2026-12-32T00:00:00"
    })
    @DisplayName("목록 조회는 초 생략·오프셋·소수 초·유효하지 않은 날짜를 거부한다")
    void list_invalidTimeFormat_returns400(String field, String value) throws Exception {
        // given
        String from = field.equals("from") ? value : "2026-11-01T00:00:00";
        String to = field.equals("to") ? value : "2026-12-01T00:00:00";
        var request = get(listPath()).param("from", from).param("to", to);

        // when
        var result = mvc.perform(auth(request, owner));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("초를 포함한 조회 기간과 기간 생략 시 기본 기간을 모두 허용한다")
    void list_validOrOmittedPeriod_returnsSchedules(boolean explicitPeriod) throws Exception {
        // given
        create();
        var request = get(listPath());
        if (explicitPeriod) request.param("from", "2026-11-01T00:00:00").param("to", "2026-12-01T00:00:00");

        // when
        var result = mvc.perform(auth(request, owner));

        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.result.items.length()").value(1));
    }

    private Long create() {
        return command.createSchedule(group.getId(), mapper.readValue(BODY, ScheduleReqDTO.Create.class), owner).scheduleId();
    }
    private Long membership(GroupMemberRole role) {
        Member m = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString())
                .nickname("일정" + UUID.randomUUID()).build();
        em.persist(m);
        em.persist(GroupMember.builder().group(group).member(m).role(role).status(GroupMemberStatus.ACTIVE)
                .joinedAt(NOW).build());
        return m.getId();
    }
    private String listPath() { return "/api/v1/groups/" + group.getId() + "/schedules"; }
    private String path(Long id) { return "/api/v1/schedules/" + id; }
    private Cookie cookie(Long id) { return new Cookie("moeasy_access_token", jwt.issueAccessToken(id).value()); }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder auth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Long id) {
        return request.cookie(cookie(id), new Cookie("XSRF-TOKEN", "test-csrf")).header("X-XSRF-TOKEN", "test-csrf");
    }
}

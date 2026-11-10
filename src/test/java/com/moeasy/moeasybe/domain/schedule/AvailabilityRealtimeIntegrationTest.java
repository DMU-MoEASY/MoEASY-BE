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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AvailabilityRealtimeIntegrationTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ScheduleCommandService schedules;
    @Autowired AvailabilityCommandService command;
    @Autowired AvailabilityQueryService query;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider jwt;
    @Autowired com.moeasy.moeasybe.global.config.CorsProperties cors;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired org.springframework.data.redis.connection.RedisConnectionFactory connection;
    @Autowired com.moeasy.moeasybe.domain.schedule.realtime.AvailabilitySocketHandler handler;
    private Long groupId, memberId, scheduleId;
    private final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
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
    @Test @DisplayName("실제 WebSocket은 인증·Origin·일정 구성원 권한을 검증한다")
    void handshake_invalidSecurity_rejectsConnection() {
        // given
        var uri = java.net.URI.create("ws://localhost:"+port+"/ws/schedules/"+scheduleId+"/availability");
        // when
        var anonymous = assertThrows(CompletionException.class, () -> client.newWebSocketBuilder()
                .header("Origin",cors.allowedOrigins().getFirst()).buildAsync(uri,new Listener()).join());
        var badOrigin = assertThrows(CompletionException.class, () -> client.newWebSocketBuilder()
                .header("Origin","https://untrusted.invalid").header("Cookie",cookie()).buildAsync(uri,new Listener()).join());
        // then
        assertAll(() -> assertEquals(401,((java.net.http.WebSocketHandshakeException)anonymous.getCause()).getResponse().statusCode()),
                () -> assertEquals(403,((java.net.http.WebSocketHandshakeException)badOrigin.getCause()).getResponse().statusCode()));
    }
    @Test @DisplayName("커밋한 변경만 Redis를 거쳐 전달하고 자격 상실 후 수신을 차단한다")
    void notifications_committedChangeAndRevocation_preservesPrivacy() throws Exception {
        // given
        command.create(scheduleId,config(),memberId);
        Listener listener=new Listener(); var socket=connect(listener);
        assertTrue(listener.messages.poll(5,TimeUnit.SECONDS).contains("CONNECTED"));
        try {
            // when
            tx.executeWithoutResult(status -> {
                events.publishEvent(new com.moeasy.moeasybe.domain.schedule.realtime.AvailabilityChanged(groupId,scheduleId,"ROLLED_BACK"));
                status.setRollbackOnly();
            });
            command.submit(scheduleId,new AvailabilityReqDTO.Submit(List.of(SLOT)),memberId);
            String message=listener.messages.poll(5,TimeUnit.SECONDS);
            // then: a rollback event would appear before this committed event if it leaked.
            assertAll(() -> assertNotNull(message), () -> assertTrue(message.contains("RESPONSE_CHANGED")),
                    () -> assertFalse(message.contains("slots")), () -> assertFalse(message.contains("memberId")));
            jdbc.update("update group_member set status='LEFT' where member_id=?",memberId);
            tx.executeWithoutResult(status -> events.publishEvent(
                    new com.moeasy.moeasybe.domain.schedule.realtime.AvailabilityChanged(groupId,scheduleId,"ROSTER_CHANGED")));
            assertEquals(1008,listener.closed.get(5,TimeUnit.SECONDS));
            assertTrue(listener.messages.isEmpty());
        } finally { socket.abort(); }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MEMBERSHIP", "ACCOUNT"})
    @DisplayName("변경 이벤트가 없어도 주기 점검은 탈퇴·계정 정지된 소켓을 종료한다")
    void revalidate_revokedWithoutEvent_closesSocket(String revocation) throws Exception {
        // given
        Listener listener = new Listener();
        var socket = connect(listener);
        String connected = listener.messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(connected);
        assertTrue(connected.contains("CONNECTED"));
        try {
            if (revocation.equals("MEMBERSHIP")) {
                jdbc.update("update group_member set status='LEFT' where member_id=?", memberId);
            } else {
                jdbc.update("update member set status='BANNED' where id=?", memberId);
            }

            // when
            handler.revalidate();

            // then
            assertAll(() -> assertEquals(1008, listener.closed.get(5, TimeUnit.SECONDS)),
                    () -> assertTrue(listener.messages.isEmpty()));
        } finally {
            socket.abort();
        }
    }

    @Test @DisplayName("동일 Redis 채널의 두 노드가 커밋된 변경을 모두 수신한다")
    void relay_twoRedisListeners_deliversToBothNodes() throws Exception {
        // given
        CountDownLatch deliveries=new CountDownLatch(2);
        var first=node(deliveries); var second=node(deliveries);
        first.start(); second.start();
        try {
            // when
            tx.executeWithoutResult(status -> events.publishEvent(
                    new com.moeasy.moeasybe.domain.schedule.realtime.AvailabilityChanged(groupId,scheduleId,"ROSTER_CHANGED")));
            // then
            assertTrue(deliveries.await(5,TimeUnit.SECONDS));
        } finally { first.destroy(); second.destroy(); }
    }
    private org.springframework.data.redis.listener.RedisMessageListenerContainer node(CountDownLatch received) {
        var node=new org.springframework.data.redis.listener.RedisMessageListenerContainer(); node.setConnectionFactory(connection);
        node.addMessageListener((message,pattern) -> {
            String text=new String(message.getBody(),java.nio.charset.StandardCharsets.UTF_8);
            if(text.equals(groupId+":"+scheduleId+":ROSTER_CHANGED")) received.countDown();
        },new org.springframework.data.redis.listener.ChannelTopic(com.moeasy.moeasybe.domain.schedule.realtime.AvailabilityRealtimeConfig.CHANNEL));
        node.afterPropertiesSet(); return node;
    }
    private String cookie() { return "moeasy_access_token="+jwt.issueAccessToken(memberId).value(); }
    private java.net.http.WebSocket connect(Listener listener) {
        return client.newWebSocketBuilder().header("Origin",cors.allowedOrigins().getFirst()).header("Cookie",cookie())
                .buildAsync(java.net.URI.create("ws://localhost:"+port+"/ws/schedules/"+scheduleId+"/availability"),listener).join();
    }
    private AvailabilityReqDTO.Configure config() { return new AvailabilityReqDTO.Configure(SLOT.toLocalDate(),3,9,12,SLOT.minusDays(1)); }
    private static class Listener implements java.net.http.WebSocket.Listener {
        final BlockingQueue<String> messages=new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed=new CompletableFuture<>();
        private final StringBuilder text=new StringBuilder();
        @Override public void onOpen(java.net.http.WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(java.net.http.WebSocket socket,CharSequence data,boolean last) {
            text.append(data); if(last) { messages.add(text.toString()); text.setLength(0); } socket.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(java.net.http.WebSocket socket,int status,String reason) { closed.complete(status); return null; }
        @Override public void onError(java.net.http.WebSocket socket,Throwable error) { closed.completeExceptionally(error); }
    }
}

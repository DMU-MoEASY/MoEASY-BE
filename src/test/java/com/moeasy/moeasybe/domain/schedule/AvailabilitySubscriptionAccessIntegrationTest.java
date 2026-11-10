package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;

import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.schedule.entity.Schedule;
import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilitySubscriptionAccess;
import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilitySubscriptionAccess.Subscription;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AvailabilitySubscriptionAccessIntegrationTest {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired AvailabilitySubscriptionAccess access;
    private Group group;
    private Member member;
    private Schedule schedule;
    private static final LocalDateTime START = LocalDateTime.of(2099, 1, 10, 9, 0);

    @BeforeEach
    void setUp() {
        group = group();
        member = member(group);
        schedule = schedule(group);
    }

    @Test
    @DisplayName("같은 일정의 구성원도 요청한 일정·회원 조합만 반환한다")
    void findAuthorized_multipleMembers_returnsOnlyRequestedPairs() {
        // given
        var otherMember = member(group);
        var otherSchedule = schedule(group);
        var requested = Set.of(subscription(schedule, member), subscription(otherSchedule, otherMember));
        em.flush();
        em.clear();
        // when
        var authorized = access.findAuthorized(requested);
        // then
        assertEquals(requested, authorized);
    }

    @Test
    @DisplayName("다른 모임의 구성원과 없는 일정·회원 조합은 허용하지 않는다")
    void findAuthorized_unrelatedMembership_excludesUnauthorizedPairs() {
        // given
        var otherGroup = group();
        var otherMember = member(otherGroup);
        var otherSchedule = schedule(otherGroup);
        var allowed = subscription(schedule, member);
        var otherAllowed = subscription(otherSchedule, otherMember);
        var requested = Set.of(allowed, otherAllowed, subscription(schedule, otherMember),
                subscription(otherSchedule, member), new Subscription(Long.MAX_VALUE, member.getId()),
                new Subscription(schedule.getId(), Long.MAX_VALUE));
        em.flush();
        em.clear();
        // when
        var authorized = access.findAuthorized(requested);
        // then
        assertEquals(Set.of(allowed, otherAllowed), authorized);
    }

    @ParameterizedTest
    @ValueSource(strings = {"banned", "deletedAccount", "softDeletedAccount", "left", "expelled",
            "softDeletedMembership", "closedGroup", "deletedGroup", "deletedSchedule"})
    @DisplayName("계정·구성원·모임·일정의 접근 자격 상실을 모두 차단한다")
    void findAuthorized_revokedAccess_returnsEmpty(String reason) {
        // given
        em.flush();
        switch (reason) {
            case "banned" -> jdbc.update("update member set status='BANNED' where id=?", member.getId());
            case "deletedAccount" -> jdbc.update("update member set status='DELETED' where id=?", member.getId());
            case "softDeletedAccount" -> jdbc.update("update member set deleted_at=? where id=?", START, member.getId());
            case "left" -> jdbc.update("update group_member set status='LEFT' where member_id=?", member.getId());
            case "expelled" -> jdbc.update("update group_member set status='KICKED' where member_id=?", member.getId());
            case "softDeletedMembership" -> jdbc.update("update group_member set deleted_at=? where member_id=?", START, member.getId());
            case "closedGroup" -> jdbc.update("update member_group set status='CLOSED' where id=?", group.getId());
            case "deletedGroup" -> jdbc.update("update member_group set deleted_at=? where id=?", START, group.getId());
            case "deletedSchedule" -> jdbc.update("update schedule set deleted_at=? where id=?", START, schedule.getId());
            default -> throw new AssertionError(reason);
        }
        em.clear();
        // when
        var authorized = access.findAuthorized(Set.of(subscription(schedule, member)));
        // then
        assertTrue(authorized.isEmpty());
    }

    @Test
    @DisplayName("보관 모임과 취소 일정은 기존 읽기 전용 구독 권한을 유지한다")
    void findAuthorized_archivedGroupAndCancelledSchedule_preservesReadAccess() {
        // given
        em.flush();
        jdbc.update("update member_group set status='ARCHIVED' where id=?", group.getId());
        jdbc.update("update schedule set status='CANCELLED' where id=?", schedule.getId());
        em.clear();
        var subscription = subscription(schedule, member);
        // when
        var authorized = access.findAuthorized(Set.of(subscription));
        // then
        assertEquals(Set.of(subscription), authorized);
    }

    @Test
    @DisplayName("다음 검사에서는 직전에 허용한 계정의 정지 상태를 새로 읽는다")
    void findAuthorized_accountBannedAfterFirstCheck_doesNotReusePermission() {
        // given
        em.flush();
        em.clear();
        var requested = Set.of(subscription(schedule, member));
        var before = access.findAuthorized(requested);
        jdbc.update("update member set status='BANNED' where id=?", member.getId());
        // when
        var after = access.findAuthorized(requested);
        // then
        assertAll(() -> assertEquals(requested, before), () -> assertTrue(after.isEmpty()));
    }

    @Test
    @DisplayName("257개 구독 권한을 엔티티 추가 조회 없이 3개 묶음 쿼리로 확인한다")
    void findAuthorized_largeBatch_boundsQueriesWithoutLazyLoading() {
        // given
        Set<Subscription> requested = new HashSet<>();
        requested.add(subscription(schedule, member));
        for (int i = 0; i < 256; i++) requested.add(subscription(schedule(group), member));
        em.flush();
        em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        long before = statistics.getPrepareStatementCount();
        try {
            // when
            var authorized = access.findAuthorized(requested);
            long statements = statistics.getPrepareStatementCount() - before;
            // then
            assertAll(() -> assertEquals(requested, authorized), () -> assertEquals(3, statements));
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    @Test
    @DisplayName("빈 구독 목록의 허용 결과는 비어 있다")
    void findAuthorized_emptySubscriptions_returnsEmpty() {
        // given
        Set<Subscription> requested = Set.of();
        // when
        var authorized = access.findAuthorized(requested);
        // then
        assertTrue(authorized.isEmpty());
    }

    private Group group() {
        Category category = em.createQuery("select c from Category c where c.code='RUNNING'", Category.class)
                .getSingleResult();
        var group = Group.builder().category(category).name("구독 권한").description("설명")
                .regionCode("11680").placeName("장소").address("주소").maxMembers(10)
                .joinPolicy(GroupJoinPolicy.APPROVAL).build();
        em.persist(group);
        return group;
    }

    private Member member(Group group) {
        var member = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString())
                .nickname("구독" + UUID.randomUUID()).build();
        em.persist(member);
        em.persist(GroupMember.builder().group(group).member(member).role(GroupMemberRole.MEMBER)
                .status(GroupMemberStatus.ACTIVE).joinedAt(START.minusDays(1)).build());
        return member;
    }

    private Schedule schedule(Group group) {
        var schedule = Schedule.builder().group(group).title("구독 일정").description("설명")
                .startsAt(START).endsAt(START.plusHours(1)).locationName("장소").locationAddress("주소").build();
        em.persist(schedule);
        return schedule;
    }

    private Subscription subscription(Schedule schedule, Member member) {
        return new Subscription(schedule.getId(), member.getId());
    }
}

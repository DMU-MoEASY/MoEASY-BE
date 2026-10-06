package com.moeasy.moeasybe.schema;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.entity.MemberCategory;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.region.entity.Region;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OnboardingSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("지역 기준 데이터는 유효한 선택 지역만 포함한다")
    void regionSeed_containsSelectableCurrentRegions() {
        // given
        List<String> representativeCodes = List.of("11680", "41280", "36110", "50110", "12110");

        // when
        Integer regionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM region WHERE deleted_at IS NULL", Integer.class);
        List<String> codes = jdbcTemplate.queryForList("SELECT code FROM region", String.class);
        String province = jdbcTemplate.queryForObject(
                "SELECT province_name FROM region WHERE code = '12110'", String.class);

        // then
        assertAll(
                () -> assertEquals(230, regionCount),
                () -> assertTrue(codes.containsAll(representativeCodes)),
                () -> assertFalse(codes.contains("41281"), "고양시 아래 덕양구는 별도 선택하지 않는다"),
                () -> assertFalse(codes.contains("11000"), "서울 전체는 구와 함께 선택지에 넣지 않는다"),
                () -> assertFalse(codes.contains("29110"), "원본에서 폐지된 코드는 제외한다"),
                () -> assertEquals("전남광주통합특별시", province)
        );
    }

    @Test
    @DisplayName("관심사 기준 데이터의 영문 코드와 한글 표시명이 등록된다")
    void categorySeed_containsOnboardingInterests() {
        // given
        List<String> expectedCodes = List.of(
                "RUNNING", "HIKING", "STUDY", "READING", "PHOTOGRAPHY",
                "FOOD", "BOARD_GAME", "TRAVEL", "VOLUNTEERING", "DEVELOPMENT");

        // when
        List<String> codes = jdbcTemplate.queryForList("SELECT code FROM category", String.class);
        String runningName = jdbcTemplate.queryForObject(
                "SELECT name FROM category WHERE code = 'RUNNING'", String.class);

        // then
        assertAll(
                () -> assertTrue(codes.containsAll(expectedCodes)),
                () -> assertEquals("러닝", runningName)
        );
    }

    @Test
    @DisplayName("온보딩 전 회원은 지역 없이 저장되고 회원의 지역과 관심사 연관관계가 조회된다")
    void memberRelations_persistAndLoadWithAuditing() {
        // given
        Region region = entityManager.createQuery("SELECT r FROM Region r WHERE r.code = :code", Region.class)
                .setParameter("code", "11680").getSingleResult();
        Category category = entityManager.createQuery("SELECT c FROM Category c WHERE c.code = :code", Category.class)
                .setParameter("code", "RUNNING").getSingleResult();
        Member member = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build();
        entityManager.persist(member);
        entityManager.flush();
        Long memberId = member.getId();
        boolean initiallyNull = member.getPrimaryRegion() == null;
        jdbcTemplate.update("UPDATE member SET primary_region_id = ? WHERE id = ?", region.getId(), memberId);
        MemberCategory interest = MemberCategory.builder().member(member).category(category).build();

        // when
        entityManager.persist(interest);
        entityManager.flush();
        Long interestId = interest.getId();
        entityManager.clear();
        Member reloadedMember = entityManager.find(Member.class, memberId);
        MemberCategory reloadedInterest = entityManager.find(MemberCategory.class, interestId);

        // then
        assertAll(
                () -> assertTrue(initiallyNull),
                () -> assertEquals("11680", reloadedMember.getPrimaryRegion().getCode()),
                () -> assertFalse(reloadedMember.isOnboardingCompleted()),
                () -> assertEquals(memberId, reloadedInterest.getMember().getId()),
                () -> assertEquals("RUNNING", reloadedInterest.getCategory().getCode()),
                () -> assertNotNull(reloadedInterest.getCreatedAt()),
                () -> assertNotNull(reloadedInterest.getCategory().getCreatedAt())
        );
    }

    @Test
    @DisplayName("존재하지 않는 주 활동 지역은 DB 외래키로 거절된다")
    void memberRegion_unknownRegion_rejectedByForeignKey() {
        // given
        Member member = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build();
        entityManager.persist(member);
        entityManager.flush();

        // when
        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("UPDATE member SET primary_region_id = -1 WHERE id = ?", member.getId()));

        // then
        assertTrue(exception.getMostSpecificCause().getMessage().contains("fk_member_primary_region"));
    }

    @Test
    @DisplayName("같은 관심사를 한 회원에게 두 번 저장할 수 없다")
    void memberCategory_duplicateInterest_rejectedByUniqueConstraint() {
        // given
        Member member = Member.builder().socialType(SocialType.KAKAO).socialId(UUID.randomUUID().toString()).build();
        entityManager.persist(member);
        entityManager.flush();
        Long categoryId = jdbcTemplate.queryForObject("SELECT id FROM category WHERE code = 'RUNNING'", Long.class);
        jdbcTemplate.update("INSERT INTO member_category (member_id, category_id) VALUES (?, ?)", member.getId(), categoryId);

        // when
        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO member_category (member_id, category_id) VALUES (?, ?)",
                        member.getId(), categoryId));

        // then
        assertTrue(exception.getMostSpecificCause().getMessage().contains("uq_member_category"));
    }

    @Test
    @DisplayName("지역과 관심사는 BaseEntity의 감사 시각과 소프트 삭제 시각을 저장한다")
    void referenceEntities_inheritedAuditAndDeletedAt_persistAndLoad() {
        // given
        Region region = Region.builder().code("99999").name("테스트 지역").provinceName("테스트 시도").build();
        Category category = Category.builder().code("TEST_" + UUID.randomUUID().toString().substring(0, 16))
                .name("테스트 관심사 " + UUID.randomUUID()).build();
        LocalDateTime deletedAt = LocalDateTime.of(2026, 9, 30, 12, 0);
        entityManager.persist(region);
        entityManager.persist(category);
        entityManager.flush();
        Long regionId = region.getId();
        Long categoryId = category.getId();

        // when
        // 저장과 조회에 동일한 JPA 날짜 매핑을 사용한다.
        entityManager.createQuery("update Region r set r.deletedAt = :deletedAt where r.id = :id")
                .setParameter("deletedAt", deletedAt)
                .setParameter("id", regionId)
                .executeUpdate();
        entityManager.createQuery("update Category c set c.deletedAt = :deletedAt where c.id = :id")
                .setParameter("deletedAt", deletedAt)
                .setParameter("id", categoryId)
                .executeUpdate();
        entityManager.clear();
        Region reloadedRegion = entityManager.find(Region.class, regionId);
        Category reloadedCategory = entityManager.find(Category.class, categoryId);

        // then
        assertAll(
                () -> assertNotNull(reloadedRegion.getCreatedAt()),
                () -> assertNotNull(reloadedRegion.getUpdatedAt()),
                () -> assertEquals(deletedAt, reloadedRegion.getDeletedAt()),
                () -> assertNotNull(reloadedCategory.getCreatedAt()),
                () -> assertNotNull(reloadedCategory.getUpdatedAt()),
                () -> assertEquals(deletedAt, reloadedCategory.getDeletedAt())
        );
    }

    @Test
    @DisplayName("동일한 닉네임의 회원은 DB 유니크 제약으로 거절된다")
    void memberNickname_duplicate_rejectedByUniqueConstraint() {
        // given
        String nickname = "test_" + UUID.randomUUID();
        Member member = Member.builder().socialType(SocialType.KAKAO)
                .socialId(UUID.randomUUID().toString()).nickname(nickname).build();
        entityManager.persist(member);
        entityManager.flush();

        // when
        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO member (social_type, social_id, nickname) VALUES (?, ?, ?)",
                        "KAKAO", UUID.randomUUID().toString(), nickname));

        // then
        assertTrue(exception.getMostSpecificCause().getMessage().contains("uq_member_nickname"));
    }

}

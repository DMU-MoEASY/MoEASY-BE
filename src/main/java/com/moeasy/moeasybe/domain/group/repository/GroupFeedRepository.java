package com.moeasy.moeasybe.domain.group.repository;

import com.moeasy.moeasybe.domain.group.service.support.GroupFeedCursor.Position;
import com.moeasy.moeasybe.domain.group.service.support.GroupFeedFilter;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GroupFeedRepository {
    private final NamedParameterJdbcTemplate db;

    public record Candidate(long id, String name, String description, String categoryCode, String categoryName,
            String regionCode, String regionName, String coverImageKey, long currentMembers, int maxMembers,
            String tier, boolean interest, boolean region, boolean recent, int score, LocalDateTime createdAt) {
        public Position position() { return new Position(score, createdAt, id); }
    }

    private static final String BASE = """
            SELECT g.*,c.code category_code,c.name category_name,
                   CONCAT_WS(' ',r.province_name,r.name) region_name,COALESCE(mc.current_members,0) current_members,
                   (g.category_id IN (:interests)) interest_match,
                   (g.region_code=:primaryRegion) region_match,
                   EXISTS(SELECT 1 FROM schedule s WHERE s.group_id=g.id AND s.deleted_at IS NULL
                          AND s.status='SCHEDULED' AND s.ends_at>:since AND s.ends_at<=:at) recent_match
            FROM member_group g JOIN category c ON c.id=g.category_id
            LEFT JOIN region r ON r.code=g.region_code
            LEFT JOIN (SELECT gm.group_id,COUNT(*) current_members FROM group_member gm WHERE
            """ + GroupMembershipSql.ACTIVE + """
             GROUP BY gm.group_id) mc ON mc.group_id=g.id
            WHERE g.deleted_at IS NULL AND g.status='ACTIVE' AND COALESCE(mc.current_members,0)<g.max_members
            AND NOT EXISTS(SELECT 1 FROM group_member me WHERE me.group_id=g.id AND me.member_id=:member
                           AND (me.status='KICKED' OR (me.status='ACTIVE' AND me.deleted_at IS NULL)))
            AND NOT EXISTS(SELECT 1 FROM group_join_request j WHERE j.group_id=g.id AND j.member_id=:member
                           AND j.status='PENDING')
            """;

    public long count(long member, List<Long> interests, String primaryRegion, GroupFeedFilter filter, LocalDateTime at) {
        return db.queryForObject("SELECT COUNT(*) FROM (" + base(filter) + ") eligible",
                parameters(member, interests, primaryRegion, filter, at), Long.class);
    }

    public List<Candidate> find(long member, List<Long> interests, String primaryRegion, GroupFeedFilter filter,
            LocalDateTime at, boolean normal, Position after, int limit) {
        MapSqlParameterSource params = parameters(member, interests, primaryRegion, filter, at)
                .addValue("limit", limit);
        boolean noPreferences = interests.isEmpty() && primaryRegion == null;
        String classification = noPreferences ? (normal ? "1=1" : "1=0")
                : (normal ? "(interest_match OR region_match)" : "(NOT interest_match AND NOT region_match)");
        String keyset = "";
        if (after != null) {
            keyset = " AND (score<:score OR (score=:score AND created_at<:created)"
                    + " OR (score=:score AND created_at=:created AND id<:id))";
            params.addValue("score", after.score()).addValue("created", after.createdAt()).addValue("id", after.id());
        }
        String sql = "SELECT * FROM (SELECT eligible.*,50*interest_match+30*region_match"
                + "+CASE tier WHEN 'silver' THEN 5 WHEN 'gold' THEN 10 ELSE 0 END+10*recent_match score"
                + " FROM (" + base(filter) + ") eligible) scored WHERE " + classification + keyset
                + " ORDER BY score DESC,created_at DESC,id DESC LIMIT :limit";
        return db.query(sql, params, (rs, i) -> new Candidate(rs.getLong("id"), rs.getString("name"),
                rs.getString("description"), rs.getString("category_code"), rs.getString("category_name"),
                rs.getString("region_code"), rs.getString("region_name"), rs.getString("cover_image_key"),
                rs.getLong("current_members"), rs.getInt("max_members"), rs.getString("tier"),
                rs.getBoolean("interest_match"), rs.getBoolean("region_match"), rs.getBoolean("recent_match"),
                rs.getInt("score"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    private String base(GroupFeedFilter filter) {
        String sql = BASE;
        if (!filter.categoryCodes().isEmpty()) sql += " AND c.code IN (:categories)";
        if (!filter.regionCode().isEmpty()) sql += " AND g.region_code=:region";
        if (!filter.query().isEmpty()) sql += """
                 AND (LOWER(g.name) LIKE :query ESCAPE '!'
                   OR LOWER(g.description) LIKE :query ESCAPE '!'
                   OR LOWER(c.name) LIKE :query ESCAPE '!'
                   OR LOWER(CONCAT_WS(' ',r.province_name,r.name)) LIKE :query ESCAPE '!')
                """;
        return sql;
    }

    private MapSqlParameterSource parameters(long member, List<Long> interests, String primaryRegion,
            GroupFeedFilter filter, LocalDateTime at) {
        String escaped = filter.query().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return new MapSqlParameterSource().addValue("member", member)
                .addValue("interests", interests.isEmpty() ? List.of(-1L) : interests)
                .addValue("primaryRegion", primaryRegion == null ? "" : primaryRegion)
                .addValue("since", at.minusDays(30)).addValue("at", at)
                .addValue("categories", filter.categoryCodes()).addValue("region", filter.regionCode())
                .addValue("query", "%" + escaped + "%");
    }
}

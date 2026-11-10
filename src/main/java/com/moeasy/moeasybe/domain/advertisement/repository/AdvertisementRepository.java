package com.moeasy.moeasybe.domain.advertisement.repository;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdvertisementRepository {
    private final JdbcTemplate db;

    public record Banner(long id, String advertiserName, String title, String imageUrl, String landingUrl) {}

    public List<Banner> findEligible(LocalDateTime at) {
        return db.query("""
                SELECT id,advertiser_name,title,image_url,landing_url FROM feed_advertisement
                WHERE active=TRUE AND starts_at<=? AND ends_at>? ORDER BY id
                """, (rs, i) -> new Banner(rs.getLong("id"), rs.getString("advertiser_name"),
                rs.getString("title"), rs.getString("image_url"), rs.getString("landing_url")), at, at);
    }
}

-- #38: 모임과 독립된 배너 소재. 기간은 [starts_at, ends_at).
CREATE TABLE feed_advertisement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    advertiser_name VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    landing_url VARCHAR(1000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_feed_ad_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_feed_ad_https CHECK (landing_url REGEXP '^https://[^/?#[:space:]]+([/?#][^[:space:]]*)?$'),
    INDEX ix_feed_ad_period (active, starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

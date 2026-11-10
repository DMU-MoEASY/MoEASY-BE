-- #38: 기존 HTTPS 스킴 검사에 호스트·포트 문법 검사를 추가한다.
-- 이미 적용된 생성 마이그레이션은 보존한다. 잘못된 기존 소재가 있으면 적용을 중단하며
-- 소재를 임의로 수정·삭제하지 않는다. 국제화 도메인은 ASCII punycode로 저장한다.

ALTER TABLE feed_advertisement
    ADD CONSTRAINT ck_feed_ad_https_host CHECK (
        landing_url REGEXP CONCAT(
            '^https://(',
            '[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?',
            '([.][a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*[.]?',
            '|\\[[0-9a-f:.]+\\])',
            '(:([0-9]{1,4}|[0-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5]))?',
            '([/?#][^[:space:][:cntrl:]]*)?$'
        )
        AND LOCATE(CHAR(92), landing_url) = 0
        AND (
            landing_url NOT REGEXP '^https://\\['
            OR IS_IPV6(SUBSTRING_INDEX(SUBSTRING(landing_url, 10), ']', 1))
        )
        AND (
            landing_url NOT REGEXP '^https://[0-9.]+([:/?#]|$)'
            OR IS_IPV4(REGEXP_SUBSTR(SUBSTRING(landing_url, 9), '^[^:/?#]+'))
        )
    );

package com.moeasy.moeasybe.domain.group.support;

import java.util.UUID;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 피드 테스트 컨텍스트마다 빈 DB를 만들고 커넥션 풀이 닫힌 뒤 해당 DB만 삭제한다. */
public final class GroupFeedTestDatabase implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String url = context.getEnvironment().getRequiredProperty("spring.datasource.url");
        String username = System.getenv().getOrDefault("MIGRATION_TEST_DB_USERNAME", "root");
        String password = System.getenv().getOrDefault("MIGRATION_TEST_DB_PASSWORD", "root1234");
        String schema = "group_feed_test_" + UUID.randomUUID().toString().replace("-", "");
        String isolatedUrl = url.replaceFirst("(jdbc:mysql://[^/]+/)[^?]*", "$1" + schema);
        if (isolatedUrl.equals(url)) {
            throw new IllegalArgumentException("jdbc:mysql:// 형식의 테스트 DB_URL이 필요합니다.");
        }
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        admin.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

        DefaultListableBeanFactory beans = (DefaultListableBeanFactory) context.getBeanFactory();
        beans.registerSingleton("groupFeedTestDatabase", this);
        beans.registerDisposableBean("groupFeedTestDatabase", () -> admin.execute("DROP DATABASE `" + schema + "`"));
        beans.registerDependentBean("groupFeedTestDatabase", "dataSource");
        TestPropertyValues.of(
                "spring.datasource.url=" + isolatedUrl,
                "spring.datasource.username=" + username,
                "spring.datasource.password=" + password,
                "spring.flyway.locations=classpath:db/migration"
        ).applyTo(context);
    }
}

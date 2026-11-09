package com.moeasy.moeasybe.domain.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.domain.bank.config.BankProperties;
import com.moeasy.moeasybe.domain.bank.exception.BankException;
import com.moeasy.moeasybe.domain.bank.infra.BankSyncRateLimiter;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import com.moeasy.moeasybe.domain.group.service.command.GroupCommandService;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import com.sun.net.httpserver.HttpServer;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@AutoConfigureMockMvc
class BankApiIntegrationTest {
    private static final HttpServer BANK = bank();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static volatile CountDownLatch entered;
    private static volatile CountDownLatch release;
    @Autowired private BankSyncRateLimiter limits;
    @Autowired private BankProperties settings;
    @Autowired private StringRedisTemplate redis;
    private static final String CSRF = "bank-test-csrf";
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokens;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GroupCommandService groups;
    private final List<Long> groupIds = new ArrayList<>(),
            memberIds = new ArrayList<>(),
            categoryIds = new ArrayList<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "bank-api.base-url",
                () ->
                        System.getenv()
                                .getOrDefault(
                                        "BANK_SIMULATION_URL",
                                        "http://localhost:" + BANK.getAddress().getPort()));
        registry.add("bank-api.rate-limit-prefix", () -> "bank:test:" + UUID.randomUUID());
        registry.add(
                "bank-api.encryption-key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    @Test
    @DisplayName("암호화 연결·실제 HTTP 동기화·일반 회원 조회·중복 제거·해제 후 키 제거")
    void account_fullLifecycle_preservesLedgerAndSecrets() throws Exception {
        // given
        Long owner = member(), group = group(owner), viewer = member();
        membership(group, viewer, "MEMBER");
        var connected = mvc.perform(auth(post(path(group)), owner).content(connectBody("NH")));
        connected
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.accountNumberMasked").value("*******6789"))
                .andExpect(jsonPath("$.result.apiKey").doesNotExist())
                .andExpect(jsonPath("$.result.accountNumber").doesNotExist());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT encrypted_account_number FROM group_bank_account WHERE"
                                        + " group_id=?",
                                String.class,
                                group))
                .doesNotContain("123456");
        // when
        var synced =
                mvc.perform(auth(post(path(group) + "/sync"), owner).content(syncBody("1111")));
        // then
        synced.andExpect(status().isOk()).andExpect(jsonPath("$.result.insertedCount").value(2));
        mvc.perform(get(path(group) + "/transactions").cookie(token(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(2))
                .andExpect(jsonPath("$.result.items[0].balance").value(-100));
        mvc.perform(get(path(group)).cookie(token(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.lastSyncedAt").isNotEmpty());
        int before = CALLS.get();
        mvc.perform(auth(post(path(group) + "/sync"), owner).content(syncBody("1111")))
                .andExpect(status().isTooManyRequests());
        assertThat(CALLS.get()).isEqualTo(before);
        allowRetry(group);
        mvc.perform(auth(post(path(group) + "/sync"), owner).content(syncBody("1111")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.insertedCount").value(0));
        mvc.perform(auth(delete(path(group)), owner)).andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT encrypted_api_key FROM bank_api_credential WHERE"
                                        + " member_id=?",
                                String.class,
                                owner))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM account_transaction WHERE"
                                    + " group_bank_account_id IN (SELECT id FROM group_bank_account"
                                    + " WHERE group_id=?)",
                                Long.class,
                                group))
                .isEqualTo(2);
        mvc.perform(auth(post(path(group)), owner).content(connectBody("NH")))
                .andExpect(status().isCreated());
        mvc.perform(get(path(group) + "/transactions").cookie(token(viewer)))
                .andExpect(jsonPath("$.result.totalElements").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2222", "3333", "4444", "5555"})
    @DisplayName("은행 429·200 실패·비정상 금액·500에서는 기존 거래와 마지막 성공 시각을 보존한다")
    void sync_bankFailure_preservesStoredHistory(String password) throws Exception {
        // given
        Long owner = member(), group = group(owner);
        mvc.perform(auth(post(path(group)), owner).content(connectBody("NH")))
                .andExpect(status().isCreated());
        mvc.perform(auth(post(path(group) + "/sync"), owner).content(syncBody("1111")))
                .andExpect(status().isOk());
        String last =
                jdbc.queryForObject(
                        "SELECT CAST(last_synced_at AS CHAR) FROM group_bank_account WHERE"
                                + " group_id=?",
                        String.class,
                        group);
        allowRetry(group);
        // when
        var result =
                mvc.perform(auth(post(path(group) + "/sync"), owner).content(syncBody(password)));
        // then
        int expected = password.equals("2222") ? 429 : password.equals("3333") ? 422 : 502;
        result.andExpect(status().is(expected));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT CAST(last_synced_at AS CHAR) FROM group_bank_account WHERE"
                                        + " group_id=?",
                                String.class,
                                group))
                .isEqualTo(last);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM account_transaction WHERE"
                                    + " group_bank_account_id IN (SELECT id FROM group_bank_account"
                                    + " WHERE group_id=?)",
                                Long.class,
                                group))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT sync_token FROM group_bank_account WHERE group_id=?",
                                String.class,
                                group))
                .isNull();
    }

    @Test
    @DisplayName("신한 로그인 응답의 계좌별 성공과 슬래시 날짜를 처리한다")
    void sync_shinhanLogin_supported() throws Exception {
        // given
        Long owner = member(), group = group(owner);
        mvc.perform(auth(post(path(group)), owner).content(connectBody("SH")))
                .andExpect(status().isCreated());
        // when
        var result =
                mvc.perform(auth(post(path(group) + "/sync"), owner).content(syncBody("1111")));
        // then
        result.andExpect(status().isOk()).andExpect(jsonPath("$.result.insertedCount").value(2));
    }

    @Test
    @DisplayName("일반 회원은 조회만 가능하고 운영진 쓰기는 CSRF를 요구하며 외부 회원은 접근할 수 없다")
    void access_readForMembers_writeForManagers() throws Exception {
        // given
        Long owner = member(),
                group = group(owner),
                viewer = member(),
                outsider = member(),
                manager = member();
        membership(group, viewer, "MEMBER");
        membership(group, manager, "MANAGER");
        // when
        var noCsrf =
                mvc.perform(
                        post(path(group))
                                .cookie(token(owner))
                                .contentType("application/json")
                                .content(connectBody("NH")));
        var notManager = mvc.perform(auth(post(path(group)), viewer).content(connectBody("NH")));
        var anonymous = mvc.perform(get(path(group)));
        // then
        noCsrf.andExpect(status().isForbidden());
        notManager.andExpect(status().isForbidden());
        anonymous.andExpect(status().isUnauthorized());
        mvc.perform(auth(post(path(group)), manager).content(connectBody("NH")))
                .andExpect(status().isCreated());
        mvc.perform(get(path(group) + "/transactions").cookie(token(outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post(path(group) + "/sync"), viewer).content(syncBody("1111")))
                .andExpect(status().isForbidden());
        mvc.perform(auth(delete(path(group)), viewer)).andExpect(status().isForbidden());
        jdbc.update(
                "UPDATE group_member SET status='LEFT' WHERE group_id=? AND member_id=?",
                group,
                viewer);
        mvc.perform(get(path(group) + "/transactions").cookie(token(viewer)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(group) + "/transactions?size=101").cookie(token(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다른 모임 계좌 중복 및 동기화 중 연결 해제를 거부한다")
    void connect_duplicateAndDisconnectDuringSync_rejected() throws Exception {
        // given
        Long owner = member(), first = group(owner), second = group(owner);
        mvc.perform(auth(post(path(first)), owner).content(connectBody("NH")))
                .andExpect(status().isCreated());
        // when
        var duplicate = mvc.perform(auth(post(path(second)), owner).content(connectBody("NH")));
        jdbc.update(
                "UPDATE group_bank_account SET"
                        + " sync_token='in-progress',sync_lease_until=DATE_ADD(NOW(), INTERVAL 1"
                        + " MINUTE) WHERE group_id=?",
                first);
        var inProgress = mvc.perform(auth(delete(path(first)), owner));
        // then
        duplicate.andExpect(status().isConflict());
        inProgress.andExpect(status().isConflict());
    }

    @Test
    @DisplayName("잘못된 입력은 은행을 호출하지 않고 거부한다")
    void sync_invalidInput_doesNotCallBank() throws Exception {
        // given
        Long owner = member(), group = group(owner);
        mvc.perform(auth(post(path(group)), owner).content(connectBody("NH")))
                .andExpect(status().isCreated());
        // when
        var invalidDate =
                mvc.perform(
                        auth(post(path(group) + "/sync"), owner)
                                .content(syncBody("1111").replace("2026-01-10", "0999-01-10")));
        var invalidKey =
                mvc.perform(
                        auth(post(path(group(owner))), owner)
                                .content(
                                        connectBody("NH").replace("local-test-api", "unsafe key")));
        // then
        invalidDate.andExpect(status().isBadRequest());
        invalidKey.andExpect(status().isBadRequest());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                        "$.paths['/api/v1/groups/{groupId}/bank-account/sync'].post.parameters[?(@.name=='X-XSRF-TOKEN')].required")
                                .value(org.hamcrest.Matchers.hasItem(true)));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT last_synced_at FROM group_bank_account WHERE group_id=?",
                                String.class,
                                group))
                .isNull();
    }

    @Test
    @DisplayName("은행 응답 대기 중 행 잠금을 해제하고 병렬 동기화는 거부한다")
    void sync_concurrentRequests_onlyOneClaimsAccount() throws Exception {
        Assumptions.assumeTrue(
                System.getenv("BANK_SIMULATION_URL") == null,
                "Controlled peer latch is exercised by the normal local integration run");
        // given
        Long owner = member(), group = group(owner);
        mvc.perform(auth(post(path(group)), owner).content(connectBody("NH")))
                .andExpect(status().isCreated());
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first =
                    executor.submit(
                            () ->
                                    mvc.perform(
                                            auth(post(path(group) + "/sync"), owner)
                                                    .content(syncBody("6666"))));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                // when: an independent DB connection can acquire the same row while HTTP waits.
                jdbc.execute(
                        (ConnectionCallback<Void>)
                                connection -> {
                                    try (var statement = connection.createStatement()) {
                                        statement.execute("SET SESSION innodb_lock_wait_timeout=1");
                                        try (var rows =
                                                statement.executeQuery(
                                                        "SELECT id FROM group_bank_account WHERE"
                                                                + " group_id="
                                                                + group
                                                                + " FOR UPDATE")) {
                                            assertThat(rows.next()).isTrue();
                                        } finally {
                                            statement.execute(
                                                    "SET SESSION innodb_lock_wait_timeout=50");
                                        }
                                    }
                                    return null;
                                });
                var second =
                        mvc.perform(
                                auth(post(path(group) + "/sync"), owner).content(syncBody("1111")));
                // then
                second.andExpect(status().isConflict());
            } finally {
                release.countDown();
            }
            first.get(10, TimeUnit.SECONDS).andExpect(status().isOk());
        } finally {
            entered = null;
            release = null;
        }
    }

    @Test
    @DisplayName("서버 공통 조회 한도 직전의 병렬 요청 중 하나만 허용한다")
    void rateLimit_concurrentThreshold_oneSucceeds() throws Exception {
        // given
        String global = settings.getRateLimitPrefix() + ":global";
        String key = settings.getRateLimitPrefix() + ":key:quota-test";
        redis.delete(List.of(global, key));
        try {
            for (int i = 0; i < 29; i++) limits.acquire("quota-test");
            // when
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Callable<Boolean> acquire =
                        () -> {
                            try {
                                limits.acquire("quota-test");
                                return true;
                            } catch (BankException ex) {
                                assertThat(ex.getCode().getCode()).isEqualTo("BANK429_1");
                                return false;
                            }
                        };
                var attempts = executor.invokeAll(List.of(acquire, acquire));
                // then
                assertThat(
                                attempts.stream()
                                        .filter(
                                                f -> {
                                                    try {
                                                        return f.get();
                                                    } catch (Exception ex) {
                                                        throw new AssertionError(ex);
                                                    }
                                                })
                                        .count())
                        .isEqualTo(1);
            }
        } finally {
            redis.delete(List.of(global, key));
        }
    }

    private static HttpServer bank() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/v1/transactions",
                    exchange -> {
                        CALLS.incrementAndGet();
                        String request =
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8);
                        if (request.contains("6666") && entered != null) {
                            entered.countDown();
                            try {
                                release.await(8, TimeUnit.SECONDS);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        String response;
                        int status = 200;
                        if (!"Bearer local-test-api:local-test-secret"
                                .equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                            status = 401;
                            response = "{\"success\":false,\"code\":\"UNAUTHORIZED\"}";
                        } else if (request.contains("2222")) {
                            status = 429;
                            response =
                                    "{\"success\":false,\"code\":\"ACCOUNT_COOLDOWN\",\"retryAfterSec\":540}";
                        } else if (request.contains("3333"))
                            response = "{\"success\":false,\"code\":\"BANK_REJECTED\"}";
                        else if (request.contains("5555")) {
                            status = 500;
                            response = "unavailable";
                        } else {
                            String row =
                                    "{\"date\":\"2026/01/10\",\"time\":\"09:00:00\",\"type\":\"deposit\",\"amount\":"
                                            + (request.contains("4444") ? "100.5" : "100")
                                            + ",\"balance\":-100,\"description\":\"입금\"}";
                            String data =
                                    "{\"success\":true,\"transactions\":[" + row + "," + row + "]}";
                            response =
                                    exchange.getRequestURI().getPath().endsWith("/login")
                                            ? "{\"success\":true,\"results\":[{\"accountNumber\":\"12345676789\",\"success\":true,\"transactions\":["
                                                    + row
                                                    + ","
                                                    + row
                                                    + "]}]}"
                                            : data;
                        }
                        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(status, bytes.length);
                        exchange.getResponseBody().write(bytes);
                        exchange.close();
                    });
            server.start();
            return server;
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private String connectBody(String bank) {
        return "{\"bankCode\":\""
                + bank
                + "\",\"accountNumber\":\"12345676789\",\"accountHolder\":\"테스트\",\"apiKey\":\"local-test-api\",\"secretKey\":\"local-test-secret\"}";
    }

    private String syncBody(String password) {
        return "{\"startDate\":\"2026-01-10\",\"endDate\":\"2026-01-10\",\"accountPassword\":\""
                + password
                + "\",\"residentNumber\":\"800101\",\"accountType\":\"personal\",\"loginId\":\"test-login\",\"loginPassword\":\"test-password\"}";
    }

    private void allowRetry(Long group) {
        jdbc.update("UPDATE group_bank_account SET next_sync_at=NULL WHERE group_id=?", group);
    }

    private String path(Long group) {
        return "/api/v1/groups/" + group + "/bank-account";
    }

    private Cookie token(Long member) {
        return new Cookie(AuthCookieNames.ACCESS_TOKEN, tokens.issueAccessToken(member).value());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, Long member) {
        return request.cookie(token(member), new Cookie("XSRF-TOKEN", CSRF))
                .header("X-XSRF-TOKEN", CSRF)
                .contentType("application/json");
    }

    private Long member() {
        String suffix = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO member (social_id,social_type,nickname) VALUES (?,'KAKAO',?)",
                suffix,
                suffix);
        Long id =
                jdbc.queryForObject("SELECT id FROM member WHERE social_id=?", Long.class, suffix);
        memberIds.add(id);
        return id;
    }

    private Long group(Long owner) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO category (code,name) VALUES (?,?)", suffix, suffix);
        Long category =
                jdbc.queryForObject("SELECT id FROM category WHERE code=?", Long.class, suffix);
        categoryIds.add(category);
        Long group =
                groups.createGroup(
                                new GroupReqDTO.Create(
                                        "회계 테스트",
                                        "설명",
                                        category,
                                        "11680",
                                        "장소",
                                        "주소",
                                        null,
                                        null,
                                        20,
                                        GroupJoinPolicy.APPROVAL,
                                        null),
                                owner)
                        .groupId();
        groupIds.add(group);
        return group;
    }

    private void membership(Long group, Long member, String role) {
        jdbc.update(
                "INSERT INTO group_member (group_id,member_id,role,status) VALUES (?,?,?,'ACTIVE')",
                group,
                member,
                role);
    }

    @AfterEach
    void cleanup() {
        for (Long group : groupIds) {
            jdbc.update(
                    "DELETE FROM account_transaction WHERE group_bank_account_id IN (SELECT id FROM"
                            + " group_bank_account WHERE group_id=?)",
                    group);
            jdbc.update("DELETE FROM group_bank_account WHERE group_id=?", group);
            jdbc.update("DELETE FROM group_member WHERE group_id=?", group);
            jdbc.update("DELETE FROM member_group WHERE id=?", group);
        }
        for (Long member : memberIds) {
            jdbc.update("DELETE FROM bank_api_credential WHERE member_id=?", member);
            jdbc.update("DELETE FROM member WHERE id=?", member);
        }
        for (Long category : categoryIds) jdbc.update("DELETE FROM category WHERE id=?", category);
    }

    @AfterAll
    static void stop() {
        BANK.stop(0);
    }
}

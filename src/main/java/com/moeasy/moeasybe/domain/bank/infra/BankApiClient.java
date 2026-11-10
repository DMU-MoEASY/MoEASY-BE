package com.moeasy.moeasybe.domain.bank.infra;

import com.moeasy.moeasybe.domain.bank.config.BankProperties;
import com.moeasy.moeasybe.domain.bank.dto.request.BankReqDTO;
import com.moeasy.moeasybe.domain.bank.enums.BankCode;
import com.moeasy.moeasybe.domain.bank.enums.TransactionType;
import com.moeasy.moeasybe.domain.bank.exception.BankException;
import com.moeasy.moeasybe.domain.bank.exception.code.BankErrorCode;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class BankApiClient {
    private final RestClient client;
    private final ObjectMapper mapper;

    public BankApiClient(BankProperties properties, ObjectMapper mapper) {
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .followRedirects(HttpClient.Redirect.NEVER)
                                .build());
        factory.setReadTimeout(Duration.ofSeconds(70));
        client =
                RestClient.builder()
                        .baseUrl(properties.getBaseUrl())
                        .requestFactory(factory)
                        .build();
        this.mapper = mapper;
    }

    public List<Transaction> fetch(
            BankCode bank, String account, String apiKey, String secret, BankReqDTO.Sync request) {
        Map<String, Object> body = new HashMap<>();
        body.put("bankCode", bank.name());
        body.put("startDate", request.startDate().format(DateTimeFormatter.BASIC_ISO_DATE));
        body.put("endDate", request.endDate().format(DateTimeFormatter.BASIC_ISO_DATE));
        if (bank == BankCode.SH) {
            body.put("loginId", request.loginId());
            body.put("loginPassword", request.loginPassword());
            body.put(
                    "accounts",
                    List.of(
                            Map.of(
                                    "accountNumber",
                                    account,
                                    "accountPassword",
                                    request.accountPassword())));
        } else {
            body.put("accountNumber", account);
            body.put("accountPassword", request.accountPassword());
            body.put("residentNumber", request.residentNumber());
            body.put("accountType", request.accountType());
        }
        try {
            JsonNode root =
                    client.post()
                            .uri(
                                    bank == BankCode.SH
                                            ? "/v1/transactions/login"
                                            : "/v1/transactions")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey + ":" + secret)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class);
            if (root == null || !root.path("success").asBoolean(false))
                throw failure(root, 0, null);
            if (bank == BankCode.SH) {
                JsonNode results = root.path("results");
                if (!results.isArray()
                        || results.size() != 1
                        || !account.equals(results.get(0).path("accountNumber").asString()))
                    throw new BankException(BankErrorCode.UPSTREAM_FAILED);
                root = results.get(0);
                if (!root.path("success").asBoolean(false)) throw failure(root, 0, null);
            }
            JsonNode nodes = root.path("transactions");
            if (!nodes.isArray() || nodes.size() > 10000)
                throw new BankException(BankErrorCode.UPSTREAM_FAILED);
            List<Transaction> result = new ArrayList<>();
            for (JsonNode node : nodes) {
                LocalDate day = LocalDate.parse(text(node, "date", 10).replace('/', '-'));
                if (day.isBefore(request.startDate()) || day.isAfter(request.endDate()))
                    throw new BankException(BankErrorCode.UPSTREAM_FAILED);
                LocalDateTime at = LocalDateTime.of(day, LocalTime.parse(text(node, "time", 8)));
                if (at.getNano() != 0) throw new BankException(BankErrorCode.UPSTREAM_FAILED);
                long amount = integer(node, "amount");
                if (amount <= 0) throw new BankException(BankErrorCode.UPSTREAM_FAILED);
                TransactionType type =
                        switch (text(node, "type", 10)) {
                            case "deposit" -> TransactionType.DEPOSIT;
                            case "withdrawal" -> TransactionType.WITHDRAWAL;
                            default -> throw new BankException(BankErrorCode.UPSTREAM_FAILED);
                        };
                result.add(
                        new Transaction(
                                type,
                                at,
                                text(node, "description", 100),
                                text(node, "displayName", 100),
                                text(node, "counterparty", 100),
                                amount,
                                integer(node, "balance"),
                                text(node, "branch", 100),
                                text(node, "memo", 255)));
            }
            return result;
        } catch (RestClientResponseException ex) {
            log.warn("은행 HTTP 오류. bank={}, status={}", bank, ex.getStatusCode().value());
            JsonNode error = null;
            try {
                error = mapper.readTree(ex.getResponseBodyAsString());
            } catch (RuntimeException ignored) {
            }
            throw failure(
                    error,
                    ex.getStatusCode().value(),
                    ex.getResponseHeaders() == null
                            ? null
                            : ex.getResponseHeaders().getFirst("Retry-After"));
        } catch (BankException ex) {
            throw ex;
        } catch (RestClientException | DateTimeException ex) {
            // 메시지와 응답 본문은 기록하지 않는다. 예기치 않은 코드 결함은 서비스로 전파한다.
            log.warn(
                    "은행 통신·응답 처리 오류. bank={}, type={}, frames={}",
                    bank,
                    ex.getClass().getName(),
                    Arrays.toString(ex.getStackTrace()));
            throw new BankException(BankErrorCode.UPSTREAM_FAILED);
        }
    }

    private UpstreamException failure(JsonNode node, int status, String retryAfter) {
        String code = node == null ? "" : node.path("code").asString("");
        BankErrorCode error =
                switch (code) {
                    case "ACCOUNT_COOLDOWN", "QUERY_QUOTA_EXCEEDED", "TOO_MANY_REQUESTS" ->
                            BankErrorCode.RATE_LIMITED;
                    case "BANK_REJECTED", "KEYPAD_FAILED", "PIN_INVALID_LENGTH" ->
                            BankErrorCode.BANK_REJECTED;
                    case "UNAUTHORIZED" -> BankErrorCode.INVALID_API_KEY;
                    default ->
                            status == 429
                                    ? BankErrorCode.RATE_LIMITED
                                    : status == 401
                                            ? BankErrorCode.INVALID_API_KEY
                                            : BankErrorCode.UPSTREAM_FAILED;
                };
        // Missing Retry-After on quota failures uses a conservative 30 minute cooldown.
        long wait = error == BankErrorCode.RATE_LIMITED ? 1800 : 300;
        if (node != null && node.path("retryAfterSec").isIntegralNumber())
            wait = Math.max(300, Math.min(86400, node.path("retryAfterSec").asLong(wait)));
        else if (retryAfter != null) {
            try {
                wait = Math.max(300, Math.min(86400, Long.parseLong(retryAfter)));
            } catch (NumberFormatException ignored) {
            }
        }
        return new UpstreamException(error, wait);
    }

    private String text(JsonNode node, String field, int max) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return "";
        if (!value.isString() || value.asString().length() > max)
            throw new BankException(BankErrorCode.UPSTREAM_FAILED);
        return value.asString();
    }

    private long integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong())
            throw new BankException(BankErrorCode.UPSTREAM_FAILED);
        return value.asLong();
    }

    public record Transaction(
            TransactionType type,
            LocalDateTime at,
            String description,
            String displayName,
            String counterparty,
            long amount,
            long balance,
            String branch,
            String memo) {}

    public static class UpstreamException extends BankException {
        private final long retrySeconds;

        public UpstreamException(BankErrorCode code, long retrySeconds) {
            super(code);
            this.retrySeconds = retrySeconds;
        }

        public long retrySeconds() {
            return retrySeconds;
        }
    }
}

package com.moeasy.moeasybe.domain.bank.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moeasy.moeasybe.domain.bank.enums.BankCode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class BankReqDTO {
    private BankReqDTO() {}

    public record Connect(
            @NotNull(message = "은행 코드는 필수입니다.") BankCode bankCode,
            @NotBlank(message = "계좌번호는 필수입니다.")
                    @Pattern(regexp = "[0-9]{8,20}", message = "계좌번호는 숫자 8~20자리여야 합니다.")
                    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                    String accountNumber,
            @NotBlank(message = "예금주명은 필수입니다.") @Size(max = 50, message = "예금주명은 50자 이하여야 합니다.")
                    String accountHolder,
            @NotBlank(message = "API Key는 필수입니다.")
                    @Size(max = 200, message = "API Key는 200자 이하여야 합니다.")
                    @Pattern(regexp = "[!-9;-~]+", message = "API Key에 공백이나 콜론을 사용할 수 없습니다.")
                    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                    String apiKey,
            @NotBlank(message = "Secret Key는 필수입니다.")
                    @Size(max = 200, message = "Secret Key는 200자 이하여야 합니다.")
                    @Pattern(regexp = "[!-9;-~]+", message = "Secret Key에 공백이나 콜론을 사용할 수 없습니다.")
                    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                    String secretKey) {
        @Override
        public String toString() {
            return "Connect[REDACTED]";
        }
    }

    public record Sync(
            @NotNull(message = "시작일은 필수입니다.") LocalDate startDate,
            @NotNull(message = "종료일은 필수입니다.") LocalDate endDate,
            @NotBlank(message = "계좌 비밀번호는 필수입니다.")
                    @Pattern(regexp = "[0-9]{4}", message = "계좌 비밀번호는 숫자 4자리여야 합니다.")
                    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                    String accountPassword,
            @Pattern(
                            regexp = "[0-9]{6}|[0-9]{10}",
                            message = "개인은 생년월일 6자리, 사업자는 사업자번호 10자리를 입력해 주세요.")
                    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                    String residentNumber,
            @Pattern(regexp = "personal|business", message = "계좌 유형은 personal 또는 business입니다.")
                    String accountType,
            @Size(max = 100, message = "로그인 ID는 100자 이하여야 합니다.")
                    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                    String loginId,
            @Size(max = 200, message = "로그인 비밀번호는 200자 이하여야 합니다.")
                    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                    String loginPassword) {
        @Override
        public String toString() {
            return "Sync[REDACTED]";
        }
    }

    public record Search(
            @Min(value = 0, message = "페이지는 0 이상이어야 합니다.") Integer page,
            @Min(value = 1, message = "크기는 1 이상이어야 합니다.")
                    @Max(value = 100, message = "크기는 100 이하여야 합니다.")
                    Integer size) {
        public Search {
            page = page == null ? 0 : page;
            size = size == null ? 20 : size;
        }
    }
}

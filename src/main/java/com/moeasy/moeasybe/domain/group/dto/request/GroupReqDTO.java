package com.moeasy.moeasybe.domain.group.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinPolicy;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;

public final class GroupReqDTO {
    private GroupReqDTO() {
    }

    @Builder
    public record Create(
            @NotBlank @Size(max = 100) String name,
            @NotBlank String description,
            @NotNull @Min(1) Long categoryId,
            @NotBlank @Size(max = 32) String regionCode,
            @NotBlank @Size(max = 100) String placeName,
            @NotBlank @Size(max = 255) String address,
            @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
            @NotNull @Min(1) Integer maxMembers,
            @NotNull GroupJoinPolicy joinPolicy,
            @Size(max = 500) @Pattern(regexp = ".*\\S.*") String coverImageKey
    ) {
    }

    // Jackson keeps absent keys out of this map; explicit null may be a null value or NullNode.
    public record Update(Map<String, JsonNode> fields) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Update {
            fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        public boolean has(String field) {
            return fields.containsKey(field);
        }

        public JsonNode value(String field) {
            return fields.get(field);
        }
    }

    @Builder
    public record Close(
            @NotBlank @Size(max = 255) String reason,
            @NotBlank @Size(max = 100) String confirmationText,
            @NotNull Boolean archiveContent
    ) {
    }
}

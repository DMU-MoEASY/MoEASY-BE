package com.moeasy.moeasybe.domain.schedule.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.moeasy.moeasybe.domain.schedule.dto.deserializer.ScheduleTimeDeserializer;
import com.moeasy.moeasybe.domain.schedule.enums.PlaceProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class ScheduleReqDTO {
    private ScheduleReqDTO() { }

    @Schema(name = "ScheduleCreate", description = "모임 일정 생성 요청")
    public record Create(
            @NotBlank(message = "제목은 필수입니다.") @Size(max = 100, message = "제목은 100자 이하여야 합니다.") String title,
            @NotBlank(message = "설명은 필수입니다.") @Size(max = 500, message = "설명은 500자 이하여야 합니다.") String description,
            @NotNull(message = "시작 시각은 필수입니다.") @JsonDeserialize(using = ScheduleTimeDeserializer.class) LocalDateTime startsAt,
            @NotNull(message = "종료 시각은 필수입니다.") @JsonDeserialize(using = ScheduleTimeDeserializer.class) LocalDateTime endsAt,
            @NotBlank(message = "장소는 필수입니다.") @Size(max = 100, message = "장소는 100자 이하여야 합니다.") String locationName,
            @NotBlank(message = "주소는 필수입니다.") @Size(max = 255, message = "주소는 255자 이하여야 합니다.") String locationAddress,
            @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
            PlaceProvider placeProvider,
            @Size(max = 100, message = "장소 ID는 100자 이하여야 합니다.") @Pattern(regexp = ".*\\S.*", message = "장소 ID는 공백일 수 없습니다.") String placeId
    ) { }

    @Schema(name = "ScheduleUpdate", description = "변경 필드만 최상위에 전달합니다. 생략은 유지, 선택 필드 쌍의 null은 제거입니다.",
            minProperties = 1, additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    @SchemaProperty(name = "title", schema = @Schema(type = "string", maxLength = 100, minLength = 1))
    @SchemaProperty(name = "description", schema = @Schema(type = "string", maxLength = 500, minLength = 1))
    @SchemaProperty(name = "startsAt", schema = @Schema(type = "string", example = "2026-12-10T18:00:00"))
    @SchemaProperty(name = "endsAt", schema = @Schema(type = "string", example = "2026-12-10T20:00:00"))
    @SchemaProperty(name = "locationName", schema = @Schema(type = "string", maxLength = 100, minLength = 1))
    @SchemaProperty(name = "locationAddress", schema = @Schema(type = "string", maxLength = 255, minLength = 1))
    @SchemaProperty(name = "latitude", schema = @Schema(type = "number", minimum = "-90", maximum = "90", nullable = true))
    @SchemaProperty(name = "longitude", schema = @Schema(type = "number", minimum = "-180", maximum = "180", nullable = true))
    @SchemaProperty(name = "placeProvider", schema = @Schema(type = "string", allowableValues = {"KAKAO", "GOOGLE"}, nullable = true))
    @SchemaProperty(name = "placeId", schema = @Schema(type = "string", minLength = 1, maxLength = 100, nullable = true))
    public record Update(@Schema(hidden = true) Map<String, JsonNode> fields) {
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Update {
            fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }
}

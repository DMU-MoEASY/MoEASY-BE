package com.moeasy.moeasybe.domain.schedule.service.support;

import com.moeasy.moeasybe.domain.schedule.dto.request.ScheduleReqDTO;
import com.moeasy.moeasybe.domain.schedule.entity.Schedule;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import tools.jackson.core.JacksonException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@RequiredArgsConstructor
public class ScheduleInput {
    private static final Set<String> FIELDS = Set.of("title", "description", "startsAt", "endsAt",
            "locationName", "locationAddress", "latitude", "longitude", "placeProvider", "placeId");
    private final ObjectMapper mapper;
    private final Validator validator;

    public void validate(ScheduleReqDTO.Create r, LocalDateTime now) {
        if (r == null || !validator.validate(r).isEmpty()) bad();
        if (r.startsAt().getYear() < 1000 || r.startsAt().getYear() > 9999
                || r.endsAt().getYear() < 1000 || r.endsAt().getYear() > 9999
                || !r.startsAt().isAfter(now) || !r.endsAt().isAfter(r.startsAt())
                || (r.latitude() == null) != (r.longitude() == null)
                || (r.placeProvider() == null) != (r.placeId() == null)) bad();
    }

    public ScheduleReqDTO.Create merge(Schedule s, ScheduleReqDTO.Update patch) {
        if (patch == null || patch.fields().isEmpty() || !FIELDS.containsAll(patch.fields().keySet())) bad();
        pair(patch, "latitude", "longitude");
        pair(patch, "placeProvider", "placeId");
        ObjectNode node = mapper.createObjectNode();
        node.put("title", s.getTitle());
        node.put("description", s.getDescription());
        node.put("startsAt", s.getStartsAt().format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")));
        node.put("endsAt", s.getEndsAt().format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")));
        node.put("locationName", s.getLocationName());
        node.put("locationAddress", s.getLocationAddress());
        node.set("latitude", mapper.valueToTree(s.getLatitude()));
        node.set("longitude", mapper.valueToTree(s.getLongitude()));
        node.set("placeProvider", mapper.valueToTree(s.getPlaceProvider()));
        node.put("placeId", s.getPlaceId());
        patch.fields().forEach((key, value) -> {
            if (value != null && !value.isNull()) {
                if (key.equals("latitude") || key.equals("longitude")) {
                    if (!value.isNumber()) bad();
                } else if (!value.isString()) bad();
            }
            node.set(key, value == null ? mapper.nullNode() : value);
        });
        try {
            ScheduleReqDTO.Create r = mapper.treeToValue(node, ScheduleReqDTO.Create.class);
            return new ScheduleReqDTO.Create(r.title(), r.description(),
                    patch.fields().containsKey("startsAt") ? r.startsAt() : s.getStartsAt(),
                    patch.fields().containsKey("endsAt") ? r.endsAt() : s.getEndsAt(),
                    r.locationName(), r.locationAddress(), r.latitude(), r.longitude(), r.placeProvider(), r.placeId());
        } catch (JacksonException exception) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
    }

    private void pair(ScheduleReqDTO.Update patch, String a, String b) {
        if (patch.fields().containsKey(a) != patch.fields().containsKey(b)) bad();
    }
    private static void bad() { throw new GeneralException(GeneralErrorCode.BAD_REQUEST); }
}

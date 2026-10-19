package com.moeasy.moeasybe.domain.settlement.dto.deserializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class SettlementDueAtDeserializer extends ValueDeserializer<LocalDateTime> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
        }
        String value = parser.getString();
        try {
            return LocalDateTime.parse(value, FORMATTER);
        } catch (DateTimeParseException exception) {
            return (LocalDateTime) context.handleWeirdStringValue(
                    LocalDateTime.class, value, "납부 기한은 yyyy-MM-dd'T'HH:mm:ss 형식의 유효한 날짜여야 합니다.");
        }
    }
}

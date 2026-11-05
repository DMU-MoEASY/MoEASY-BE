package com.moeasy.moeasybe.domain.settlement.dto.deserializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** DB에 저장 가능한 초 단위의 정산 일시를 읽는다. 현재 시각과의 비교는 서비스에서 처리한다. */
public class SettlementDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
        }
        String value = parser.getString();
        try {
            LocalDateTime dateTime = LocalDateTime.parse(value, FORMATTER);
            if (dateTime.getYear() < 1000 || dateTime.getYear() > 9999) {
                return (LocalDateTime) context.handleWeirdStringValue(
                        LocalDateTime.class, value, "일시의 연도는 1000~9999 범위여야 합니다.");
            }
            return dateTime;
        } catch (DateTimeParseException exception) {
            return (LocalDateTime) context.handleWeirdStringValue(
                    LocalDateTime.class, value, "일시는 yyyy-MM-dd'T'HH:mm:ss 형식의 유효한 날짜여야 합니다.");
        }
    }
}

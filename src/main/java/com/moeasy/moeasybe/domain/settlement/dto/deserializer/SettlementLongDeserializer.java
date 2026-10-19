package com.moeasy.moeasybe.domain.settlement.dto.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** JSON 정수만 허용하여 소수의 자동 절삭이나 문자열·불리언의 숫자 변환을 방지한다. */
public class SettlementLongDeserializer extends ValueDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return (Long) context.handleUnexpectedToken(Long.class, parser);
        }
        return parser.getLongValue();
    }
}

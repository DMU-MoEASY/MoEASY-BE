package com.moeasy.moeasybe.domain.settlement.dto.deserializer;

import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class SettlementSplitModeDeserializer extends ValueDeserializer<SettlementSplitMode> {

    @Override
    public SettlementSplitMode deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (SettlementSplitMode) context.handleUnexpectedToken(SettlementSplitMode.class, parser);
        }
        String value = parser.getString();
        try {
            return SettlementSplitMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return (SettlementSplitMode) context.handleWeirdStringValue(
                    SettlementSplitMode.class, value, "분할 방식은 EQUAL 또는 CUSTOM이어야 합니다.");
        }
    }
}

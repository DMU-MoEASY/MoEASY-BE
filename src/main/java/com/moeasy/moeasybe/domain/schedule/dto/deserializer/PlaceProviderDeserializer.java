package com.moeasy.moeasybe.domain.schedule.dto.deserializer;

import com.moeasy.moeasybe.domain.schedule.enums.PlaceProvider;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class PlaceProviderDeserializer extends ValueDeserializer<PlaceProvider> {

    @Override
    public PlaceProvider deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (PlaceProvider) context.handleUnexpectedToken(PlaceProvider.class, parser);
        }
        String value = parser.getString();
        try {
            return PlaceProvider.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return (PlaceProvider) context.handleWeirdStringValue(
                    PlaceProvider.class, value, "장소 제공자는 KAKAO 또는 GOOGLE이어야 합니다.");
        }
    }
}

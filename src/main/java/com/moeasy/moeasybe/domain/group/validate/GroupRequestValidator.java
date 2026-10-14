package com.moeasy.moeasybe.domain.group.validate;

import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.math.BigDecimal;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public final class GroupRequestValidator {
    private static final Set<String> PATCH_FIELDS = Set.of("categoryId", "name", "description",
            "regionCode", "placeName", "address", "coverImageKey", "latitude", "longitude");

    private GroupRequestValidator() {
    }

    public static void requireActor(Object request, Long memberId) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 필요합니다.");
        }
        requireMemberId(memberId);
    }

    public static void requireMemberId(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("인증된 회원 ID가 필요합니다.");
        }
    }

    public static void validateCreate(GroupReqDTO.Create request) {
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new GroupException(GroupErrorCode.GROUP_COORDINATE_PAIR_INVALID);
        }
    }

    public static void validatePatch(GroupReqDTO.Update request) {
        if (request.fields().isEmpty() || !PATCH_FIELDS.containsAll(request.fields().keySet())) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        boolean latitude = request.has("latitude");
        boolean longitude = request.has("longitude");
        if (latitude != longitude || (latitude && isNull(request.value("latitude")) != isNull(request.value("longitude")))) {
            throw new GroupException(GroupErrorCode.GROUP_COORDINATE_PAIR_INVALID);
        }
    }

    public static void requireOpen(Group group) {
        if (group.getStatus() == GroupStatus.CLOSED || group.getDeletedAt() != null) {
            throw new GroupException(GroupErrorCode.GROUP_NOT_FOUND);
        }
    }

    public static void requireConfirmationText(Group group, GroupReqDTO.Close request) {
        if (!group.getName().equals(request.confirmationText())) {
            throw new GroupException(GroupErrorCode.GROUP_CONFIRMATION_TEXT_MISMATCH);
        }
    }

    public static boolean isNull(JsonNode node) {
        return node == null || node.isNull();
    }

    public static String text(JsonNode node, int maxLength) {
        if (isNull(node) || !node.isTextual() || node.textValue().isBlank()
                || (maxLength > 0 && node.textValue().length() > maxLength)) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        return node.textValue();
    }

    public static Long longValue(JsonNode node) {
        if (isNull(node) || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() <= 0) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        return node.longValue();
    }

    public static BigDecimal decimal(JsonNode node, String min, String max) {
        if (isNull(node) || !node.isNumber()) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        BigDecimal value = node.decimalValue();
        if (value.compareTo(new BigDecimal(min)) < 0 || value.compareTo(new BigDecimal(max)) > 0
                || value.scale() > 7) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        return value;
    }
}

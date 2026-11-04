package com.moeasy.moeasybe.domain.schedule.exception;
import com.moeasy.moeasybe.domain.schedule.exception.code.AvailabilityErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
public class AvailabilityException extends GeneralException {
    public AvailabilityException(AvailabilityErrorCode code) { super(code); }
}

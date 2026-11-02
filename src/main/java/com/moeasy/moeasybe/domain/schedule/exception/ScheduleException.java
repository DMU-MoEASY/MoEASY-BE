package com.moeasy.moeasybe.domain.schedule.exception;
import com.moeasy.moeasybe.domain.schedule.exception.code.ScheduleErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;

public class ScheduleException extends GeneralException {
    public ScheduleException(ScheduleErrorCode code) { super(code); }
}

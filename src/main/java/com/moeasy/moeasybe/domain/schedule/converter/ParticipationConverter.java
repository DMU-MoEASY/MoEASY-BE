package com.moeasy.moeasybe.domain.schedule.converter;

import com.moeasy.moeasybe.domain.schedule.dto.request.ParticipationReqDTO.ResponseStatus;
import com.moeasy.moeasybe.domain.schedule.dto.response.ParticipationResDTO.Member;
import com.moeasy.moeasybe.domain.schedule.dto.response.ParticipationResDTO.Rsvp;
import com.moeasy.moeasybe.domain.schedule.entity.Schedule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ParticipationConverter {
    private ParticipationConverter() {}

    public static Rsvp toRsvp(Schedule schedule, long actor, List<Member> members, boolean canRespond) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ResponseStatus status : ResponseStatus.values()) counts.put(status.name(), 0L);
        String mine = ResponseStatus.UNDECIDED.name();
        for (Member member : members) {
            counts.merge(member.status(), 1L, Long::sum);
            if (member.groupMemberId() == actor) mine = member.status();
        }
        return Rsvp.builder().scheduleId(schedule.getId()).startsAt(schedule.getStartsAt()).endsAt(schedule.getEndsAt())
                .canRespond(canRespond).myStatus(mine).counts(counts).members(members).build();
    }
}

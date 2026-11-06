package com.moeasy.moeasybe.domain.schedule.service.command;

import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleAccess.Access;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.schedule.converter.AvailabilityConverter;
import com.moeasy.moeasybe.domain.schedule.dto.request.AvailabilityReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.AvailabilityResDTO;
import com.moeasy.moeasybe.domain.schedule.entity.*;
import com.moeasy.moeasybe.domain.schedule.enums.AvailabilityStatus;
import com.moeasy.moeasybe.domain.schedule.exception.AvailabilityException;
import com.moeasy.moeasybe.domain.schedule.exception.code.AvailabilityErrorCode;
import com.moeasy.moeasybe.domain.schedule.repository.*;
import com.moeasy.moeasybe.domain.schedule.service.support.*;
import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilityChanged;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class AvailabilityCommandService {
    private final AvailabilitySupport support;
    private final AvailabilityPollRepository polls;
    private final AvailabilitySlotRepository slots;
    private final AvailabilityResponseRepository responses;
    private final AvailabilityVoteRepository votes;
    private final ScheduleRepository schedules;
    private final GroupRepository groups;
    private final ApplicationEventPublisher events;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilityResDTO.Poll create(Long scheduleId, AvailabilityReqDTO.Configure r, Long memberId) {
        var a = support.access(scheduleId, memberId, true, true, false);
        support.requireEditable(a.schedule());
        AvailabilityInput.validate(r, support.now());
        if (polls.findByScheduleId(scheduleId).isPresent()) fail(AvailabilityErrorCode.DUPLICATE_POLL);
        AvailabilityPoll p = polls.save(AvailabilityConverter.toEntity(scheduleId, r));
        slots.saveAll(AvailabilityInput.slots(p).stream().map(at -> AvailabilityConverter.toSlot(p.getId(), at)).toList());
        changed(a, "CREATED");
        return AvailabilityConverter.toPoll(support.snapshot(a));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilityResDTO.Poll configure(Long scheduleId, AvailabilityReqDTO.Configure r, Long memberId) {
        var a = support.access(scheduleId, memberId, true, true, false);
        support.requireEditable(a.schedule());
        AvailabilityInput.validate(r, support.now());
        AvailabilityPoll p = support.poll(scheduleId);
        if (p.getStatus() == AvailabilityStatus.CONFIRMED) {
            if (!Objects.equals(p.getConfirmedTimeRevision(), a.schedule().getTimeRevision())
                    || p.getOriginalStartAt() == null || !p.getOriginalStartAt().isAfter(support.now()))
                fail(AvailabilityErrorCode.RESTORE_CONFLICT);
            a.schedule().reschedule(p.getOriginalStartAt(), p.getOriginalEndAt());
        }
        p.configure(r.startDate(), r.dayCount(), r.startHour(), r.endHour(), r.voteDeadlineAt());
        Set<LocalDateTime> desired = new HashSet<>(AvailabilityInput.slots(p));
        List<AvailabilitySlot> existing = slots.findAllByAvailabilityPollIdOrderBySlotAt(p.getId());
        Set<Long> removed = existing.stream().filter(s -> !desired.contains(s.getSlotAt()))
                .map(AvailabilitySlot::getId).collect(Collectors.toSet());
        votes.deleteAllInBatch(votes.findForPoll(p.getId()).stream().filter(v -> removed.contains(v.getAvailabilitySlotId())).toList());
        slots.deleteAllInBatch(existing.stream().filter(s -> removed.contains(s.getId())).toList());
        existing.forEach(s -> desired.remove(s.getSlotAt()));
        slots.saveAll(desired.stream().map(at -> AvailabilityConverter.toSlot(p.getId(), at)).toList());
        changed(a, "REOPENED");
        return AvailabilityConverter.toPoll(support.snapshot(a));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void submit(Long scheduleId, AvailabilityReqDTO.Submit r, Long memberId) {
        var a = support.access(scheduleId, memberId, true, false, false);
        support.requireEditable(a.schedule());
        AvailabilityPoll p = support.poll(scheduleId);
        requireOpen(p);
        if (r == null || r.slots() == null || r.slots().stream().anyMatch(Objects::isNull)) AvailabilityInput.invalid();
        Map<LocalDateTime, Long> candidates = slots.findAllByAvailabilityPollIdOrderBySlotAt(p.getId()).stream()
                .collect(Collectors.toMap(AvailabilitySlot::getSlotAt, AvailabilitySlot::getId));
        Set<LocalDateTime> selected = new HashSet<>(r.slots());
        if (selected.size() != r.slots().size() || !candidates.keySet().containsAll(selected)) AvailabilityInput.invalid();
        Long actor = a.actor().getId();
        var prior = responses.findByAvailabilityPollIdAndGroupMemberId(p.getId(), actor);
        List<AvailabilityVote> oldVotes = votes.findForPoll(p.getId()).stream().filter(v -> v.getGroupMemberId().equals(actor)).toList();
        Set<Long> desired = selected.stream().map(candidates::get).collect(Collectors.toSet());
        if (prior.isPresent() && prior.get().getMembershipJoinedAt().equals(a.actor().getJoinedAt())
                && oldVotes.stream().allMatch(AvailabilityVote::isAvailable)
                && oldVotes.stream().map(AvailabilityVote::getAvailabilitySlotId).collect(Collectors.toSet()).equals(desired)) return;
        AvailabilityResponse response = prior.orElseGet(() -> responses.save(AvailabilityConverter.toResponse(p.getId(), actor, a.actor().getJoinedAt())));
        response.submit(a.actor().getJoinedAt());
        votes.deleteAllInBatch(oldVotes);
        votes.saveAll(desired.stream().map(id -> AvailabilityConverter.toVote(id, actor)).toList());
        p.touch(); changed(a, "RESPONSE_CHANGED");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(Long scheduleId, Long memberId) {
        var a = support.access(scheduleId, memberId, true, false, false);
        support.requireEditable(a.schedule());
        AvailabilityPoll p = support.poll(scheduleId);
        requireOpen(p);
        var response = responses.findByAvailabilityPollIdAndGroupMemberId(p.getId(), a.actor().getId());
        if (response.isEmpty()) return;
        votes.deleteAllInBatch(votes.findForPoll(p.getId()).stream().filter(v -> v.getGroupMemberId().equals(a.actor().getId())).toList());
        responses.delete(response.get()); p.touch(); changed(a, "RESPONSE_WITHDRAWN");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilityResDTO.Poll confirm(Long scheduleId, AvailabilityReqDTO.Confirm r, Long memberId) {
        var a = support.access(scheduleId, memberId, true, true, false);
        AvailabilityPoll p = support.poll(scheduleId);
        if (r == null || r.startsAt() == null || r.token() == null) AvailabilityInput.invalid();
        support.requireEditable(a.schedule());
        if (p.getStatus() == AvailabilityStatus.CONFIRMED) {
            if (r.startsAt().equals(p.getConfirmedStartAt()) && r.token().equals(p.getConfirmationToken())
                    && Objects.equals(p.getConfirmedTimeRevision(), a.schedule().getTimeRevision()))
                return AvailabilityConverter.toPoll(support.snapshot(a));
            fail(AvailabilityErrorCode.STALE_RESULT);
        }
        var snapshot = support.snapshot(a);
        if (!snapshot.token().equals(r.token())) fail(AvailabilityErrorCode.STALE_RESULT);
        var window = snapshot.recommendations().stream().filter(w -> w.startsAt().equals(r.startsAt()))
                .findFirst().orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.STALE_RESULT));
        Schedule s = a.schedule();
        LocalDateTime originalStart = s.getStartsAt(), originalEnd = s.getEndsAt();
        s.reschedule(window.startsAt(), window.endsAt());
        p.confirm(window.startsAt(), window.endsAt(), originalStart, originalEnd, s.getTimeRevision(), r.token());
        changed(a, "CONFIRMED");
        return AvailabilityConverter.toPoll(support.snapshot(a));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long scheduleId, Long memberId) {
        var a = support.access(scheduleId, memberId, true, false, true);
        AvailabilityPoll p = support.poll(scheduleId);
        votes.deleteAllInBatch(votes.findForPoll(p.getId()));
        responses.deleteAllInBatch(responses.findAllByAvailabilityPollId(p.getId()));
        slots.deleteAllInBatch(slots.findAllByAvailabilityPollIdOrderBySlotAt(p.getId()));
        polls.delete(p); changed(a, "DELETED");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void closeDue(Long scheduleId) {
        // Jobs use the same group -> schedule order, including deleted schedules.
        var initial = schedules.findById(scheduleId);
        if (initial.isEmpty()) return;
        Long groupId = initial.get().getGroup().getId();
        groups.findByIdForUpdate(groupId).orElseThrow();
        schedules.findByIdForUpdate(scheduleId);
        polls.findByScheduleId(scheduleId).ifPresent(p -> {
            if (p.getStatus() == AvailabilityStatus.OPEN && !p.getVoteDeadlineAt().isAfter(support.now())) {
                p.closeIfDue(support.now());
                events.publishEvent(new AvailabilityChanged(groupId, scheduleId, "CLOSED"));
            }
        });
    }
    private void requireOpen(AvailabilityPoll p) {
        if (p.effectiveStatus(support.now()) != AvailabilityStatus.OPEN) fail(AvailabilityErrorCode.NOT_OPEN);
    }
    private void changed(Access a, String type) {
        events.publishEvent(new AvailabilityChanged(a.group().getId(), a.schedule().getId(), type));
    }
    private void fail(AvailabilityErrorCode code) { throw new AvailabilityException(code); }
}

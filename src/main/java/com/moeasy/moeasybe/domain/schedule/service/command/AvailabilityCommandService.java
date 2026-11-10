package com.moeasy.moeasybe.domain.schedule.service.command;

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

@Service
@RequiredArgsConstructor
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
    public AvailabilityResDTO.Poll create(Long scheduleId, AvailabilityReqDTO.Configure request, Long memberId) {
        var access = support.access(scheduleId, memberId, true, true, false);
        support.requireEditable(access.schedule());
        AvailabilityInput.validate(request, support.now());
        if (polls.findByScheduleId(scheduleId).isPresent()) fail(AvailabilityErrorCode.DUPLICATE_POLL);
        AvailabilityPoll poll = polls.save(AvailabilityConverter.toEntity(scheduleId, request));
        slots.saveAll(AvailabilityInput.slots(poll).stream().map(at -> AvailabilityConverter.toSlot(poll.getId(), at)).toList());
        changed(access, "CREATED");
        return AvailabilityConverter.toPoll(support.snapshot(access));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilityResDTO.Poll configure(Long scheduleId, AvailabilityReqDTO.Configure request, Long memberId) {
        var access = support.access(scheduleId, memberId, true, true, false);
        support.requireEditable(access.schedule());
        AvailabilityInput.validate(request, support.now());
        AvailabilityPoll poll = support.poll(scheduleId);
        if (poll.getStatus() == AvailabilityStatus.CONFIRMED) {
            if (!Objects.equals(poll.getConfirmedTimeRevision(), access.schedule().getTimeRevision())
                    || poll.getOriginalStartAt() == null || !poll.getOriginalStartAt().isAfter(support.now()))
                fail(AvailabilityErrorCode.RESTORE_CONFLICT);
            access.schedule().reschedule(poll.getOriginalStartAt(), poll.getOriginalEndAt());
        }
        poll.configure(request.startDate(), request.dayCount(), request.startHour(), request.endHour(), request.voteDeadlineAt());
        Set<LocalDateTime> desired = new HashSet<>(AvailabilityInput.slots(poll));
        List<AvailabilitySlot> existing = slots.findAllByAvailabilityPollIdOrderBySlotAt(poll.getId());
        Set<Long> removed = existing.stream().filter(slot -> !desired.contains(slot.getSlotAt()))
                .map(AvailabilitySlot::getId).collect(Collectors.toSet());
        if (!removed.isEmpty()) {
            votes.deleteAllByAvailabilitySlotIdIn(removed);
        }
        slots.deleteAllInBatch(existing.stream().filter(slot -> removed.contains(slot.getId())).toList());
        existing.forEach(slot -> desired.remove(slot.getSlotAt()));
        slots.saveAll(desired.stream().map(at -> AvailabilityConverter.toSlot(poll.getId(), at)).toList());
        changed(access, "REOPENED");
        return AvailabilityConverter.toPoll(support.snapshot(access));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void submit(Long scheduleId, AvailabilityReqDTO.Submit request, Long memberId) {
        var access = support.access(scheduleId, memberId, true, false, false);
        support.requireEditable(access.schedule());
        AvailabilityPoll poll = support.poll(scheduleId);
        requireOpen(poll);
        if (request == null || request.slots() == null || request.slots().stream().anyMatch(Objects::isNull)) AvailabilityInput.invalid();
        Map<LocalDateTime, Long> candidates = slots.findAllByAvailabilityPollIdOrderBySlotAt(poll.getId()).stream()
                .collect(Collectors.toMap(AvailabilitySlot::getSlotAt, AvailabilitySlot::getId));
        Set<LocalDateTime> selected = new HashSet<>(request.slots());
        if (selected.size() != request.slots().size() || !candidates.keySet().containsAll(selected)) AvailabilityInput.invalid();
        Long actor = access.actor().getId();
        var prior = responses.findByAvailabilityPollIdAndGroupMemberId(poll.getId(), actor);
        List<AvailabilityVote> oldVotes = votes.findForPoll(poll.getId()).stream().filter(vote -> vote.getGroupMemberId().equals(actor)).toList();
        Set<Long> desired = selected.stream().map(candidates::get).collect(Collectors.toSet());
        if (prior.isPresent() && prior.get().getMembershipJoinedAt().equals(access.actor().getJoinedAt())
                && oldVotes.stream().allMatch(AvailabilityVote::isAvailable)
                && oldVotes.stream().map(AvailabilityVote::getAvailabilitySlotId).collect(Collectors.toSet()).equals(desired)) return;
        AvailabilityResponse response = prior.orElseGet(() -> responses.save(AvailabilityConverter.toResponse(poll.getId(), actor, access.actor().getJoinedAt())));
        response.submit(access.actor().getJoinedAt());
        votes.deleteAllInBatch(oldVotes);
        votes.saveAll(desired.stream().map(id -> AvailabilityConverter.toVote(id, actor)).toList());
        poll.touch();
        changed(access, "RESPONSE_CHANGED");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(Long scheduleId, Long memberId) {
        var access = support.access(scheduleId, memberId, true, false, false);
        support.requireEditable(access.schedule());
        AvailabilityPoll poll = support.poll(scheduleId);
        requireOpen(poll);
        var response = responses.findByAvailabilityPollIdAndGroupMemberId(poll.getId(), access.actor().getId());
        if (response.isEmpty()) return;
        votes.deleteAllInBatch(votes.findForPoll(poll.getId()).stream().filter(vote -> vote.getGroupMemberId().equals(access.actor().getId())).toList());
        responses.delete(response.get());
        poll.touch();
        changed(access, "RESPONSE_WITHDRAWN");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilityResDTO.Poll confirm(Long scheduleId, AvailabilityReqDTO.Confirm request, Long memberId) {
        var access = support.access(scheduleId, memberId, true, true, false);
        AvailabilityPoll poll = support.poll(scheduleId);
        if (request == null || request.startsAt() == null || request.token() == null) AvailabilityInput.invalid();
        support.requireEditable(access.schedule());
        if (poll.getStatus() == AvailabilityStatus.CONFIRMED) {
            if (request.startsAt().equals(poll.getConfirmedStartAt()) && request.token().equals(poll.getConfirmationToken())
                    && Objects.equals(poll.getConfirmedTimeRevision(), access.schedule().getTimeRevision()))
                return AvailabilityConverter.toPoll(support.snapshot(access));
            fail(AvailabilityErrorCode.STALE_RESULT);
        }
        var snapshot = support.snapshot(access);
        if (!snapshot.token().equals(request.token())) fail(AvailabilityErrorCode.STALE_RESULT);
        var window = snapshot.recommendations().stream().filter(candidateWindow -> candidateWindow.startsAt().equals(request.startsAt()))
                .findFirst().orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.STALE_RESULT));
        Schedule schedule = access.schedule();
        LocalDateTime originalStart = schedule.getStartsAt();
        LocalDateTime originalEnd = schedule.getEndsAt();
        schedule.reschedule(window.startsAt(), window.endsAt());
        poll.confirm(window.startsAt(), window.endsAt(), originalStart, originalEnd, schedule.getTimeRevision(), request.token());
        changed(access, "CONFIRMED");
        return AvailabilityConverter.toPoll(support.snapshot(access));
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long scheduleId, Long memberId) {
        var access = support.access(scheduleId, memberId, true, false, true);
        AvailabilityPoll poll = support.poll(scheduleId);
        votes.deleteAllByPollId(poll.getId());
        responses.deleteAllInBatch(responses.findAllByAvailabilityPollId(poll.getId()));
        slots.deleteAllInBatch(slots.findAllByAvailabilityPollIdOrderBySlotAt(poll.getId()));
        polls.delete(poll);
        changed(access, "DELETED");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void closeDue(Long scheduleId) {
        // Jobs use the same group -> schedule order, including deleted schedules.
        var initial = schedules.findById(scheduleId);
        if (initial.isEmpty()) return;
        Long groupId = initial.get().getGroup().getId();
        groups.findByIdForUpdate(groupId).orElseThrow();
        schedules.findByIdForUpdate(scheduleId);
        polls.findByScheduleId(scheduleId).ifPresent(poll -> {
            if (poll.getStatus() == AvailabilityStatus.OPEN && !poll.getVoteDeadlineAt().isAfter(support.now())) {
                poll.closeIfDue(support.now());
                events.publishEvent(new AvailabilityChanged(groupId, scheduleId, "CLOSED"));
            }
        });
    }
    private void requireOpen(AvailabilityPoll poll) {
        if (poll.effectiveStatus(support.now()) != AvailabilityStatus.OPEN) fail(AvailabilityErrorCode.NOT_OPEN);
    }
    private void changed(AvailabilitySupport.Access access, String type) {
        events.publishEvent(new AvailabilityChanged(access.group().getId(), access.schedule().getId(), type));
    }
    private void fail(AvailabilityErrorCode code) {
        throw new AvailabilityException(code);
    }
}

package com.moeasy.moeasybe.domain.schedule.repository;
import com.moeasy.moeasybe.domain.schedule.entity.AvailabilityVote;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AvailabilityVoteRepository extends JpaRepository<AvailabilityVote, Long> {
    @Query("select v from AvailabilityVote v where v.availabilitySlotId in (select s.id from AvailabilitySlot s where s.availabilityPollId = :pollId)")
    List<AvailabilityVote> findForPoll(@Param("pollId") Long pollId);
}

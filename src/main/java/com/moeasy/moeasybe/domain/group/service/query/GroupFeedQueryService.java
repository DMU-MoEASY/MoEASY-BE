package com.moeasy.moeasybe.domain.group.service.query;

import com.moeasy.moeasybe.domain.advertisement.repository.AdvertisementRepository;
import com.moeasy.moeasybe.domain.group.converter.GroupFeedConverter;
import com.moeasy.moeasybe.domain.group.dto.request.GroupFeedReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupFeedResDTO;
import com.moeasy.moeasybe.domain.group.repository.CategoryRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupFeedRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupFeedRepository.Candidate;
import com.moeasy.moeasybe.domain.group.service.GroupCoverImageService;
import com.moeasy.moeasybe.domain.group.service.support.*;
import com.moeasy.moeasybe.domain.group.validate.GroupRequestValidator;
import com.moeasy.moeasybe.domain.member.enums.MemberStatus;
import com.moeasy.moeasybe.domain.member.repository.MemberCategoryRepository;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import com.moeasy.moeasybe.domain.region.repository.RegionRepository;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupFeedQueryService {
    private final MemberRepository members;
    private final MemberCategoryRepository interests;
    private final CategoryRepository categories;
    private final RegionRepository regions;
    private final GroupFeedRepository candidates;
    private final AdvertisementRepository advertisements;
    private final GroupCoverImageService covers;
    private final GroupFeedCursorCodec cursors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GroupFeedResDTO.Feed getFeed(Long memberId, GroupFeedReqDTO.Search input) {
        GroupRequestValidator.requireMemberId(memberId);
        GroupFeedFilter filter = GroupFeedFilter.from(input);
        if ((!filter.categoryCodes().isEmpty() && categories.findAllByCodeInAndDeletedAtIsNull(filter.categoryCodes())
                .size() != filter.categoryCodes().size()) || (!filter.regionCode().isEmpty()
                && regions.findByCodeAndDeletedAtIsNull(filter.regionCode()).isEmpty())) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        var member = members.findById(memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));
        if (member.getStatus() != MemberStatus.ACTIVE || member.getDeletedAt() != null) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        var cursor = input.cursor() == null
                ? new GroupFeedCursor(memberId, filter.fingerprint(),
                        LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul"))).truncatedTo(ChronoUnit.MICROS), null, null, 0)
                : cursors.decode(input.cursor(), memberId, filter.fingerprint());
        List<Long> categoryIds = interests.findCategoryIdsByMemberId(memberId);
        String primaryRegion = member.getPrimaryRegion() == null ? null : member.getPrimaryRegion().getCode();
        long total = candidates.count(memberId, categoryIds, primaryRegion, filter, cursor.at());
        var normal = candidates.find(memberId, categoryIds, primaryRegion, filter, cursor.at(), true, cursor.normal(), 11);
        var discovery = candidates.find(memberId, categoryIds, primaryRegion, filter, cursor.at(), false, cursor.discovery(), 1);
        var ads = advertisements.findEligible(cursor.at());
        var page = GroupFeedPage.select(normal.size(), !discovery.isEmpty(), !ads.isEmpty());
        List<GroupFeedResDTO.Item> items = new ArrayList<>();
        var normalPosition = cursor.normal();
        var discoveryPosition = cursor.discovery();
        for (Candidate c : normal.subList(0, page.normalCount())) {
            items.add(GroupFeedConverter.toGroupCard(c, covers.createViewUrl(c.coverImageKey()), reasons(c)));
            normalPosition = c.position();
        }
        if (page.discovery()) {
            Candidate c = discovery.getFirst();
            items.add(GroupFeedConverter.toGroupCard(c, covers.createViewUrl(c.coverImageKey()), reasons(c)));
            discoveryPosition = c.position();
        }
        long adSequence = cursor.adSequence();
        if (page.advertisement()) {
            items.add(GroupFeedConverter.toAdCard(ads.get((int) (adSequence % ads.size()))));
            adSequence = Math.incrementExact(adSequence);
        }
        String next = page.hasNext() ? cursors.encode(new GroupFeedCursor(memberId, filter.fingerprint(), cursor.at(),
                normalPosition, discoveryPosition, adSequence)) : null;
        return GroupFeedConverter.toFeed(items, total, next);
    }

    private List<String> reasons(Candidate c) {
        List<String> tags = new ArrayList<>();
        if (c.interest()) tags.add("INTEREST_MATCH");
        if (c.region()) tags.add("REGION_MATCH");
        if (c.tier().equals("silver")) tags.add("SILVER_TIER");
        if (c.tier().equals("gold")) tags.add("GOLD_TIER");
        if (c.recent()) tags.add("RECENT_ACTIVITY");
        return List.copyOf(tags);
    }
}

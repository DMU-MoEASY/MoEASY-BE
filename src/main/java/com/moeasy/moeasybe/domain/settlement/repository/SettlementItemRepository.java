package com.moeasy.moeasybe.domain.settlement.repository;

import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {
}

package com.scrumai.fraud.repository;

import com.scrumai.fraud.entity.TransactionChainEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionChainRepository extends JpaRepository<TransactionChainEntity, UUID> {

  List<TransactionChainEntity> findAllByRootFraudTransaction_IdOrderByHopLevelAscTransactionTimeAsc(
      UUID rootFraudTransactionId);
}

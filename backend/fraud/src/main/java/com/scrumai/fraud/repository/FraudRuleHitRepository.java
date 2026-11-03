package com.scrumai.fraud.repository;

import com.scrumai.fraud.entity.FraudRuleHitEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudRuleHitRepository extends JpaRepository<FraudRuleHitEntity, UUID> {

  List<FraudRuleHitEntity> findAllByFraudTransaction_IdOrderByCreatedAtAsc(UUID fraudTransactionId);
}

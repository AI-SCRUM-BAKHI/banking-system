package com.scrumai.fraud.repository;

import com.scrumai.fraud.entity.FraudTransactionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudTransactionRepository extends JpaRepository<FraudTransactionEntity, UUID> {

  Optional<FraudTransactionEntity> findByTransactionRef(String transactionRef);
}

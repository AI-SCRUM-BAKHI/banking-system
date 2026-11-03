package com.scrumai.fraud.repository;

import com.scrumai.fraud.entity.FraudTransactionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FraudTransactionRepository
    extends JpaRepository<FraudTransactionEntity, UUID>,
        JpaSpecificationExecutor<FraudTransactionEntity> {

  Optional<FraudTransactionEntity> findByTransactionRef(String transactionRef);

  boolean existsByTransactionRef(String transactionRef);
}

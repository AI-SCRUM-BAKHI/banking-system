package com.scrumai.fraud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "fraud_rule_hits",
    uniqueConstraints =
        @UniqueConstraint(
            name = "fraud_rule_hits_fraud_transaction_id_rule_code_key",
            columnNames = {"fraud_transaction_id", "rule_code"}),
    indexes = @Index(name = "idx_rule_hits_transaction", columnList = "fraud_transaction_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class FraudRuleHitEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fraud_transaction_id", nullable = false)
  private FraudTransactionEntity fraudTransaction;

  @Column(name = "rule_code", nullable = false, length = 50)
  private String ruleCode;

  @Column(name = "rule_name", nullable = false, length = 160)
  private String ruleName;

  @Column(name = "score_contribution", nullable = false, precision = 5, scale = 2)
  private BigDecimal scoreContribution;

  @Column(nullable = false, length = 500)
  private String explanation;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

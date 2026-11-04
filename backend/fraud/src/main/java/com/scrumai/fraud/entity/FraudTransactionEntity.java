package com.scrumai.fraud.entity;

import com.scrumai.fraud.enums.ReviewStatus;
import com.scrumai.fraud.enums.RiskLevel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "fraud_transactions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "fraud_transactions_transaction_ref_key",
            columnNames = "transaction_ref"),
    indexes = {
      @Index(name = "idx_fraud_transactions_created", columnList = "created_at"),
      @Index(name = "idx_fraud_transactions_risk", columnList = "risk_score, transaction_time")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class FraudTransactionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "transaction_ref", nullable = false, length = 80)
  private String transactionRef;

  @Column(name = "from_account_ref", nullable = false, length = 80)
  private String fromAccountRef;

  @Column(name = "from_account_masked", nullable = false, length = 40)
  private String fromAccountMasked;

  @Column(name = "from_customer_name", length = 120)
  private String fromCustomerName;

  @Column(name = "to_account_ref", nullable = false, length = 80)
  private String toAccountRef;

  @Column(name = "to_account_masked", nullable = false, length = 40)
  private String toAccountMasked;

  @Column(name = "to_customer_name", length = 120)
  private String toCustomerName;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String currency = "VND";

  @Column(name = "transaction_time", nullable = false)
  private Instant transactionTime;

  @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
  private BigDecimal riskScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "risk_level", nullable = false, length = 10)
  private RiskLevel riskLevel;

  @Column(name = "fraud_reason", nullable = false, length = 500)
  private String fraudReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false, length = 30)
  private ReviewStatus reviewStatus = ReviewStatus.NEW;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(
      mappedBy = "fraudTransaction",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private List<FraudRuleHitEntity> ruleHits = new ArrayList<>();

  @OneToMany(
      mappedBy = "rootFraudTransaction",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private List<TransactionChainEntity> transactionChain = new ArrayList<>();

  public void addRuleHit(FraudRuleHitEntity ruleHit) {
    ruleHits.add(ruleHit);
    ruleHit.setFraudTransaction(this);
  }

  public void addTransactionChainEntry(TransactionChainEntity chainEntry) {
    transactionChain.add(chainEntry);
    chainEntry.setRootFraudTransaction(this);
  }
}

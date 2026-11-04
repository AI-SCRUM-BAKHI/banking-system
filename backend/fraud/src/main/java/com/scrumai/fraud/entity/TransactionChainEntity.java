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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "transaction_chain",
    uniqueConstraints =
        @UniqueConstraint(
            name = "transaction_chain_root_fraud_transaction_id_transaction_ref_key",
            columnNames = {"root_fraud_transaction_id", "transaction_ref"}),
    indexes =
        @Index(
            name = "idx_transaction_chain_root_hop",
            columnList = "root_fraud_transaction_id, hop_level, transaction_time"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class TransactionChainEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "root_fraud_transaction_id", nullable = false)
  private FraudTransactionEntity rootFraudTransaction;

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

  @Column(name = "hop_level", nullable = false)
  private Integer hopLevel;

  @Column(name = "is_root", nullable = false)
  private boolean root;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}

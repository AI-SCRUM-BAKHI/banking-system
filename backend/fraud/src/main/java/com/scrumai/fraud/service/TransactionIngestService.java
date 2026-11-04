package com.scrumai.fraud.service;

import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.fraud.dto.TransactionCreateRequest;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionIngestService {
  private final JdbcTemplate jdbc;

  public TransactionIngestService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Created(String transactionRef, String processingStatus, Instant createdAt) {}

  @Transactional
  public Created create(TransactionCreateRequest request) {
    var tx = request.source();
    try {
      tx.validate(Instant.now());
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(
          ErrorCode.INVALID_REQUEST,
          "Invalid transaction: require whole positive VND, different accounts and valid timestamps");
    }
    // Only source fields are accepted. Evaluation fields always use database defaults.
    var inserted =
        jdbc.query(
            """
        INSERT INTO transactions
          (transaction_ref, from_account_ref, from_account_masked, from_customer_name,
           to_account_ref, to_account_masked, to_customer_name, amount, currency,
           transaction_time, posted_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (transaction_ref) DO NOTHING
        RETURNING transaction_ref, processing_status, created_at
        """,
            (rs, row) ->
                new Created(
                    rs.getString("transaction_ref"),
                    rs.getString("processing_status"),
                    rs.getTimestamp("created_at").toInstant()),
            tx.transactionRef(),
            tx.fromAccountRef(),
            tx.fromAccountMasked(),
            tx.fromCustomerName(),
            tx.toAccountRef(),
            tx.toAccountMasked(),
            tx.toCustomerName(),
            tx.amount(),
            tx.currency(),
            Timestamp.from(tx.transactionTime()),
            tx.postedAt() == null ? null : Timestamp.from(tx.postedAt()));
    if (inserted.isEmpty()) {
      throw new BusinessException(
          ErrorCode.RESOURCE_CONFLICT, "Transaction reference already exists");
    }
    return inserted.getFirst();
  }
}

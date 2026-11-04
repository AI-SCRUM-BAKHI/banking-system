package com.scrumai.fraud.scan;

import com.scrumai.fraud.dto.FraudRequests.ChainEntry;
import com.scrumai.fraud.service.RiskModels.Transfer;
import java.math.BigDecimal;
import java.time.Instant;

public record SourceTransaction(
    String transactionRef,
    String fromAccountRef,
    String fromAccountMasked,
    String fromCustomerName,
    String toAccountRef,
    String toAccountMasked,
    String toCustomerName,
    BigDecimal amount,
    String currency,
    Instant transactionTime,
    Instant postedAt) {
  public Transfer transfer() {
    return new Transfer(
        transactionRef, fromAccountRef, toAccountRef, amount, currency, transactionTime, postedAt);
  }

  public ChainEntry root() {
    return new ChainEntry(
        transactionRef,
        fromAccountRef,
        fromAccountMasked,
        fromCustomerName,
        toAccountRef,
        toAccountMasked,
        toCustomerName,
        amount,
        currency,
        transactionTime,
        0,
        true);
  }

  public void validate(Instant now) {
    required(transactionRef, 80);
    required(fromAccountRef, 80);
    required(toAccountRef, 80);
    required(fromAccountMasked, 40);
    required(toAccountMasked, 40);
    if (fromCustomerName != null && fromCustomerName.length() > 120
        || toCustomerName != null && toCustomerName.length() > 120
        || amount.signum() <= 0
        || amount.stripTrailingZeros().scale() > 0
        || amount.compareTo(new BigDecimal("999999999999999")) > 0
        || !"VND".equals(currency)
        || fromAccountRef.equals(toAccountRef)
        || transactionTime.isAfter(now)
        || postedAt != null && (postedAt.isBefore(transactionTime) || postedAt.isAfter(now))) {
      throw new IllegalArgumentException("Source transaction is incompatible with v1 evaluation");
    }
  }

  private static void required(String value, int max) {
    if (value == null || value.isBlank() || value.length() > max) {
      throw new IllegalArgumentException("Source identifier or display field is invalid");
    }
  }
}

package com.scrumai.fraud.dto;

import com.scrumai.fraud.enums.ReviewStatus;
import com.scrumai.fraud.enums.RiskLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class FraudRequests {

  private FraudRequests() {}

  public record Create(
      @NotBlank @Size(max = 80) String transactionRef,
      @NotBlank @Size(max = 80) String fromAccountRef,
      @NotBlank @Size(max = 40) String fromAccountMasked,
      @Size(max = 120) String fromCustomerName,
      @NotBlank @Size(max = 80) String toAccountRef,
      @NotBlank @Size(max = 40) String toAccountMasked,
      @Size(max = 120) String toCustomerName,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
          BigDecimal amount,
      @NotBlank @Size(min = 3, max = 3) String currency,
      @NotNull Instant transactionTime,
      @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal riskScore,
      @NotNull RiskLevel riskLevel,
      @NotBlank @Size(max = 500) String fraudReason,
      @NotNull List<@Valid RuleHit> ruleHits,
      @NotNull @Size(min = 1) List<@Valid ChainEntry> transactionChain) {}

  public record RuleHit(
      @NotBlank @Size(max = 50) String ruleCode,
      @NotBlank @Size(max = 160) String ruleName,
      @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal scoreContribution,
      @NotBlank @Size(max = 500) String explanation) {}

  public record ChainEntry(
      @NotBlank @Size(max = 80) String transactionRef,
      @NotBlank @Size(max = 80) String fromAccountRef,
      @NotBlank @Size(max = 40) String fromAccountMasked,
      @Size(max = 120) String fromCustomerName,
      @NotBlank @Size(max = 80) String toAccountRef,
      @NotBlank @Size(max = 40) String toAccountMasked,
      @Size(max = 120) String toCustomerName,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
          BigDecimal amount,
      @NotBlank @Size(min = 3, max = 3) String currency,
      @NotNull Instant transactionTime,
      @NotNull Integer hopLevel,
      boolean root) {}

  public record Review(@NotNull ReviewStatus status) {}
}

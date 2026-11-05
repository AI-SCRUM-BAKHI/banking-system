package com.scrumai.fraud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.scrumai.fraud.scan.SourceTransaction;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionCreateRequest(
    @NotBlank @Size(max = 80) String transactionRef,
    @NotBlank @Size(max = 80) String fromAccountRef,
    @NotBlank @Size(max = 40) String fromAccountMasked,
    @Size(max = 120) String fromCustomerName,
    @NotBlank @Size(max = 80) String toAccountRef,
    @NotBlank @Size(max = 40) String toAccountMasked,
    @Size(max = 120) String toCustomerName,
    @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("999999999999999")
        BigDecimal amount,
    @NotNull @Pattern(regexp = "VND") String currency,
    @NotNull @JsonDeserialize(using = OffsetTimestampDeserializer.class)
        OffsetDateTime transactionTime,
    @JsonDeserialize(using = OffsetTimestampDeserializer.class) OffsetDateTime postedAt) {
  public SourceTransaction source() {
    return new SourceTransaction(
        transactionRef,
        fromAccountRef,
        fromAccountMasked,
        fromCustomerName,
        toAccountRef,
        toAccountMasked,
        toCustomerName,
        amount,
        currency,
        transactionTime.toInstant(),
        postedAt == null ? null : postedAt.toInstant());
  }
}

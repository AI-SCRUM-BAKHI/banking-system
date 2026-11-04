package com.scrumai.fraud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationRequest(
    @NotBlank @Size(max = 80) String transactionRef,
    @NotBlank @Size(max = 80) String senderAccountId,
    @NotBlank @Size(max = 80) String receiverAccountId,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 19, fraction = 0)
        BigDecimal amountVnd,
    @NotNull @Pattern(regexp = "VND") String currency,
    @NotNull @JsonDeserialize(using = OffsetTimestampDeserializer.class)
        OffsetDateTime transactionTime,
    @JsonDeserialize(using = OffsetTimestampDeserializer.class) OffsetDateTime postedAt) {}

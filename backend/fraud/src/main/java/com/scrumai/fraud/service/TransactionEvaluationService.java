package com.scrumai.fraud.service;

import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.fraud.dto.EvaluationRequest;
import com.scrumai.fraud.repository.RiskEvaluationRepository;
import com.scrumai.fraud.repository.RiskEvaluationRepository.Saved;
import com.scrumai.fraud.repository.RiskEvaluationRepository.State;
import com.scrumai.fraud.service.RiskModels.Context;
import com.scrumai.fraud.service.RiskModels.Evaluation;
import com.scrumai.fraud.service.RiskModels.Result;
import com.scrumai.fraud.service.RiskModels.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class TransactionEvaluationService {
  private final RiskEvaluationRepository repository;
  private final RiskScoreEngine engine;
  private final ObjectMapper mapper;

  public TransactionEvaluationService(
      RiskEvaluationRepository repository, RiskScoreEngine engine, ObjectMapper mapper) {
    this.repository = repository;
    this.engine = engine;
    this.mapper = mapper;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Result evaluate(EvaluationRequest request) {
    Transfer tx =
        new Transfer(
            request.transactionRef(),
            request.senderAccountId(),
            request.receiverAccountId(),
            new BigDecimal(request.amountVnd().toBigIntegerExact()),
            request.currency(),
            request.transactionTime().toInstant(),
            request.postedAt() == null ? null : request.postedAt().toInstant());
    State state = repository.lock();
    Optional<Saved> previous = repository.find(tx.transactionRef());
    if (previous.isPresent()) {
      Transfer original = mapper.readValue(previous.get().requestJson(), Transfer.class);
      if (!original.equals(tx)) {
        throw new BusinessException(
            ErrorCode.RESOURCE_CONFLICT,
            "Transaction reference already exists with different data");
      }
      return mapper.readValue(previous.get().resultJson(), Result.class);
    }
    Instant evaluatedAt = Instant.now();
    validate(tx, state, evaluatedAt);
    Context context = repository.context(tx, state);
    Evaluation evaluation = engine.evaluate(tx, context);
    Map<String, Object> audit = new LinkedHashMap<>();
    audit.put("evaluatedAt", evaluatedAt);
    audit.put("configuration", engine.configuration());
    audit.put("coverage", context.coverage());
    audit.put("unknownPosting", context.unknownPosting());
    audit.put("contextLastIngestOrder", context.lastIngestOrder());
    audit.put("contextWatermark", context.watermark());
    audit.put("rules", evaluation.rules());
    audit.put("result", evaluation.result());
    repository.save(
        tx,
        state,
        evaluatedAt,
        mapper.writeValueAsString(tx),
        mapper.writeValueAsString(evaluation.result()),
        mapper.writeValueAsString(audit));
    return evaluation.result();
  }

  private void validate(Transfer tx, State state, Instant evaluatedAt) {
    if (tx.senderAccountId().equals(tx.receiverAccountId())) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "Sender and receiver must differ");
    }
    if (tx.transactionTime().isAfter(evaluatedAt)
        || tx.postedAt() != null
            && (tx.postedAt().isBefore(tx.transactionTime())
                || tx.postedAt().isAfter(evaluatedAt))) {
      throw new BusinessException(
          ErrorCode.INVALID_REQUEST,
          "Transaction must have occurred; postedAt must be between transactionTime and evaluation time");
    }
    if (state.watermark() != null && tx.transactionTime().isBefore(state.watermark())) {
      throw new BusinessException(
          ErrorCode.RESOURCE_CONFLICT,
          "Late transaction: transactionTime precedes the global ingest watermark");
    }
  }
}

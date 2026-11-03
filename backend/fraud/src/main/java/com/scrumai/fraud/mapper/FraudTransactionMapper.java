package com.scrumai.fraud.mapper;

import com.scrumai.fraud.dto.FraudRequests;
import com.scrumai.fraud.dto.FraudResponses;
import com.scrumai.fraud.entity.FraudRuleHitEntity;
import com.scrumai.fraud.entity.FraudTransactionEntity;
import com.scrumai.fraud.entity.TransactionChainEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FraudTransactionMapper {

  public FraudTransactionEntity toEntity(FraudRequests.Create request) {
    FraudTransactionEntity entity = new FraudTransactionEntity();
    entity.setTransactionRef(request.transactionRef());
    entity.setFromAccountRef(request.fromAccountRef());
    entity.setFromAccountMasked(request.fromAccountMasked());
    entity.setFromCustomerName(request.fromCustomerName());
    entity.setToAccountRef(request.toAccountRef());
    entity.setToAccountMasked(request.toAccountMasked());
    entity.setToCustomerName(request.toCustomerName());
    entity.setAmount(request.amount());
    entity.setCurrency(request.currency());
    entity.setTransactionTime(request.transactionTime());
    entity.setRiskScore(request.riskScore());
    entity.setRiskLevel(request.riskLevel());
    entity.setFraudReason(request.fraudReason());
    for (FraudRequests.RuleHit hit : request.ruleHits()) {
      FraudRuleHitEntity child = new FraudRuleHitEntity();
      child.setRuleCode(hit.ruleCode());
      child.setRuleName(hit.ruleName());
      child.setScoreContribution(hit.scoreContribution());
      child.setExplanation(hit.explanation());
      entity.addRuleHit(child);
    }
    for (FraudRequests.ChainEntry entry : request.transactionChain()) {
      TransactionChainEntity child = new TransactionChainEntity();
      child.setTransactionRef(entry.transactionRef());
      child.setFromAccountRef(entry.fromAccountRef());
      child.setFromAccountMasked(entry.fromAccountMasked());
      child.setFromCustomerName(entry.fromCustomerName());
      child.setToAccountRef(entry.toAccountRef());
      child.setToAccountMasked(entry.toAccountMasked());
      child.setToCustomerName(entry.toCustomerName());
      child.setAmount(entry.amount());
      child.setCurrency(entry.currency());
      child.setTransactionTime(entry.transactionTime());
      child.setHopLevel(entry.hopLevel());
      child.setRoot(entry.root());
      entity.addTransactionChainEntry(child);
    }
    return entity;
  }

  public FraudResponses.Created toCreated(FraudTransactionEntity entity) {
    return new FraudResponses.Created(
        entity.getId(),
        entity.getTransactionRef(),
        entity.getReviewStatus(),
        entity.getCreatedAt());
  }

  public FraudResponses.SummaryItem toSummary(FraudTransactionEntity entity) {
    return new FraudResponses.SummaryItem(
        entity.getId(),
        entity.getTransactionRef(),
        entity.getFromAccountMasked(),
        entity.getToAccountMasked(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getTransactionTime(),
        entity.getRiskScore(),
        entity.getRiskLevel(),
        entity.getReviewStatus(),
        entity.getCreatedAt());
  }

  public FraudResponses.Detail toDetail(
      FraudTransactionEntity entity, List<FraudRuleHitEntity> hits) {
    return new FraudResponses.Detail(
        entity.getId(),
        entity.getTransactionRef(),
        new FraudResponses.Account(
            entity.getFromAccountRef(),
            entity.getFromAccountMasked(),
            entity.getFromCustomerName()),
        new FraudResponses.Account(
            entity.getToAccountRef(), entity.getToAccountMasked(), entity.getToCustomerName()),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getTransactionTime(),
        entity.getRiskScore(),
        entity.getRiskLevel(),
        entity.getFraudReason(),
        entity.getReviewStatus(),
        entity.getCreatedAt(),
        hits.stream().map(this::toRuleHit).toList());
  }

  public FraudResponses.RuleHit toRuleHit(FraudRuleHitEntity entity) {
    return new FraudResponses.RuleHit(
        entity.getId(),
        entity.getRuleCode(),
        entity.getRuleName(),
        entity.getScoreContribution(),
        entity.getExplanation());
  }

  public FraudResponses.GraphEdge toEdge(TransactionChainEntity entity) {
    return new FraudResponses.GraphEdge(
        entity.getId(),
        entity.getTransactionRef(),
        entity.getFromAccountRef(),
        entity.getToAccountRef(),
        entity.getAmount(),
        entity.getCurrency(),
        entity.getTransactionTime(),
        entity.getHopLevel(),
        entity.isRoot());
  }
}

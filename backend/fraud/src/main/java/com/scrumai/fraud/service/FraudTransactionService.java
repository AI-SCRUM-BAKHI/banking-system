package com.scrumai.fraud.service;

import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.fraud.dto.FraudRequests;
import com.scrumai.fraud.dto.FraudResponses;
import com.scrumai.fraud.entity.FraudTransactionEntity;
import com.scrumai.fraud.entity.TransactionChainEntity;
import com.scrumai.fraud.enums.ReviewStatus;
import com.scrumai.fraud.enums.RiskLevel;
import com.scrumai.fraud.mapper.FraudTransactionMapper;
import com.scrumai.fraud.repository.FraudRuleHitRepository;
import com.scrumai.fraud.repository.FraudTransactionRepository;
import com.scrumai.fraud.repository.TransactionChainRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudTransactionService {

  private static final Set<String> SORT_FIELDS =
      Set.of("createdAt", "transactionTime", "riskScore", "amount", "transactionRef");

  private final FraudTransactionRepository transactions;
  private final FraudRuleHitRepository ruleHits;
  private final TransactionChainRepository chain;
  private final FraudTransactionMapper mapper;

  public FraudTransactionService(
      FraudTransactionRepository transactions,
      FraudRuleHitRepository ruleHits,
      TransactionChainRepository chain,
      FraudTransactionMapper mapper) {
    this.transactions = transactions;
    this.ruleHits = ruleHits;
    this.chain = chain;
    this.mapper = mapper;
  }

  @Transactional
  public FraudResponses.Created create(FraudRequests.Create request) {
    validateAggregate(request);
    if (transactions.existsByTransactionRef(request.transactionRef())) {
      throw new BusinessException(
          ErrorCode.RESOURCE_CONFLICT, "Transaction reference already exists");
    }
    try {
      return mapper.toCreated(transactions.saveAndFlush(mapper.toEntity(request)));
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(
          ErrorCode.RESOURCE_CONFLICT, "Fraud transaction conflicts with existing data");
    }
  }

  @Transactional(readOnly = true)
  public FraudResponses.Page<FraudResponses.SummaryItem> list(
      int page,
      int size,
      RiskLevel riskLevel,
      ReviewStatus reviewStatus,
      String keyword,
      Instant from,
      Instant to,
      String sortField,
      Sort.Direction direction) {
    validateRange(from, to);
    if (page < 0 || size < 1 || size > 100) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "page must be >= 0 and size 1..100");
    }
    if (!SORT_FIELDS.contains(sortField)) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported sort field");
    }
    PageRequest paging =
        PageRequest.of(
            page, size, Sort.by(direction, sortField).and(Sort.by(Sort.Direction.DESC, "id")));
    Page<FraudTransactionEntity> results =
        transactions.findAll(filter(riskLevel, reviewStatus, keyword, from, to), paging);
    return new FraudResponses.Page<>(
        results.getContent().stream().map(mapper::toSummary).toList(),
        results.getNumber(),
        results.getSize(),
        results.getTotalElements(),
        results.getTotalPages());
  }

  @Transactional(readOnly = true)
  public FraudResponses.Detail detail(UUID id) {
    FraudTransactionEntity entity = find(id);
    return mapper.toDetail(entity, ruleHits.findAllByFraudTransaction_IdOrderByCreatedAtAsc(id));
  }

  @Transactional(readOnly = true)
  public List<FraudResponses.RuleHit> ruleHits(UUID id) {
    find(id);
    return ruleHits.findAllByFraudTransaction_IdOrderByCreatedAtAsc(id).stream()
        .map(mapper::toRuleHit)
        .toList();
  }

  @Transactional(readOnly = true)
  public FraudResponses.Graph graph(UUID id) {
    find(id);
    Map<String, FraudResponses.GraphNode> nodes = new LinkedHashMap<>();
    List<FraudResponses.GraphEdge> edges = new ArrayList<>();
    for (TransactionChainEntity entry :
        chain.findAllByRootFraudTransaction_IdOrderByHopLevelAscTransactionTimeAsc(id)) {
      nodes.putIfAbsent(
          entry.getFromAccountRef(),
          new FraudResponses.GraphNode(
              entry.getFromAccountRef(),
              entry.getFromAccountMasked(),
              entry.getFromCustomerName()));
      nodes.putIfAbsent(
          entry.getToAccountRef(),
          new FraudResponses.GraphNode(
              entry.getToAccountRef(), entry.getToAccountMasked(), entry.getToCustomerName()));
      edges.add(mapper.toEdge(entry));
    }
    return new FraudResponses.Graph(List.copyOf(nodes.values()), edges);
  }

  @Transactional
  public FraudResponses.ReviewResult review(UUID id, ReviewStatus target) {
    FraudTransactionEntity entity = find(id);
    ReviewStatus current = entity.getReviewStatus();
    boolean valid =
        current == ReviewStatus.NEW && target == ReviewStatus.REVIEWING
            || current == ReviewStatus.REVIEWING
                && (target == ReviewStatus.CONFIRMED_FRAUD
                    || target == ReviewStatus.FALSE_POSITIVE);
    if (!valid) {
      throw new BusinessException(
          ErrorCode.RESOURCE_CONFLICT,
          "Invalid review status transition: " + current + " -> " + target);
    }
    entity.setReviewStatus(target);
    return new FraudResponses.ReviewResult(id, target);
  }

  @Transactional(readOnly = true)
  public FraudResponses.Dashboard summary(Instant from, Instant to) {
    validateRange(from, to);
    Specification<FraudTransactionEntity> period = filter(null, null, null, from, to);
    long total = transactions.count(period);
    return new FraudResponses.Dashboard(
        total,
        transactions.count(filter(null, ReviewStatus.NEW, null, from, to)),
        transactions.count(filter(null, ReviewStatus.REVIEWING, null, from, to)),
        transactions.count(filter(null, ReviewStatus.CONFIRMED_FRAUD, null, from, to)),
        transactions.count(filter(null, ReviewStatus.FALSE_POSITIVE, null, from, to)),
        transactions.count(filter(RiskLevel.HIGH, null, null, from, to)),
        transactions.count(filter(RiskLevel.CRITICAL, null, null, from, to)));
  }

  private FraudTransactionEntity find(UUID id) {
    return transactions
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Fraud transaction not found"));
  }

  private void validateAggregate(FraudRequests.Create request) {
    Set<String> codes = new HashSet<>();
    for (FraudRequests.RuleHit hit : request.ruleHits()) {
      if (!codes.add(hit.ruleCode())) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "Duplicate rule code in request");
      }
    }
    Set<String> refs = new HashSet<>();
    int roots = 0;
    for (FraudRequests.ChainEntry entry : request.transactionChain()) {
      if (!refs.add(entry.transactionRef())) {
        throw new BusinessException(
            ErrorCode.INVALID_REQUEST, "Duplicate transaction reference in chain");
      }
      if (entry.hopLevel() < 0 || entry.root() != (entry.hopLevel() == 0)) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid chain root or hop level");
      }
      if (entry.root()) {
        roots++;
        if (!entry.transactionRef().equals(request.transactionRef())
            || !entry.fromAccountRef().equals(request.fromAccountRef())
            || !entry.toAccountRef().equals(request.toAccountRef())
            || entry.amount().compareTo(request.amount()) != 0
            || !entry.currency().equals(request.currency())
            || !entry.transactionTime().equals(request.transactionTime())) {
          throw new BusinessException(
              ErrorCode.INVALID_REQUEST, "Root chain entry must match the fraud transaction");
        }
      }
    }
    if (roots != 1) {
      throw new BusinessException(
          ErrorCode.INVALID_REQUEST, "Transaction chain must contain exactly one root");
    }
  }

  private void validateRange(Instant from, Instant to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "from must be before or equal to to");
    }
  }

  private Specification<FraudTransactionEntity> filter(
      RiskLevel risk, ReviewStatus status, String keyword, Instant from, Instant to) {
    return (root, query, builder) -> {
      List<Predicate> conditions = new ArrayList<>();
      if (risk != null) {
        conditions.add(builder.equal(root.get("riskLevel"), risk));
      }
      if (status != null) {
        conditions.add(builder.equal(root.get("reviewStatus"), status));
      }
      if (from != null) {
        conditions.add(builder.greaterThanOrEqualTo(root.get("transactionTime"), from));
      }
      if (to != null) {
        conditions.add(builder.lessThanOrEqualTo(root.get("transactionTime"), to));
      }
      if (keyword != null && !keyword.isBlank()) {
        String pattern =
            "%"
                + keyword
                    .trim()
                    .toLowerCase()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_")
                + "%";
        conditions.add(
            builder.or(
                builder.like(builder.lower(root.get("transactionRef")), pattern, '\\'),
                builder.like(builder.lower(root.get("fromAccountMasked")), pattern, '\\'),
                builder.like(builder.lower(root.get("toAccountMasked")), pattern, '\\')));
      }
      return builder.and(conditions.toArray(Predicate[]::new));
    };
  }
}

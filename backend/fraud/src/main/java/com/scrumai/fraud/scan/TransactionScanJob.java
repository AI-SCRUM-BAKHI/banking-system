package com.scrumai.fraud.scan;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.fraud.scan.enabled", havingValue = "true")
public class TransactionScanJob {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionScanJob.class);
  private final TransactionScanLock lock;
  private final TransactionScanRepository repository;
  private final TransactionScanProcessor processor;
  private final int batchSize;

  public TransactionScanJob(
      TransactionScanLock lock,
      TransactionScanRepository repository,
      TransactionScanProcessor processor,
      @Value("${app.fraud.scan.batch-size:100}") int batchSize) {
    if (batchSize < 1 || batchSize > 100) {
      throw new IllegalArgumentException("Transaction scan batch size must be between 1 and 100");
    }
    this.lock = lock;
    this.repository = repository;
    this.processor = processor;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${app.fraud.scan.fixed-delay-ms:30000}")
  public void scan() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    try (var lease = lock.tryAcquire()) {
      if (lease == null) {
        return;
      }
      for (String ref : repository.candidates(batchSize)) {
        lease.verify();
        try {
          counts.merge(processor.process(ref), 1, Integer::sum);
        } catch (RuntimeException exception) {
          // If even failure bookkeeping is unavailable, leave the row eligible for the next run.
          counts.merge("UNRECORDED_ERROR", 1, Integer::sum);
          LOG.error(
              "Transaction scan bookkeeping failed ref={} errorType={}",
              ref,
              exception.getClass().getSimpleName());
        }
      }
      LOG.info("Transaction scan finished counts={}", counts);
    } catch (Exception exception) {
      LOG.error(
          "Transaction scan stopped counts={} errorType={}",
          counts,
          exception.getClass().getSimpleName());
    }
  }
}

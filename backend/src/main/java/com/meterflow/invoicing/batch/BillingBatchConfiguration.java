package com.meterflow.invoicing.batch;

import com.meterflow.invoicing.BillingService;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import java.io.Serializable;

/**
 * QA3: customer-partitioned, chunk-committed billing job. Progress is checkpointed per chunk in the
 * Spring Batch job repository; re-launching the same period after a crash resumes from the last
 * committed chunk. Each customer's invoice is idempotent (existence check + unique constraint),
 * so even a chunk that is replayed cannot create a second invoice. A customer whose data is
 * invalid is skipped and logged instead of stopping the other customers.
 */
@Configuration
public class BillingBatchConfiguration {
    private static final int GRID_SIZE = 8;
    private static final int CHUNK_SIZE = 25;
    private static final int SKIP_LIMIT = 1000;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BillingBatchConfiguration.class);

    @Bean Partitioner customerBillingPartitioner(SubscriptionRepository subscriptions) {
        return gridSize -> {
            // Sorted, de-duplicated customer list: partitions are deterministic for a given data set.
            // Active customers plus those paused, blocked or cancelled since they were last active;
            // the run skips anyone with nothing to bill for the period.
            List<String> customers = subscriptions.findBillingCandidateCustomerIds();
            int partitions = Math.min(Math.max(1, gridSize), Math.max(1, customers.size()));
            Map<String, ExecutionContext> result = new LinkedHashMap<>();
            for (int i=0; i<partitions; i++) {
                List<String> slice = new ArrayList<>();
                for (int j=i; j<customers.size(); j+=partitions) slice.add(customers.get(j));
                ExecutionContext context = new ExecutionContext();
                context.put("customerIds", (Serializable) slice);
                result.put("customers-"+i, context);
            }
            return result;
        };
    }

    @Bean @StepScope ItemStreamReader<String> billingCustomerReader(
            @Value("#{stepExecutionContext['customerIds']}") List<String> customerIds) {
        return new PartitionCustomerReader(customerIds);
    }

    @Bean @StepScope ItemWriter<String> billingCustomerWriter(
            @Value("#{jobParameters['period']}") String period, BillingService billing) {
        return chunk -> { for (String customer : chunk) billing.runCustomer(period, customer); };
    }

    @Bean SkipListener<String, String> billingSkipListener() {
        return new SkipListener<>() {
            @Override public void onSkipInWrite(String customer, Throwable t) {
                log.warn("Billing skipped customer {}: {}", customer, t.getMessage());
            }
        };
    }

    @Bean Step billingWorkerStep(JobRepository repository, PlatformTransactionManager transactionManager,
            ItemStreamReader<String> billingCustomerReader, ItemWriter<String> billingCustomerWriter,
            SkipListener<String, String> billingSkipListener) {
        return new StepBuilder("billingWorkerStep", repository)
                .<String,String>chunk(CHUNK_SIZE, transactionManager)
                .reader(billingCustomerReader).writer(billingCustomerWriter)
                .faultTolerant()
                .skip(IllegalArgumentException.class).skip(IllegalStateException.class)
                .skipLimit(SKIP_LIMIT)
                .listener(billingSkipListener)
                .build();
    }

    @Bean Step billingManagerStep(JobRepository repository, Partitioner customerBillingPartitioner, Step billingWorkerStep) throws Exception {
        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(billingWorkerStep); handler.setGridSize(GRID_SIZE);
        handler.setTaskExecutor(new SimpleAsyncTaskExecutor("meterflow-billing-")); handler.afterPropertiesSet();
        return new StepBuilder("billingManagerStep", repository)
                .partitioner("billingWorkerStep", customerBillingPartitioner).partitionHandler(handler).build();
    }

    @Bean Job billingJob(JobRepository repository, Step billingManagerStep) {
        return new JobBuilder("meterflowBillingJob", repository).start(billingManagerStep).build();
    }
}

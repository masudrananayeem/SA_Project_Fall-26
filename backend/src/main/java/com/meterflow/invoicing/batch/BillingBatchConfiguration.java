package com.meterflow.invoicing.batch;

import com.meterflow.invoicing.BillingService;
import com.meterflow.subscriptions.SubscriptionRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Chunk-oriented and partitioned billing: customer IDs are sharded across workers and committed in chunks. */
@Configuration
public class BillingBatchConfiguration {
    @Bean
    Partitioner customerBillingPartitioner(SubscriptionRepository subscriptions) {
        return gridSize -> {
            List<String> customers = subscriptions.findByStatus("ACTIVE").stream()
                    .map(s -> s.getCustomerId()).distinct().sorted().toList();
            int partitionCount = Math.max(1, Math.min(gridSize, customers.size()));
            Map<String, org.springframework.batch.item.ExecutionContext> partitions = new LinkedHashMap<>();
            for (int i = 0; i < partitionCount; i++) {
                List<String> shard = new ArrayList<>();
                for (int j = i; j < customers.size(); j += partitionCount) shard.add(customers.get(j));
                var context = new org.springframework.batch.item.ExecutionContext();
                context.put("customerIds", shard);
                partitions.put("customer-shard-" + i, context);
            }
            return partitions;
        };
    }

    @Bean
    @StepScope
    CustomerIdItemReader customerIdItemReader(@Value("#{stepExecutionContext['customerIds']}") List<String> customerIds) {
        return new CustomerIdItemReader(customerIds);
    }

    @Bean
    @StepScope
    ItemProcessor<String,String> customerBillingProcessor(BillingService billing,
            @Value("#{jobParameters['period']}") String period) {
        return customerId -> {
            billing.runCustomer(period, customerId);
            return customerId;
        };
    }

    @Bean
    Step billingWorkerStep(JobRepository repository, PlatformTransactionManager transactionManager,
                           CustomerIdItemReader customerIdItemReader,
                           ItemProcessor<String,String> customerBillingProcessor) {
        ItemWriter<String> auditWriter = chunk -> { /* BillingService commits invoice + credit use per customer. */ };
        return new StepBuilder("billingWorkerStep", repository)
                .<String,String>chunk(25, transactionManager)
                .reader(customerIdItemReader)
                .processor(customerBillingProcessor)
                .writer(auditWriter)
                .build();
    }

    @Bean
    Step billingManagerStep(JobRepository repository, Partitioner customerBillingPartitioner, Step billingWorkerStep) throws Exception {
        var handler = new TaskExecutorPartitionHandler();
        handler.setStep(billingWorkerStep);
        handler.setGridSize(8);
        handler.setTaskExecutor(new SimpleAsyncTaskExecutor("meterflow-billing-"));
        handler.afterPropertiesSet();
        return new StepBuilder("billingManagerStep", repository)
                .partitioner("billingWorkerStep", customerBillingPartitioner)
                .partitionHandler(handler).build();
    }

    @Bean
    Job billingJob(JobRepository repository, Step billingManagerStep) {
        return new JobBuilder("meterflowBillingJob", repository).start(billingManagerStep).build();
    }
}

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

/** Customer-partitioned, chunk-committed billing. Each chunk is independently restartable. */
@Configuration
public class BillingBatchConfiguration {
    private static final int GRID_SIZE = 8;
    private static final int CHUNK_SIZE = 25;

    @Bean Partitioner customerBillingPartitioner(SubscriptionRepository subscriptions) {
        return gridSize -> {
            List<String> customers = subscriptions.findByStatus("ACTIVE").stream()
                    .map(s -> s.getCustomerId()).distinct().sorted().toList();
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

    @Bean Step billingWorkerStep(JobRepository repository, PlatformTransactionManager transactionManager,
            ItemStreamReader<String> billingCustomerReader, ItemWriter<String> billingCustomerWriter) {
        return new StepBuilder("billingWorkerStep", repository)
                .<String,String>chunk(CHUNK_SIZE, transactionManager)
                .reader(billingCustomerReader).writer(billingCustomerWriter).build();
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

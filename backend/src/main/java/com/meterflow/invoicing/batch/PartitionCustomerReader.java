package com.meterflow.invoicing.batch;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import java.util.List;

/** Restartable partition reader. The current index is checkpointed at each committed chunk. */
public final class PartitionCustomerReader implements ItemStreamReader<String> {
    private static final String INDEX_KEY = "meterflow.customer.index";
    private final List<String> customers;
    private int index;
    public PartitionCustomerReader(List<String> customers) { this.customers = List.copyOf(customers); }
    @Override public void open(ExecutionContext context) { this.index = context.containsKey(INDEX_KEY) ? context.getInt(INDEX_KEY) : 0; }
    @Override public String read() { return index < customers.size() ? customers.get(index++) : null; }
    @Override public void update(ExecutionContext context) { context.putInt(INDEX_KEY, index); }
    @Override public void close() { }
}

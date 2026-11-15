package com.meterflow.invoicing.batch;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;
import java.util.List;

/** Restart-aware reader for the customer IDs assigned to one billing partition. */
public class CustomerIdItemReader implements ItemStreamReader<String> {
    private static final String INDEX_KEY = "meterflow.customer.reader.index";
    private final List<String> customerIds;
    private int index;

    public CustomerIdItemReader(List<String> customerIds) {
        this.customerIds = customerIds == null ? List.of() : List.copyOf(customerIds);
    }

    @Override public void open(ExecutionContext context) {
        this.index = context.containsKey(INDEX_KEY) ? context.getInt(INDEX_KEY) : 0;
        if (index < 0 || index > customerIds.size()) throw new IllegalStateException("Invalid customer reader restart index");
    }

    @Override public String read() {
        if (index >= customerIds.size()) return null;
        return customerIds.get(index++);
    }

    @Override public void update(ExecutionContext context) { context.putInt(INDEX_KEY, index); }
    @Override public void close() { }
}

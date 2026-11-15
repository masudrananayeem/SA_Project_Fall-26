package com.meterflow.invoicing.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PartitionCustomerReaderTest {
    @Test void checkpointsAndResumesAtNextCustomer() throws Exception {
        var reader = new PartitionCustomerReader(List.of("c-1", "c-2", "c-3"));
        var context = new ExecutionContext();
        reader.open(context);
        assertEquals("c-1", reader.read());
        assertEquals("c-2", reader.read());
        reader.update(context);
        var resumed = new PartitionCustomerReader(List.of("c-1", "c-2", "c-3"));
        resumed.open(context);
        assertEquals("c-3", resumed.read());
        assertNull(resumed.read());
    }
}

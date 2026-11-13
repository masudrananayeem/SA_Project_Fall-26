package com.meterflow.invoicing;
import java.math.*;
public class InvoiceBuilder {private String customer,period,currency;private BigDecimal subtotal=BigDecimal.ZERO,tax=BigDecimal.ZERO;public InvoiceBuilder customer(String x){customer=x;return this;}public InvoiceBuilder period(String x){period=x;return this;}public InvoiceBuilder currency(String x){currency=x;return this;}public InvoiceBuilder subtotal(BigDecimal x){subtotal=x;return this;}public InvoiceBuilder tax(BigDecimal x){tax=x;return this;}public Invoice build(){return new Invoice(customer,period,currency,subtotal,tax);}}

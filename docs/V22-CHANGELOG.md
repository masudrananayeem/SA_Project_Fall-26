# V22 changelog: no payment without the customer

## Problem
In V21, as soon as an invoice was finalised the payments module charged the mock "card on file"
(`PaymentService.on(InvoiceFinalised)` → `collect`). Every invoice therefore became PAID without the
customer doing anything.

## Behaviour now
- A finalised invoice stays **FINALISED**, shown as **"Payment pending"**, until the customer pays it. Nothing is charged automatically.
- **Customer portal:** "Pay now" (overview banner) and "Pay" (invoice) open a checkout dialog. The customer chooses a method and confirms; only then is `POST /api/payments/mock/{id}/pay` called.
  - Test mode: no card or wallet details are collected and no real money moves.
- **Admin console:**
  - "Mark as paid" (asks for confirmation) records a payment received outside MeterFlow.
  - "Charge card on file" (`/collect`) is an explicit admin action. If the provider declines, dunning starts (FR8).
  - "Simulate failed payment" still starts dunning for testing.
- Invoices with nothing to pay (total 0, for example fully covered by credit) are closed as PAID automatically, because nobody is charged.
- Dunning retries charge only invoices whose payment already failed, as before.
- Old behaviour, if wanted: set `METERFLOW_AUTO_COLLECT=true` (`meterflow.payments.auto-collect`).

## Tests
- `smoke-test.ps1`: after billing, the invoice must still be pending; it becomes PAID only after the payment call.
- `feature-test.ps1`: billing creates no payment attempt and no dunning case; an explicit admin charge with a declining card opens dunning.

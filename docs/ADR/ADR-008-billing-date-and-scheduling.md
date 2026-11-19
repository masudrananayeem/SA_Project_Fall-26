# ADR-008: Billing date, prepaid packages and scheduled jobs on Cloudflare

## Status
Accepted (V23).

## Context
- FR5 asks for a billing run "at each customer's billing date".
- V23 adds prepaid plans that must be paid before use.
- On Cloudflare the Spring Boot container sleeps after 30 idle minutes. While it sleeps,
  Spring `@Scheduled` jobs (billing, trials, dunning retries, renewals, account standing) do not fire.

## Decision
1. **One billing date for every customer: calendar months (UTC).**
   - Postpaid usage is billed on the 1st at 00:30 UTC for the month that just closed.
   - Mid-month starts and plan changes are handled by proration (`PlanChangeService`) and the trial rule (`PricingService.quoteForPeriod`).
   - Usage, budgets, late events (72 h after close) and live estimates all use the same period, which keeps QA1/QA5 reconciliation exact.
2. **Prepaid billing date is the day the customer subscribes, then the 1st of each month.**
   - `PrepaidService` issues the package invoice for the current month when the subscription starts.
   - It issues the next one on renewal.
   - The month-end run finds that invoice and returns `EXISTING`, so the month is never billed twice.
3. **Every scheduled job is also an idempotent ADMIN endpoint.**
   - A Cloudflare Worker cron trigger (`15 0 * * *`) wakes the container and calls these endpoints:
     - `POST /api/subscriptions/lifecycle/run`
     - `POST /api/invoices/batch/run?period=<previous month>` (on the 1st only)
     - `POST /api/invoices/prepaid/renew`
     - `POST /api/dunning/process`
     - `POST /api/payments/standing/enforce`
   - Locally the Spring schedulers keep running as before.

## Consequences
- **Advantage:** simple, explainable invoices; one period key `(customer, YYYY-MM)` guards against duplicates.
- **Advantage:** jobs run on time on Cloudflare without keeping the container awake (low cost).
- **Trade-off:** customers cannot pick an individual anniversary date. Supporting one would need a per-subscription anchor day in the period key and in the batch partitioning. This is a contained change in `AbstractBillingRun.periodFor`, the reader and the unique key.
- **Trade-off:** a prepaid package bought late in a month is charged for the whole month.

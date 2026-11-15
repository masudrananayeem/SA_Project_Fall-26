# MeterFlow V16 — Functional gap closure

**Baseline:** V15 source tree.

## Implemented in this source revision

1. Dunning retry processing now propagates terminal `SUSPENDED` state to the customer's active subscription by moving it to a dedicated `PAUSED_DUNNING` state (manual `PAUSED` subscriptions are not resumed). A recorded successful payment transitions the dunning case to `REACTIVATED` and resumes the customer's paused subscription. Both the explicit success path and the success-if-present path perform the same access restoration.
2. Subscription lifecycle processing now expires trials idempotently on/after the configured `trialEnd`, while retaining scheduled plan-change processing in the same daily transaction. Once the trial marker is cleared, the normal billing path can charge the subscription rather than continuing to treat the period as trial-covered.
3. Added a unit test for exact-date trial expiration and strengthened dunning schedule assertions.
4. Updated the master criteria matrix to mark FR2 source implementation as implemented and to describe FR4/FR8 as improved but not yet fully accepted.

## Verification limits

Java 21 is available in the packaging environment, but Maven is not installed. Therefore `mvn verify`, Java compilation, JUnit execution, PostgreSQL integration, and Docker-based tests have **not** been run. This revision does not claim that QA1–QA5 pass. Cloudflare/Vercel deployment and live OIDC/webhook egress acceptance still require external accounts, secrets and target infrastructure.

## Remaining work that cannot honestly be marked complete

- FR1: automatically apply late-event adjustments to a subsequent invoice with an auditable invoice line, including concurrency/replay tests.
- FR3: complete mixed-charge allowance/minimum commitment semantics and exhaustive currency/tier boundaries.
- FR4: end-to-end period-boundary lifecycle/proration tests and overlapping subscription policy.
- FR5: complete per-charge/tax breakdown and invoice-estimate reconciliation.
- FR6: refund, reversal and dispute lifecycle.
- FR7: independent <=1-minute estimate refresh and measured parity.
- FR8: provider adapter and verified scheduled retry acceptance.
- FR9: socket-level DNS pinning/controlled egress proxy and receiver tests.
- FR10: immutable plan snapshots and faithful historical replay/migration dry-run.
- QA1/QA2/QA3/QA5: execute the documented workload/acceptance tests and retain raw output; do not fabricate performance evidence.
- Cloudflare/Vercel: deploy using the owner's accounts and run smoke tests.

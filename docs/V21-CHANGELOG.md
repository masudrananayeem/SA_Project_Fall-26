# V21 changelog: dark mode and full feature test

## Dark mode
- Both apps have light and dark themes.
  - Toggle: sun/moon button in the admin top bar, in the portal header and on the login pages.
  - The first visit follows the operating-system setting; after that the choice is saved per browser (`localStorage`, key `meterflow.theme`).
  - An inline script in `index.html` sets the theme before the first paint, so the page does not flash.
- Implementation:
  - All colours are CSS variables (`packages/shared/src/styles.css`); `html[data-theme='dark']` swaps them.
  - Each app has its own dark accent (indigo for admin, emerald for the portal) plus a darker solid accent for buttons, to keep white text readable.
  - Charts take their colours from `chartPalette(theme, app)` in `packages/shared/src/theme.tsx`.

## Full feature test (`scripts/feature-test.ps1`)
One command checks every backend feature the two apps use, against the running backend (local or Cloudflare). Each run uses new IDs, so it can be repeated.

| Area | What it checks |
|---|---|
| Login | `/api/me`; wrong password gives 401 with no `WWW-Authenticate` (no browser popup) |
| Catalogue | Charge types; plan v1 → v2; version history; flat + graduated tier plan; invalid tiers rejected |
| Subscriptions | Create; a second active one is refused (409); seats; pause/resume; change plan now (prorated) and at period end; history; paging |
| Metering | 40 events, re-send = 40 duplicates; future event rejected; sum, count, maximum, unique_count, latest_value; property filter; summary; late ledger |
| Live spend | Estimate; simulator under another plan; version comparison |
| Billing and dunning | Declining provider; bill twice (CREATED then EXISTING); finalised but unpaid; retry fast-forward; pay → PAID; case closed; line trace (QA5); text and CSV render |
| Batch | Partitioned job for last month; stale-run recovery |
| Credits | Top-up + credit note = balance 60.00 |
| Budgets | Over-budget evaluation; notifications `invoice.finalised`, `payment.failed`, `payment.succeeded`, `usage.threshold_reached` |
| Webhooks | Register, list, delivery log |
| Migration | Dry-run impact |
| Self-service | Sign-up; existing customer ID refused; profile update; self-subscribe; scheduled change; own data only; 403 on other customers' data and admin endpoints; cancel |
| Admin accounts | Customer list; login for an existing customer ID; disable gives 401; re-enable |

Run: `powershell -ExecutionPolicy Bypass -File scripts\feature-test.ps1`. It must end with `ALL FEATURES PASSED`.

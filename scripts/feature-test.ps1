# MeterFlow full feature test: calls every API that the admin console and the customer portal use,
# against a running backend, and checks the results. Windows PowerShell 5.1 or PowerShell 7.
#   Local:      powershell -ExecutionPolicy Bypass -File scripts/feature-test.ps1
#   Cloudflare: powershell -ExecutionPolicy Bypass -File scripts/feature-test.ps1 -BaseUrl https://meterflow-api.<you>.workers.dev -Password <admin password>
# Every run uses fresh customer IDs, so it can be repeated. It ends with "ALL FEATURES PASSED".
param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$User = "admin",
  [string]$Password = "meterflow-admin"
)
$ErrorActionPreference = "Stop"
$script:failed = 0
$script:passed = 0
function Basic($u, $p) { "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${u}:${p}")) }
$admin = @{ Authorization = (Basic $User $Password); "X-Requested-With" = "XMLHttpRequest" }
function Step($name) { Write-Host "`n== $name" -ForegroundColor Cyan }
function Ok($msg) { Write-Host "  PASS $msg" -ForegroundColor Green; $script:passed++ }
function Bad($msg) { Write-Host "  FAIL $msg" -ForegroundColor Red; $script:failed++ }
function Check($cond, $msg, $detail = "") { if ($cond) { Ok $msg } else { Bad "$msg $detail" } }
function Api($method, $path, $body = $null, $headers = $admin) {
  $p = @{ Method = $method; Uri = "$BaseUrl$path"; Headers = $headers; ContentType = "application/json"; TimeoutSec = 300 }
  if ($null -ne $body) { $p.Body = ($body | ConvertTo-Json -Depth 10) }
  return Invoke-RestMethod @p
}
# Runs a call that must fail and returns the HTTP status code (0 if it did not fail).
function StatusOf([scriptblock]$call) {
  try { & $call | Out-Null; return 0 } catch {
    $r = $_.Exception.Response
    if ($null -eq $r) { return -1 }
    return [int]$r.StatusCode
  }
}
function Safe($name, [scriptblock]$block) {
  try { & $block } catch { Bad "$name threw: $($_.Exception.Message)" }
}

$run = Get-Random -Maximum 999999
$period = (Get-Date).ToUniversalTime().ToString("yyyy-MM")
$prevPeriod = (Get-Date).ToUniversalTime().AddMonths(-1).ToString("yyyy-MM")
$today = (Get-Date).ToUniversalTime()
$monthStart = $today.ToString("yyyy-MM-01'T'00:00:00'Z'")
$nowPlus = $today.AddMinutes(5).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
$cust = "ft-$run"
$keyA = "ft-a-$run"
$keyB = "ft-b-$run"

Step "Health and login"
Safe "health" { $h = Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 300; Check ($h.status -eq "UP") "backend UP" }
Safe "me" { $me = Api GET "/api/me"; Check ($me.roles -contains "ADMIN" -and $null -eq $me.customerId) "/api/me = ADMIN" }
$bad = @{ Authorization = (Basic $User "wrong-password"); "X-Requested-With" = "XMLHttpRequest" }
Check ((StatusOf { Api GET "/api/me" $null $bad }) -eq 401) "wrong password -> 401"
try {
  Invoke-WebRequest -Uri "$BaseUrl/api/me" -Headers $bad -UseBasicParsing -TimeoutSec 60 | Out-Null
  Bad "wrong password accepted"
} catch {
  $hdrs = $_.Exception.Response.Headers
  $www = $null
  try { $www = $hdrs["WWW-Authenticate"] } catch { }
  if (-not $www) { try { $www = ($hdrs.WwwAuthenticate | Out-String).Trim() } catch { } }
  Check (-not $www) "browser apps get no WWW-Authenticate (no popup)"
}

Step "Catalogue: plans, versions, charge components (FR3, FR10)"
Safe "catalogue" {
  $types = Api GET "/api/catalogue/plans/charge-types"
  Check (@($types.types).Count -ge 6) "charge types listed ($(@($types.types) -join ', '))"
  $simple = @{ planKey = $keyA; name = "FT Simple"; currency = "BDT"; flatFee = 100; unitPrice = 0.5; metric = "api_calls"; freeAllowance = 10; minimumCommitment = 0; taxRate = 15 }
  $script:a1 = Api POST "/api/catalogue/plans" $simple
  $simple.flatFee = 80
  $script:a2 = Api PUT "/api/catalogue/plans/$keyA" $simple
  Check ($a1.version -eq 1 -and $a2.version -eq 2) "plan $keyA v1 -> v2"
  $versions = Api GET "/api/catalogue/plans/$keyA/versions"
  Check (@($versions).Count -eq 2) "version history has 2 versions"
  $tiered = @{ planKey = $keyB; name = "FT Tiered"; currency = "BDT"; flatFee = 0; unitPrice = 0; metric = "api_calls"; freeAllowance = 0; minimumCommitment = 0; taxRate = 15;
    aggregation = "sum"; charges = @(
      @{ type = "flat_fee"; name = "Platform"; amount = 200 },
      @{ type = "graduated_tier"; name = "Calls"; amount = 0; includedUnits = 0; tiers = @(@{ upTo = 20; unitPrice = 1 }, @{ upTo = $null; unitPrice = 0.5 }) }
    ) }
  $script:b1 = Api POST "/api/catalogue/plans" $tiered
  Check ($b1.chargeDefinitionsJson -match "graduated_tier") "plan with flat + graduated tier charges"
  $badPlan = @{ planKey = "ft-bad-$run"; name = "Bad"; currency = "BDT"; flatFee = 0; unitPrice = 0; metric = "x"; freeAllowance = 0; minimumCommitment = 0;
    charges = @(@{ type = "volume_tier"; name = "V"; amount = 0; tiers = @(@{ upTo = 10; unitPrice = 1 }) }) }
  Check ((StatusOf { Api POST "/api/catalogue/plans" $badPlan }) -eq 400) "invalid tiers rejected (400)"
}

Step "Subscriptions (FR4): create, seats, pause/resume, change plan"
Safe "subscriptions" {
  $script:sub = Api POST "/api/subscriptions" @{ customerId = $cust; planId = $a1.id; periodStart = $today.AddDays(-40).ToString("yyyy-MM-dd"); periodEnd = $today.AddDays(330).ToString("yyyy-MM-dd") }
  Check ($sub.status -eq "ACTIVE") "subscription created"
  Check ((StatusOf { Api POST "/api/subscriptions" @{ customerId = $cust; planId = $a1.id; periodStart = $today.ToString("yyyy-MM-dd"); periodEnd = $today.AddDays(30).ToString("yyyy-MM-dd") } }) -eq 409) "second active subscription refused (409)"
  $s = Api POST "/api/subscriptions/$($sub.id)/seats?count=3"
  Check ($s.seatCount -eq 3) "seats -> 3"
  $s = Api POST "/api/subscriptions/$($sub.id)/pause"
  Check ($s.status -eq "PAUSED") "paused"
  $s = Api POST "/api/subscriptions/$($sub.id)/resume"
  Check ($s.status -eq "ACTIVE") "resumed"
  $s = Api POST "/api/subscriptions/$($sub.id)/seats?count=1"
  $ch = Api POST "/api/subscriptions/$($sub.id)/change-plan" @{ planId = $a2.id; timing = "IMMEDIATE" }
  Check ($ch.timing -eq "IMMEDIATE" -and $null -ne $ch.prorationDelta) "change plan now (proration $($ch.prorationDelta))"
  $ch2 = Api POST "/api/subscriptions/$($sub.id)/change-plan" @{ planId = $a1.id; timing = "PERIOD_END" }
  Check ($ch2.timing -eq "PERIOD_END" -and $ch2.effectiveDate) "change plan at period end ($($ch2.effectiveDate))"
  $hist = Api GET "/api/subscriptions/$($sub.id)/plan-changes"
  Check (@($hist).Count -ge 1) "plan-change history"
  $page = Api GET "/api/subscriptions/page?size=5"
  Check ($page.content -ne $null) "paged subscriptions"
}

Step "Metering (FR1, FR2): ingest, duplicates, aggregations, summary"
Safe "metering" {
  $events = 1..40 | ForEach-Object { @{ customerId = $cust; metric = "api_calls"; quantity = 1; timestamp = $today.AddMinutes(-$_).ToString("o");
      idempotencyKey = "ft-$run-$_"; properties = @{ region = $(if ($_ % 2) { "bd" } else { "sg" }); endpoint = "/v1/e$($_ % 4)" } } }
  $r1 = Api POST "/api/usage/events:batch" @{ events = $events }
  $r2 = Api POST "/api/usage/events:batch" @{ events = $events }
  Check ($r1.accepted -eq 40 -and $r2.duplicates -eq 40 -and $r2.accepted -eq 0) "40 accepted, re-send = 40 duplicates"
  $future = @(@{ customerId = $cust; metric = "api_calls"; quantity = 1; timestamp = $today.AddDays(2).ToString("o"); idempotencyKey = "ft-future-$run" })
  $rf = Api POST "/api/usage/events:batch" @{ events = $future }
  Check ($rf.rejected -eq 1) "future event rejected"
  $q = "customerId=$cust&metric=api_calls&from=$monthStart&to=$nowPlus"
  $sum = Api GET "/api/usage/aggregate?$q&operation=sum"
  $cnt = Api GET "/api/usage/aggregate?$q&operation=count"
  $max = Api GET "/api/usage/aggregate?$q&operation=maximum"
  $uniq = Api GET "/api/usage/aggregate?$q&operation=unique_count&distinctProperty=endpoint"
  $last = Api GET "/api/usage/aggregate?$q&operation=latest_value"
  $flt = Api GET ("/api/usage/aggregate?$q&operation=count&filters=" + [uri]::EscapeDataString('{"region":"bd"}'))
  Check ([decimal]$sum.value -eq 40 -and [decimal]$cnt.value -eq 40) "sum = count = 40"
  Check ([decimal]$max.value -eq 1 -and [decimal]$last.value -eq 1) "maximum = latest_value = 1"
  Check ([decimal]$uniq.value -eq 4) "unique_count(endpoint) = 4"
  Check ([decimal]$flt.value -eq 20) "filter region=bd -> 20"
  $summary = Api GET "/api/usage/summary?customerId=$cust"
  Check ($summary.eventCount -eq 40) "month-to-date summary"
  Api GET "/api/late-adjustments?customerId=$cust" | Out-Null
  Ok "late-usage ledger readable"
}

Step "Live spend, simulator, version comparison (FR7, FR10)"
Safe "estimate" {
  $script:est = Api GET "/api/usage/estimate?customerId=$cust&period=$period"
  Check ($est.lines -ne $null -and [decimal]$est.estimatedAmount -gt 0) "live estimate $($est.estimatedAmount) $($est.currency)"
  $sim = Api GET "/api/invoices/simulate?customerId=$cust&period=$period&planId=$($b1.id)"
  Check ($sim.status -eq "SIMULATED" -and $sim.simulationOnly) "simulate under tiered plan: $($sim.total)"
  $cmp = Api GET "/api/invoices/simulate/versions?customerId=$cust&period=$period"
  Check (@($cmp.simulations).Count -eq 2) "compare v1 vs v2"
}

Step "Billing (FR5): invoice stays pending until paid; declined charge -> dunning (FR8)"
Safe "billing" {
  $o = Api POST "/api/payments/mock/customers/$cust/outcome?succeed=false"
  Check ($o.chargesSucceed -eq $false) "mock provider set to decline"
  $b = Api POST "/api/invoices/run/customer?customerId=$cust&period=$period"
  $b2 = Api POST "/api/invoices/run/customer?customerId=$cust&period=$period"
  Check ($b.status -eq "CREATED" -and $b2.status -eq "EXISTING") "billed once (CREATED, then EXISTING)"
  $script:inv = Api GET "/api/invoices/$($b.invoiceId)"
  Start-Sleep -Seconds 4
  $script:inv = Api GET "/api/invoices/$($b.invoiceId)"
  Check ($inv.status -eq "FINALISED") "invoice finalised and pending: nothing charged without the customer"
  Check (@(Api GET "/api/dunning" | Where-Object { $_.invoiceId -eq $inv.id }).Count -eq 0) "no payment attempt yet, no dunning case"
  $all = Api POST "/api/invoices/run?period=$period"
  Check ($all.status -eq "completed") "run all customers: created $($all.created), existing $($all.alreadyExisted)"
  $lines = Api GET "/api/invoices/$($inv.id)/lines"
  Check (@($lines).Count -ge 1) "$(@($lines).Count) invoice lines"
  $usageLine = @($lines | Where-Object { $_.sourceEventCount -gt 0 })[0]
  if ($usageLine) {
    $t = Api GET "/api/invoices/lines/$($usageLine.id)/trace"
    Check ($t.eventCount -eq 40 -and $t.quantityMatches) "line traced to 40 events (QA5)"
  } else { Bad "no usage line to trace" }
  $txt = Invoke-RestMethod -Uri "$BaseUrl/api/invoices/$($inv.id)/render" -Headers $admin -TimeoutSec 60
  $csv = Invoke-RestMethod -Uri "$BaseUrl/api/invoices/$($inv.id)/render?format=csv" -Headers $admin -TimeoutSec 60
  Check ($txt.Length -gt 20 -and $csv.Length -gt 10) "render text and CSV"
  $pg = Api GET "/api/invoices/page?customerId=$cust&size=5"
  Check (@($pg.content).Count -ge 1) "paged invoices"
  $col = Api POST "/api/payments/mock/$($inv.id)/collect"
  Check ($col.paymentStatus -eq "FAILED") "admin charges card on file -> declined"
  Start-Sleep -Seconds 2
  $cases = @(Api GET "/api/dunning" | Where-Object { $_.invoiceId -eq $inv.id })
  Check ($cases.Count -ge 1 -and $cases[0].status -eq "RETRYING") "dunning case RETRYING"
  $asOf = $today.AddDays(2).ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
  $p = Api POST "/api/dunning/process?asOf=$asOf"
  Check ($p.processed -ge 1) "retry processed as of +2 days (processed $($p.processed))"
  Api POST "/api/payments/mock/customers/$cust/outcome?succeed=true" | Out-Null
  $pay = Api POST "/api/payments/mock/$($inv.id)/pay"
  Check ($pay.paymentStatus -eq "SUCCEEDED") "payment succeeded"
  $inv2 = Api GET "/api/invoices/$($inv.id)"
  Check ($inv2.status -eq "PAID") "invoice PAID"
  $case = @(Api GET "/api/dunning" | Where-Object { $_.invoiceId -eq $inv.id })[0]
  Check ($case.status -ne "RETRYING") "dunning case closed ($($case.status))"
}

Step "Batch job (QA3)"
Safe "batch" {
  $job = Api POST "/api/invoices/batch/run?period=$prevPeriod"
  Check ($job.status) "batch job for $prevPeriod -> $($job.status)"
  $rec = Api POST "/api/invoices/batch/recover"
  Ok "stale-run recovery ($($rec.recoveredExecutions) recovered)"
}

Step "Credits (FR6)"
Safe "credits" {
  $acc = Api POST "/api/credits/top-up" @{ customerId = $cust; currency = "BDT"; amount = 50 }
  $note = Api POST "/api/credits/notes" @{ customerId = $cust; currency = "BDT"; amount = 10; reason = "Feature test credit" }
  $bal = Api GET "/api/credits?customerId=$cust"
  Check ([decimal]$bal.balance -eq 60) "balance 60.00 after top-up + credit note"
  $notes = Api GET "/api/credits/notes?customerId=$cust"
  Check (@($notes).Count -eq 1) "credit note listed"
}

Step "Budgets and alerts (FR7)"
Safe "budgets" {
  Api POST "/api/budgets" @{ customerId = $cust; period = $period; amount = 1; currency = "BDT" } | Out-Null
  $ev = Api GET "/api/budgets/evaluate?customerId=$cust&period=$period"
  Check ([int]$ev.percentUsed -ge 100 -and $ev.notifiedThresholds -match "100") "budget evaluated: $($ev.percentUsed)% (alerts $($ev.notifiedThresholds))"
  Start-Sleep -Seconds 3
  $n = Api GET "/api/notifications?customerId=$cust"
  $types = @($n | ForEach-Object { $_.eventType }) | Sort-Object -Unique
  Check ($types -contains "invoice.finalised") "notification invoice.finalised"
  Check ($types -contains "payment.failed") "notification payment.failed"
  Check ($types -contains "payment.succeeded") "notification payment.succeeded"
  Check ($types -contains "usage.threshold_reached") "notification usage.threshold_reached"
}

Step "Webhooks (FR9)"
Safe "webhooks" {
  $wh = Api POST "/api/webhooks" @{ customerId = $cust; endpoint = "https://example.com/meterflow-hook"; secret = "whsec_feature_test_$run" }
  Check ($wh.id) "endpoint registered"
  $list = Api GET "/api/webhooks"
  Check (@($list | Where-Object { $_.id -eq $wh.id }).Count -eq 1) "endpoint listed"
  Api POST "/api/payments/mock/customers/$cust/outcome?succeed=true" | Out-Null
  $d = @(Api GET "/api/webhooks/$($wh.id)/deliveries")
  Ok "delivery log readable ($($d.Count) deliveries)"
}

Step "Daily jobs (what the Cloudflare cron trigger calls)"
Safe "daily-jobs" {
  $lc = Api POST "/api/subscriptions/lifecycle/run"
  Check ($null -ne $lc.trialsEnded -and $null -ne $lc.scheduledPlanChangesApplied) "trial / scheduled-change sweep ran"
  $dp = Api POST "/api/dunning/process"
  Check ($null -ne $dp) "dunning retry sweep ran"
}

Step "Migration (FR10)"
Safe "migration" {
  $dry = Api POST "/api/catalogue/plans/$keyA/migrate-active?fromVersion=2&toVersion=1&dryRun=true"
  Check ($dry.dryRun -eq $true) "dry-run impact: $($dry.affectedSubscriptions) subscription(s)"
}

Step "Customer self-service (accounts)"
$selfUser = "ft-self-$run"
$selfPass = "Self-pass-$run"
$selfHeaders = @{ Authorization = (Basic $selfUser $selfPass); "X-Requested-With" = "XMLHttpRequest" }
Safe "accounts" {
  $su = Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/auth/signup" -ContentType "application/json" -TimeoutSec 300 `
    -Body (@{ username = $selfUser; password = $selfPass; displayName = "Feature Test"; email = "ft@example.com"; company = "FT Ltd" } | ConvertTo-Json)
  Check ($su.customerId -eq $selfUser) "sign-up"
  Check ((StatusOf { Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/auth/signup" -ContentType "application/json" -Body (@{ username = $cust; password = "whatever-123"; displayName = "x"; email = "x@y.zz" } | ConvertTo-Json) }) -eq 400) "cannot sign up as an existing customer ID"
  $me = Api GET "/api/me" $null $selfHeaders
  Check ($me.customerId -eq $selfUser) "customer signs in"
  $prof = Api PUT "/api/account" @{ displayName = "Feature Tester"; email = "ft2@example.com"; company = "FT Ltd" } $selfHeaders
  Check ($prof.displayName -eq "Feature Tester") "profile updated"
  $mine = Api POST "/api/subscriptions/self" @{ planId = $a2.id } $selfHeaders
  Check ($mine.status -eq "ACTIVE" -and $mine.customerId -eq $selfUser) "self-subscribed"
  $chg = Api POST "/api/subscriptions/$($mine.id)/change-plan" @{ planId = $b1.id; timing = "PERIOD_END" } $selfHeaders
  Check ($chg.timing -eq "PERIOD_END") "customer schedules a plan change"
  $own = Api GET "/api/subscriptions" $null $selfHeaders
  Check (@($own).Count -eq 1) "customer sees only its own subscription"
  $myEst = Api GET "/api/usage/estimate?customerId=$selfUser" $null $selfHeaders
  Check ($myEst.lines -ne $null) "customer live estimate"
  $mev = @(@{ customerId = $selfUser; metric = "api_calls"; quantity = 1; timestamp = $today.ToString("o"); idempotencyKey = "ft-self-$run-1" })
  $mr = Api POST "/api/usage/events:batch" @{ events = $mev } $selfHeaders
  Check ($mr.accepted -eq 1) "customer sends its own usage"
  Check ((StatusOf { Api GET "/api/invoices?customerId=$cust" $null $selfHeaders }) -eq 403) "403 on another customer's invoices"
  Check ((StatusOf { Api POST "/api/usage/events:batch" @{ events = @(@{ customerId = $cust; metric = "api_calls"; quantity = 1; timestamp = $today.ToString("o"); idempotencyKey = "x-$run" }) } $selfHeaders }) -eq 403) "403 on usage for another customer"
  Check ((StatusOf { Api POST "/api/invoices/run?period=$period" $null $selfHeaders }) -eq 403) "403 on admin billing run"
  Check ((StatusOf { Api GET "/api/customers" $null $selfHeaders }) -eq 403) "403 on admin customer list"
  $cancel = Api POST "/api/subscriptions/$($mine.id)/cancel" $null $selfHeaders
  Check ($cancel.status -eq "CANCELLED") "customer cancels own subscription"
}

Step "Prepaid plan: pay before use"
$preUser = "ft-pre-$run"
$prePass = "Pre-pass-$run"
$preHeaders = @{ Authorization = (Basic $preUser $prePass); "X-Requested-With" = "XMLHttpRequest" }
Safe "prepaid" {
  $pp = Api POST "/api/catalogue/plans" @{ planKey = "ft-pre-$run"; name = "FT Prepaid"; currency = "BDT"; flatFee = 1000; unitPrice = 0; metric = "api_calls";
    freeAllowance = 50000; minimumCommitment = 0; taxRate = 15; billingMode = "PREPAID" }
  Check ($pp.billingMode -eq "PREPAID") "prepaid plan published"
  Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/auth/signup" -ContentType "application/json" -TimeoutSec 300 `
    -Body (@{ username = $preUser; password = $prePass; displayName = "Prepaid Tester"; email = "pre@example.com" } | ConvertTo-Json) | Out-Null
  $ps = Api POST "/api/subscriptions/self" @{ planId = $pp.id } $preHeaders
  Check ($ps.status -eq "PENDING_PAYMENT") "subscription waits for payment (PENDING_PAYMENT)"
  Start-Sleep -Seconds 4
  $pinv = @(Api GET "/api/invoices?customerId=$preUser" $null $preHeaders | Where-Object { $_.period -eq $period })
  Check ($pinv.Count -eq 1 -and $pinv[0].status -eq "FINALISED" -and [decimal]$pinv[0].total -eq 1150) "package invoice issued up front: 1000 + 15% = 1150.00"
  $ev = @(@{ customerId = $preUser; metric = "api_calls"; quantity = 1; timestamp = $today.ToString("o"); idempotencyKey = "ft-pre-$run-1" })
  $r = Api POST "/api/usage/events:batch" @{ events = $ev } $preHeaders
  Check ($r.accepted -eq 0 -and $r.rejected -eq 1) "usage refused before payment ($($r.rejectionDetails[0].reason))"
  $pay = Api POST "/api/payments/mock/$($pinv[0].id)/pay?method=wallet" $null $preHeaders
  Check ($pay.paymentStatus -eq "SUCCEEDED" -and $pay.method -eq "wallet") "customer pays with mobile wallet"
  $after = @(Api GET "/api/subscriptions" $null $preHeaders)[0]
  Check ($after.status -eq "ACTIVE") "subscription ACTIVE after payment"
  $r2 = Api POST "/api/usage/events:batch" @{ events = @(@{ customerId = $preUser; metric = "api_calls"; quantity = 1; timestamp = $today.ToString("o"); idempotencyKey = "ft-pre-$run-2" }) } $preHeaders
  Check ($r2.accepted -eq 1) "usage accepted after payment"
  $again = Api POST "/api/invoices/run/customer?customerId=$preUser&period=$period"
  Check ($again.status -eq "EXISTING") "month-end run does not bill the prepaid month again"
  $mine = @(Api GET "/api/payments" $null $preHeaders)
  Check ($mine.Count -ge 1 -and $mine[0].method -eq "wallet") "customer sees own payment history"
  $feed = @(Api GET "/api/payments")
  Check (@($feed | Where-Object { $_.customerId -eq $preUser -and $_.status -eq "SUCCEEDED" }).Count -ge 1) "admin payment feed shows the payment"
  $sum = Api GET "/api/payments/summary"
  Check ($sum.today.count -ge 1) "admin summary: $($sum.today.count) payment(s) today, $($sum.today.amount)"
  $top = Api POST "/api/payments/mock/top-up" @{ amount = 300; currency = "BDT"; method = "card" } $preHeaders
  Check ([decimal]$top.balance -ge 300) "customer buys prepaid credit (balance $($top.balance))"

  # Mid-month upgrade of a paid prepaid package: pay only the difference for the remaining days.
  $big = Api POST "/api/catalogue/plans" @{ planKey = "ft-pre-big-$run"; name = "FT Prepaid Big"; currency = "BDT"; flatFee = 2000; unitPrice = 0; metric = "api_calls";
    freeAllowance = 200000; minimumCommitment = 0; taxRate = 15; billingMode = "PREPAID" }
  $pv = Api GET "/api/billing/upgrade-charges/preview?subscriptionId=$($ps.id)&planId=$($big.id)" $null $preHeaders
  $dim = [DateTime]::DaysInMonth($today.Year, $today.Month); $left = $dim - $today.Day + 1
  $expSub = [Math]::Round(1000 * $left / $dim, 2, [MidpointRounding]::AwayFromZero)
  $expTotal = $expSub + [Math]::Round($expSub * 0.15, 2, [MidpointRounding]::AwayFromZero)
  Check ($pv.mode -eq "SETTLE_NOW" -and [Math]::Abs([decimal]$pv.totalDueNow - $expTotal) -le 0.02) "upgrade preview: pay $($pv.totalDueNow) now for $left of $dim days (expected $expTotal)"
  Api POST "/api/subscriptions/$($ps.id)/change-plan" @{ planId = $big.id; timing = "IMMEDIATE" } $preHeaders | Out-Null
  Start-Sleep -Seconds 4
  $uc = @(Api GET "/api/billing/upgrade-charges" $null $preHeaders | Where-Object { $_.status -eq "DUE" })
  Check ($uc.Count -eq 1 -and [decimal]$uc[0].total -eq [decimal]$pv.totalDueNow -and [decimal]$uc[0].alreadyPaid -eq 1150) "balance due after upgrade: already paid 1150.00, rest $($uc[0].total)"
  $bal = Api GET "/api/payments/balance" $null $preHeaders
  Check ([decimal]$bal.totalDueNow -eq [decimal]$uc[0].total) "client balance shows $($bal.totalDueNow) due now"
  $again = Api POST "/api/invoices/run/customer?customerId=$preUser&period=$period"
  Check ($again.status -eq "EXISTING") "the month's invoice is not duplicated by the upgrade"
  $up = Api POST "/api/payments/mock/upgrade-charges/$($uc[0].id)/pay?method=card" $null $preHeaders
  Check ($up.paymentStatus -eq "SUCCEEDED") "customer pays the upgrade balance"
  $bal2 = Api GET "/api/payments/balance" $null $preHeaders
  Check ([decimal]$bal2.totalDueNow -eq 0) "nothing left to pay after the upgrade balance"
  Check (@(Api GET "/api/payments" | Where-Object { $_.customerId -eq $preUser -and $_.kind -eq "UPGRADE" }).Count -eq 1) "admin feed shows the upgrade payment"
  $code = StatusOf { Api POST "/api/subscriptions/$($ps.id)/change-plan" @{ planId = $a1.id; timing = "IMMEDIATE" } $preHeaders }
  Check ($code -eq 400) "prepaid -> postpaid only at period end (immediate refused: $code)"
  $cBefore = [decimal](Api GET "/api/credits?customerId=$preUser" $null $preHeaders).balance
  Api POST "/api/subscriptions/$($ps.id)/change-plan" @{ planId = $pp.id; timing = "IMMEDIATE" } $preHeaders | Out-Null
  Start-Sleep -Seconds 4
  $cAfter = [decimal](Api GET "/api/credits?customerId=$preUser" $null $preHeaders).balance
  Check ([Math]::Abs(($cAfter - $cBefore) - $expSub) -le 0.02) "downgrade back: unused $([Math]::Round($cAfter - $cBefore, 2)) added to credit"
  $ren = Api POST "/api/invoices/prepaid/renew"
  Check ($null -ne $ren.renewed) "prepaid renewal job runs ($($ren.renewed) renewed)"
}

Step "Postpaid: two unpaid invoices block the account"
$late = "ft-late-$run"
Safe "auto-block" {
  $ls = Api POST "/api/subscriptions" @{ customerId = $late; planId = $a1.id; periodStart = $today.AddDays(-45).ToString("yyyy-MM-dd"); periodEnd = $today.AddDays(320).ToString("yyyy-MM-dd") }
  Api POST "/api/usage/events:batch" @{ events = @(@{ customerId = $late; metric = "api_calls"; quantity = 5; timestamp = $today.ToString("o"); idempotencyKey = "ft-late-$run-1" }) } | Out-Null
  $i1 = Api POST "/api/invoices/run/customer?customerId=$late&period=$prevPeriod"
  $i2 = Api POST "/api/invoices/run/customer?customerId=$late&period=$period"
  Check ($i1.status -eq "CREATED" -and $i2.status -eq "CREATED") "two monthly invoices, both unpaid"
  Start-Sleep -Seconds 4
  Api POST "/api/payments/standing/enforce" | Out-Null
  $st = @(Api GET "/api/subscriptions" | Where-Object { $_.customerId -eq $late })[0]
  Check ($st.status -eq "BLOCKED") "account BLOCKED after 2 unpaid invoices"
  $rb = Api POST "/api/usage/events:batch" @{ events = @(@{ customerId = $late; metric = "api_calls"; quantity = 1; timestamp = $today.ToString("o"); idempotencyKey = "ft-late-$run-2" }) }
  Check ($rb.rejected -eq 1) "usage refused while blocked"
  Api POST "/api/payments/mock/$($i1.invoiceId)/pay?method=bank" | Out-Null
  $st2 = @(Api GET "/api/subscriptions" | Where-Object { $_.customerId -eq $late })[0]
  Check ($st2.status -eq "ACTIVE") "paying the oldest invoice unblocks the account"
  $an = @(Api GET "/api/subscriptions/analytics" | Where-Object { $_.customerId -eq $late })
  Check ($an.Count -eq 1 -and $an[0].daysOnPlan -ge 44) "analytics: on plan for $($an[0].daysOnPlan) days"
  $bd = Api GET "/api/usage/breakdown?customerId=$cust"
  Check ($bd.totalEvents -ge 40 -and $bd.properties.endpoint.Count -eq 4) "usage breakdown: $($bd.totalEvents) events, 4 endpoints"
}

Step "Admin: customers and logins"
Safe "admin accounts" {
  $list = Api GET "/api/customers"
  Check (@($list | Where-Object { $_.customerId -eq $selfUser }).Count -eq 1) "admin sees the sign-up"
  $created = Api POST "/api/customers" @{ username = $cust; password = "Temp-pass-$run"; displayName = "FT Billing"; email = "billing@ft.test" }
  Check ($created.customerId -eq $cust) "admin creates a login for existing customer $cust"
  $custHeaders = @{ Authorization = (Basic $cust "Temp-pass-$run"); "X-Requested-With" = "XMLHttpRequest" }
  $invs = Api GET "/api/invoices?customerId=$cust" $null $custHeaders
  Check (@($invs).Count -ge 1) "that customer now sees its invoices"
  Api POST "/api/customers/$cust/enabled?value=false" | Out-Null
  Check ((StatusOf { Api GET "/api/me" $null $custHeaders }) -eq 401) "disabled login is refused"
  Api POST "/api/customers/$cust/enabled?value=true" | Out-Null
  $back = Api GET "/api/me" $null $custHeaders
  Check ($back.customerId -eq $cust) "re-enabled login works"
}

Write-Host ""
Write-Host "$($script:passed) passed, $($script:failed) failed"
if ($script:failed -eq 0) { Write-Host "ALL FEATURES PASSED" -ForegroundColor Green; exit 0 } else { Write-Host "$($script:failed) CHECK(S) FAILED" -ForegroundColor Red; exit 1 }

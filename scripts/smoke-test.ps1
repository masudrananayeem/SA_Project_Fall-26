# MeterFlow end-to-end smoke test (Windows PowerShell 5+ or PowerShell 7).
#   Local:      powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
#   Cloudflare: powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1 -BaseUrl https://meterflow-api.<you>.workers.dev -Password <admin password>
param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$User = "admin",
  [string]$Password = "meterflow-admin"
)
$ErrorActionPreference = "Stop"
$auth = "Basic " + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${User}:${Password}"))
$headers = @{ Authorization = $auth }
$failed = 0
function Step($name) { Write-Host "`n== $name" -ForegroundColor Cyan }
function Ok($msg) { Write-Host "  PASS $msg" -ForegroundColor Green }
function Bad($msg) { Write-Host "  FAIL $msg" -ForegroundColor Red; $script:failed++ }
function Call($method, $path, $body = $null) {
  $p = @{ Method = $method; Uri = "$BaseUrl$path"; Headers = $headers; ContentType = "application/json"; TimeoutSec = 300 }
  if ($body -ne $null) { $p.Body = ($body | ConvertTo-Json -Depth 8) }
  return Invoke-RestMethod @p
}

Step "Health (first call on Cloudflare can take 1-3 minutes while the container starts)"
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 300
if ($health.status -eq "UP") { Ok "backend UP" } else { Bad "health = $($health.status)" }

$run = Get-Random -Maximum 999999
$customer = "smoke-$run"
$period = (Get-Date).ToUniversalTime().ToString("yyyy-MM")

Step "Plan (FR3/FR10): publish a plan, then edit it -> version 2"
$planBody = @{ planKey = "smoke-$run"; name = "Smoke plan"; currency = "BDT"; flatFee = 100; unitPrice = 0.5; metric = "api_calls";
               freeAllowance = 10; minimumCommitment = 0; taxRate = 15 }
$v1 = Call POST "/api/catalogue/plans" $planBody
$planBody.flatFee = 80
$v2 = Call PUT "/api/catalogue/plans/smoke-$run" $planBody
if ($v1.version -eq 1 -and $v2.version -eq 2) { Ok "plan v1 and v2 created" } else { Bad "versions $($v1.version), $($v2.version)" }

Step "Subscription (FR4) on v1"
$today = (Get-Date).ToUniversalTime()
$sub = Call POST "/api/subscriptions" @{ customerId = $customer; planId = $v1.id; periodStart = $today.AddDays(-40).ToString("yyyy-MM-dd"); periodEnd = $today.AddDays(330).ToString("yyyy-MM-dd") }
if ($sub.status -eq "ACTIVE") { Ok "subscription $($sub.id)" } else { Bad "status $($sub.status)" }

Step "Ingestion (FR1): 50 events, then the same 50 again (duplicates)"
$events = 1..50 | ForEach-Object { @{ customerId = $customer; metric = "api_calls"; quantity = 1; timestamp = $today.AddMinutes(-$_).ToString("o");
                                      idempotencyKey = "smoke-$run-$_"; properties = @{ region = "bd" } } }
$r1 = Call POST "/api/usage/events:batch" @{ events = $events }
$r2 = Call POST "/api/usage/events:batch" @{ events = $events }
if ($r1.accepted -eq 50 -and $r2.accepted -eq 0 -and $r2.duplicates -eq 50) { Ok "50 accepted, 50 duplicates ignored" } else { Bad "first=$($r1.accepted) second=$($r2.accepted)/$($r2.duplicates)" }

Step "Live spend (FR7)"
$est = Call GET "/api/usage/estimate?customerId=$customer&period=$period"
# 100 + (50 - 10) x 0.5 = 120.00 ; VAT 15% = 18.00 ; total 138.00
if ([decimal]$est.estimatedAmount -eq 138) { Ok "estimate 138.00 BDT" } else { Bad "estimate $($est.estimatedAmount)" }

Step "Simulator (FR10): same month under v2"
$sim = Call GET "/api/invoices/simulate?customerId=$customer&period=$period&planId=$($v2.id)"
if ([decimal]$sim.subtotal -eq 100) { Ok "v2 would be 100.00 + tax" } else { Bad "simulated subtotal $($sim.subtotal)" }

Step "Billing (FR5): bill the customer twice (must create only one invoice)"
$b1 = Call POST "/api/invoices/run/customer?customerId=$customer&period=$period"
$b2 = Call POST "/api/invoices/run/customer?customerId=$customer&period=$period"
if ($b1.status -eq "CREATED" -and $b2.status -eq "EXISTING") { Ok "idempotent (CREATED then EXISTING)" } else { Bad "$($b1.status) / $($b2.status) $($b1.reason)" }
$invoice = Call GET "/api/invoices/$($b1.invoiceId)"
if ($invoice.status -eq "FINALISED" -or $invoice.status -eq "PAID") { Ok "invoice $($invoice.status), total $($invoice.total)" } else { Bad "invoice status $($invoice.status)" }
if ([decimal]$invoice.total -eq [decimal]$est.estimatedAmount) { Ok "QA5: estimate equals invoice total" } else { Bad "estimate $($est.estimatedAmount) vs invoice $($invoice.total)" }

Step "Trace and render (QA5)"
$lines = Call GET "/api/invoices/$($invoice.id)/lines"
$trace = Call GET "/api/invoices/lines/$($lines[0].id)/trace"
if ($trace.eventCount -eq 50 -and $trace.quantityMatches) { Ok "line traced to 50 events, quantity matches" } else { Bad "trace events=$($trace.eventCount) matches=$($trace.quantityMatches)" }
$text = Invoke-RestMethod -Uri "$BaseUrl/api/invoices/$($invoice.id)/render" -Headers $headers
if ($text -match "Total due") { Ok "rendered invoice" } else { Bad "render" }

Step "Notifications (Observer, async)"
Start-Sleep -Seconds 3
$notes = Call GET "/api/notifications?customerId=$customer"
if (@($notes | Where-Object { $_.eventType -eq "invoice.finalised" }).Count -ge 1) { Ok "invoice.finalised notification" } else { Bad "no invoice.finalised notification yet" }

Write-Host ""
if ($failed -eq 0) { Write-Host "ALL CHECKS PASSED" -ForegroundColor Green; exit 0 } else { Write-Host "$failed CHECK(S) FAILED" -ForegroundColor Red; exit 1 }

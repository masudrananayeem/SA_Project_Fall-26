# Runs the backend unit + Spring Modulith architecture tests inside Docker (no local Java/Maven needed).
# Usage (repository root):   .\scripts\backend-tests.ps1
#   -Qa   also runs the QA1 accuracy test (1,000,000 events; takes a few minutes)
# The raw output is saved to docs\quality-evidence\backend-tests-<date>.txt as test evidence.
param([switch]$Qa)
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "backend"
$evidence = Join-Path $root "docs\quality-evidence"
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$out = Join-Path $evidence ("backend-tests-" + (Get-Date -Format "yyyy-MM-dd_HHmm") + ".txt")
$mvnArgs = @("-B")
if ($Qa) { $mvnArgs += "-Pqa" }
$mvnArgs += "test"
Write-Host "Running mvn test in Docker (first run downloads dependencies, 3-6 minutes)..." -ForegroundColor Cyan
docker run --rm -v "${backend}:/app" -v meterflow-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 `
  mvn @mvnArgs 2>&1 | Tee-Object -FilePath $out
if ($LASTEXITCODE -eq 0) {
  Write-Host "`nALL BACKEND TESTS PASSED  (log: $out)" -ForegroundColor Green
} else {
  Write-Host "`nBACKEND TESTS FAILED  (log: $out) - send this file for a fix" -ForegroundColor Red
  exit 1
}

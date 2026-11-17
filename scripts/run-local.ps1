$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not (Test-Path '.env')) { Copy-Item '.env.example' '.env'; Write-Host 'Created .env from .env.example. Review local passwords before continuing.' -ForegroundColor Yellow }
docker compose up --build -d --remove-orphans
docker compose ps
Write-Host "Admin console:   http://localhost:5173  (admin / meterflow-admin)" -ForegroundColor Green
Write-Host "Customer portal: http://localhost:5174  (customer / meterflow-customer)" -ForegroundColor Green
Write-Host "Backend health: http://localhost:8080/actuator/health" -ForegroundColor Green
Write-Host "Swagger: http://localhost:8080/swagger-ui.html" -ForegroundColor Green

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
docker compose down
Write-Host 'Services stopped. Database volume was preserved.' -ForegroundColor Green

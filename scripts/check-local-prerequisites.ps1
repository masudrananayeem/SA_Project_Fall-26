$ErrorActionPreference = 'Continue'
$checks = @(
  @{ Name = 'Java 21'; Command = 'java'; Args = @('-version') },
  @{ Name = 'Maven'; Command = 'mvn'; Args = @('-version') },
  @{ Name = 'Node.js'; Command = 'node'; Args = @('--version') },
  @{ Name = 'npm'; Command = 'npm'; Args = @('--version') },
  @{ Name = 'Docker'; Command = 'docker'; Args = @('--version') },
  @{ Name = 'Docker Compose'; Command = 'docker'; Args = @('compose','version') }
)
$failed = @()
foreach ($check in $checks) {
  Write-Host "`n--- $($check.Name) ---" -ForegroundColor Cyan
  $cmd = Get-Command $check.Command -ErrorAction SilentlyContinue
  if (-not $cmd) { Write-Host 'NOT FOUND - install it and reopen PowerShell.' -ForegroundColor Red; $failed += $check.Name; continue }
  & $check.Command @($check.Args)
  if ($LASTEXITCODE -ne 0) { $failed += $check.Name }
}
Write-Host "`n--- Summary ---" -ForegroundColor Cyan
if ($failed.Count -eq 0) { Write-Host 'All prerequisite commands are available. Confirm Java reports version 21.' -ForegroundColor Green }
else { Write-Host ('Missing or failed: ' + ($failed -join ', ')) -ForegroundColor Yellow }

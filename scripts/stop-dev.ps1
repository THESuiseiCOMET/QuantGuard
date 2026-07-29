[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeDir = Join-Path $repoRoot '.runtime'
$statePath = Join-Path $runtimeDir 'dev-processes.json'

if (-not (Test-Path -LiteralPath $statePath)) {
    Write-Host 'No QuantGuard development processes are recorded.'
    exit 0
}

try {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
} catch {
    throw "Cannot read process state: $statePath"
}

$remaining = @()
foreach ($service in @($state.services)) {
    $process = Get-Process -Id $service.pid -ErrorAction SilentlyContinue
    if (-not $process) {
        Write-Host "$($service.name) already stopped."
        continue
    }

    $expectedStartTime = [long]$service.startTimeFileUtc
    if ($process.StartTime.ToFileTimeUtc() -ne $expectedStartTime) {
        Write-Warning "Skipped PID $($service.pid) for $($service.name) because it was reused by another process."
        continue
    }

    & taskkill.exe /PID $service.pid /T /F | Out-Null
    $process = Get-Process -Id $service.pid -ErrorAction SilentlyContinue
    if ($LASTEXITCODE -ne 0 -and $process) {
        Write-Warning "Could not stop $($service.name) (PID $($service.pid))."
        $remaining += $service
        continue
    }
    Write-Host "Stopped $($service.name)."
}

if ($remaining.Count -gt 0) {
    $state.services = $remaining
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding UTF8
    throw 'Some managed processes could not be stopped. Their PID records were preserved.'
}

Remove-Item -LiteralPath $statePath -Force
Write-Host 'QuantGuard development services are stopped.'

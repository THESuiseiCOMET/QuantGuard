[CmdletBinding()]
param(
    [switch]$RunLiveMarketData
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'runtime-build-common.ps1')

$pythonProject = Join-Path $repoRoot 'quant-guard-market-data-python'
$pythonExe = Join-Path $pythonProject '.venv\Scripts\python.exe'
$backendProject = Join-Path $repoRoot 'quant-guard-backend'
$mavenWrapper = Join-Path $backendProject 'mvnw.cmd'
$runtimeRoot = Join-Path $repoRoot 'dist\desktop-runtime'

if (-not (Test-Path -LiteralPath $pythonExe)) {
    throw "Python virtual environment is missing: $pythonExe"
}

Push-Location $pythonProject
try {
    & $pythonExe -m pytest
    Assert-LastExitCode 'Python tests'
    & $pythonExe -m compileall app
    Assert-LastExitCode 'Python module compilation'
} finally {
    Pop-Location
}

& (Join-Path $PSScriptRoot 'build-python-runtime.ps1') -OutputRoot $runtimeRoot

Push-Location $backendProject
try {
    & $mavenWrapper clean test
    Assert-LastExitCode 'Java tests'
} finally {
    Pop-Location
}

& (Join-Path $PSScriptRoot 'build-backend-runtime.ps1') -OutputRoot $runtimeRoot
& (Join-Path $PSScriptRoot 'test-desktop-runtimes.ps1') `
    -OutputRoot $runtimeRoot `
    -RunLiveMarketData:$RunLiveMarketData

$pythonRuntime = Join-Path $runtimeRoot 'market-data'
$backendRuntime = Join-Path $runtimeRoot 'backend'
Write-Host 'QuantGuard desktop runtime build complete:'
Write-Host "  Python: $pythonRuntime"
Write-Host ('  Python size: {0:N2} MB' -f ((Get-DirectorySizeBytes $pythonRuntime) / 1MB))
Write-Host "  Backend: $backendRuntime"
Write-Host ('  Backend size: {0:N2} MB' -f ((Get-DirectorySizeBytes $backendRuntime) / 1MB))

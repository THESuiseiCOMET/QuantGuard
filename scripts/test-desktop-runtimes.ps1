[CmdletBinding()]
param(
    [string]$OutputRoot,
    [switch]$RunLiveMarketData
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$runtimeRoot = if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    Join-Path $repoRoot 'dist\desktop-runtime'
} else {
    [IO.Path]::GetFullPath($OutputRoot)
}
$pythonExe = Join-Path $runtimeRoot 'market-data\quantguard-market-data.exe'
$backendExe = Join-Path $runtimeRoot 'backend\QuantGuardBackend.exe'
$pythonPort = 18001
$backendPort = 18080
$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("QuantGuardRuntimeSmoke-" + [guid]::NewGuid())
$dataRoot = Join-Path $temporaryRoot 'data-root'
$pythonLogs = Join-Path $temporaryRoot 'market-data-logs'
$capturedLogs = Join-Path $temporaryRoot 'captured'
$pythonProcess = $null
$backendProcess = $null
$smokeSucceeded = $false

function Quote-ProcessArgument {
    param([string]$Value)
    return '"' + $Value.Replace('"', '\"') + '"'
}

function Start-IsolatedRuntime {
    param(
        [string]$Executable,
        [string[]]$Arguments,
        [hashtable]$Environment
    )

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Executable
    $startInfo.Arguments = ($Arguments | ForEach-Object { Quote-ProcessArgument $_ }) -join ' '
    $startInfo.WorkingDirectory = Split-Path -Parent $Executable
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.EnvironmentVariables['PATH'] = "$env:SystemRoot\System32"
    $startInfo.EnvironmentVariables.Remove('JAVA_HOME')
    $startInfo.EnvironmentVariables.Remove('PYTHONHOME')
    $startInfo.EnvironmentVariables.Remove('PYTHONPATH')
    foreach ($entry in $Environment.GetEnumerator()) {
        $startInfo.EnvironmentVariables[$entry.Key] = [string]$entry.Value
    }

    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Failed to start runtime: $Executable"
    }
    return @{
        Process = $process
        StandardOutput = $process.StandardOutput.ReadToEndAsync()
        StandardError = $process.StandardError.ReadToEndAsync()
    }
}

function Wait-HttpReady {
    param(
        [string]$Name,
        [string]$Uri,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "$Name did not become healthy at $Uri."
}

function Stop-IsolatedRuntime {
    param(
        [hashtable]$Runtime,
        [string]$Name
    )

    if ($null -eq $Runtime) {
        return
    }
    $process = $Runtime.Process
    if (-not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F | Out-Null
        $process.WaitForExit(10000) | Out-Null
    }
    New-Item -ItemType Directory -Path $capturedLogs -Force | Out-Null
    [IO.File]::WriteAllText(
        (Join-Path $capturedLogs "$Name.out.log"),
        $Runtime.StandardOutput.GetAwaiter().GetResult()
    )
    [IO.File]::WriteAllText(
        (Join-Path $capturedLogs "$Name.err.log"),
        $Runtime.StandardError.GetAwaiter().GetResult()
    )
}

if (-not (Test-Path -LiteralPath $pythonExe)) {
    throw "Packaged Python runtime is missing: $pythonExe"
}
if (-not (Test-Path -LiteralPath $backendExe)) {
    throw "Packaged backend runtime is missing: $backendExe"
}
foreach ($port in $pythonPort, $backendPort) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "Smoke-test port $port is already in use."
    }
}

New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
try {
    $pythonProcess = Start-IsolatedRuntime `
        -Executable $pythonExe `
        -Arguments @(
            '--host', '127.0.0.1',
            '--port', "$pythonPort",
            '--log-dir', $pythonLogs
        ) `
        -Environment @{}
    Wait-HttpReady 'Packaged market-data service' "http://127.0.0.1:$pythonPort/health" 90

    $invalidLookup = @{
        symbols = @('INVALID')
    } | ConvertTo-Json
    $lookupResponse = Invoke-RestMethod `
        -Method Post `
        -Uri "http://127.0.0.1:$pythonPort/v1/instruments/lookup" `
        -ContentType 'application/json' `
        -Body $invalidLookup
    if ($lookupResponse.results[0].error.code -ne 'INVALID_SYMBOL') {
        throw 'Packaged instrument lookup did not return its structured validation error.'
    }

    $invalidDailyBars = @{
        symbols = @('INVALID')
        start_date = '2026-01-01'
        end_date = '2026-01-02'
    } | ConvertTo-Json
    $barsResponse = Invoke-RestMethod `
        -Method Post `
        -Uri "http://127.0.0.1:$pythonPort/v1/daily-bars" `
        -ContentType 'application/json' `
        -Body $invalidDailyBars
    if ($barsResponse.results[0].error.code -ne 'INVALID_SYMBOL') {
        throw 'Packaged daily-bars API did not return its structured validation error.'
    }

    $backendEnvironment = @{
        QUANTGUARD_DATA_DIR = $dataRoot
        MARKET_DATA_PROVIDER = 'python'
        PYTHON_MARKET_DATA_BASE_URL = "http://127.0.0.1:$pythonPort"
    }
    $backendProcess = Start-IsolatedRuntime `
        -Executable $backendExe `
        -Arguments @("--server.port=$backendPort") `
        -Environment $backendEnvironment
    Wait-HttpReady 'Packaged backend' "http://127.0.0.1:$backendPort/api/health" 120

    $accountBody = @{
        accountName = 'runtime-smoke-account'
        accountType = 'LONG_TERM'
        initialCash = 100000
    } | ConvertTo-Json
    $account = (Invoke-RestMethod `
        -Method Post `
        -Uri "http://127.0.0.1:$backendPort/api/accounts" `
        -ContentType 'application/json' `
        -Body $accountBody).data
    $portfolioBody = @{
        accountId = $account.id
        portfolioName = 'runtime-smoke-portfolio'
        portfolioType = 'LONG_TERM'
        description = 'Packaged runtime persistence smoke'
    } | ConvertTo-Json
    $portfolio = (Invoke-RestMethod `
        -Method Post `
        -Uri "http://127.0.0.1:$backendPort/api/portfolios" `
        -ContentType 'application/json' `
        -Body $portfolioBody).data
    $createdPortfolios = (Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/api/portfolios").data
    if (-not (@($createdPortfolios).id -contains $portfolio.id)) {
        throw 'Packaged backend did not make the smoke portfolio readable before restart.'
    }

    if ($RunLiveMarketData) {
        $importBody = @{
            codes = @('600519.SH')
            syncDailyPrices = $false
            startDate = '2026-01-01'
        } | ConvertTo-Json
        $import = (Invoke-RestMethod `
            -Method Post `
            -Uri "http://127.0.0.1:$backendPort/api/instruments/import" `
            -ContentType 'application/json' `
            -Body $importBody).data[0]
        if ($import.metadataStatus -ne 'SUCCESS') {
            throw "Live packaged Java-to-Python market-data call failed: $($import.message)"
        }
    }

    $databasePath = Join-Path $dataRoot 'data\quantguard.mv.db'
    if (-not (Test-Path -LiteralPath $databasePath)) {
        throw "Packaged backend did not create its H2 database: $databasePath"
    }

    # The smoke harness force-stops the child process, so allow H2's write delay to flush first.
    Start-Sleep -Seconds 2
    Stop-IsolatedRuntime -Runtime $backendProcess -Name 'backend-first-run'
    $backendProcess = $null
    $backendProcess = Start-IsolatedRuntime `
        -Executable $backendExe `
        -Arguments @("--server.port=$backendPort") `
        -Environment $backendEnvironment
    Wait-HttpReady 'Restarted packaged backend' "http://127.0.0.1:$backendPort/api/health" 120

    $accounts = (Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/api/accounts").data
    $portfolios = (Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/api/portfolios").data
    if (@($accounts).Count -ne 1 -or $accounts[0].accountName -ne 'runtime-smoke-account') {
        throw 'Packaged backend did not preserve the smoke account across restart.'
    }
    $persistedPortfolio = @($portfolios) | Where-Object {
        $_.accountId -eq $account.id -and $_.portfolioName -eq $portfolio.portfolioName
    }
    if (@($persistedPortfolio).Count -ne 1) {
        throw 'Packaged backend did not preserve the smoke portfolio across restart.'
    }

    Write-Host 'Desktop runtime smoke tests passed:'
    Write-Host "  Python executable: $pythonExe"
    Write-Host "  Backend executable: $backendExe"
    Write-Host '  PATH isolation: system directory only; JAVA_HOME/PYTHONHOME/PYTHONPATH removed'
    Write-Host '  Packaged APIs: health, instrument lookup, and daily bars verified'
    Write-Host '  H2 restart persistence: account and portfolio preserved'
    Write-Host "  Live Java-to-Python market data: $($RunLiveMarketData.IsPresent)"
    $smokeSucceeded = $true
} finally {
    Stop-IsolatedRuntime -Runtime $backendProcess -Name 'backend-final'
    Stop-IsolatedRuntime -Runtime $pythonProcess -Name 'market-data'
    foreach ($port in $pythonPort, $backendPort) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            throw "Runtime smoke test left port $port in use. Logs: $capturedLogs"
        }
    }
    if ($smokeSucceeded -and (Test-Path -LiteralPath $temporaryRoot)) {
        [IO.Directory]::Delete($temporaryRoot, $true)
    } elseif (Test-Path -LiteralPath $temporaryRoot) {
        Write-Warning "Runtime smoke data and logs were preserved for diagnosis: $temporaryRoot"
    }
}

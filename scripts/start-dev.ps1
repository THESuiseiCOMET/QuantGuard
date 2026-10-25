[CmdletBinding()]
param(
    [switch]$Desktop,
    [string]$DataDirectory
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$pathHelpers = Join-Path $PSScriptRoot 'dev-paths.ps1'
. $pathHelpers
$layout = Get-QuantGuardRuntimeLayout -RepoRoot $repoRoot -Desktop:$Desktop -DataDirectory $DataDirectory
$runtimeDir = $layout.runtimeDir
$logDir = $layout.logDir
$statePath = Join-Path $runtimeDir 'dev-processes.json'
$stopScript = Join-Path $PSScriptRoot 'stop-dev.ps1'
$services = @()

function Test-PortAvailable {
    param([int]$Port)

    return -not [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Assert-CommandAvailable {
    param([string]$Command)

    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "Required command is unavailable: $Command"
    }
}

function Save-ProcessState {
    $state = @{
        repoRoot = $repoRoot
        mode = $layout.mode
        dataRoot = $layout.dataRoot
        createdAt = (Get-Date).ToUniversalTime().ToString('o')
        services = $services
    }
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding UTF8
}

function Start-ManagedCommand {
    param(
        [string]$Name,
        [int]$Port,
        [string]$Command,
        [string]$WorkingDirectory
    )

    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Command))
    $stdoutPath = Join-Path $logDir "$Name.out.log"
    $stderrPath = Join-Path $logDir "$Name.err.log"
    $process = Start-Process `
        -FilePath 'powershell.exe' `
        -ArgumentList @('-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $encodedCommand) `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath `
        -WindowStyle Hidden `
        -PassThru
    $startedProcess = Get-Process -Id $process.Id
    $script:services += @{
        name = $Name
        port = $Port
        pid = $process.Id
        startTimeFileUtc = $startedProcess.StartTime.ToFileTimeUtc()
        stdout = $stdoutPath
        stderr = $stderrPath
    }
    Save-ProcessState
}

function Wait-HttpEndpoint {
    param(
        [string]$Name,
        [string]$Uri,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                return
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "$Name did not become healthy within $TimeoutSeconds seconds. Check $logDir."
}

try {
    if (Test-Path -LiteralPath $statePath) {
        & $stopScript -Desktop:$Desktop -DataDirectory $DataDirectory
    }

    $pythonDir = Join-Path $repoRoot 'quant-guard-market-data-python'
    $pythonExe = Join-Path $pythonDir '.venv\Scripts\python.exe'
    $backendDir = Join-Path $repoRoot 'quant-guard-backend'
    $mavenWrapper = Join-Path $backendDir 'mvnw.cmd'
    $frontendDir = Join-Path $repoRoot 'quant-guard-frontend'

    if (-not (Test-Path -LiteralPath $pythonExe)) {
        throw "Python virtual environment is missing: quant-guard-market-data-python\.venv"
    }
    if (-not (Test-Path -LiteralPath $mavenWrapper)) {
        throw 'Maven Wrapper is missing: quant-guard-backend\mvnw.cmd'
    }
    Assert-CommandAvailable 'java.exe'
    Assert-CommandAvailable 'npm.cmd'

    & $pythonExe -c 'import akshare, baostock, fastapi, pandas, uvicorn'
    if ($LASTEXITCODE -ne 0) {
        throw 'Python market-data dependencies are incomplete. Install requirements.txt in the project virtual environment.'
    }

    foreach ($port in 8001, 8080, 5173) {
        if (-not (Test-PortAvailable $port)) {
            throw "Port $port is already in use. Stop the existing service before running this script."
        }
    }

    New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null

    $pythonCommand = "& '$pythonExe' -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --workers 1"
    Start-ManagedCommand 'market-data' 8001 $pythonCommand $pythonDir
    Wait-HttpEndpoint 'Python market-data service' 'http://127.0.0.1:8001/health' 45

    if ($Desktop) {
        $escapedDataRoot = $layout.dataRoot.Replace("'", "''")
        $backendCommand =
            "`$env:SPRING_PROFILES_ACTIVE='desktop'; " +
            "`$env:QUANTGUARD_DATA_DIR='$escapedDataRoot'; " +
            "& '$mavenWrapper' spring-boot:run"
    } else {
        $backendCommand = "& '$mavenWrapper' spring-boot:run"
    }
    Start-ManagedCommand 'backend' 8080 $backendCommand $backendDir
    Wait-HttpEndpoint 'Spring Boot backend' 'http://127.0.0.1:8080/api/health' 90

    $frontendCommand = "& npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort"
    Start-ManagedCommand 'frontend' 5173 $frontendCommand $frontendDir
    Wait-HttpEndpoint 'Vue frontend' 'http://127.0.0.1:5173/' 60

    Write-Host 'QuantGuard development services are ready:'
    Write-Host "  运行模式：$($layout.mode)"
    if ($Desktop) {
        Write-Host '  数据库：H2 File'
        Write-Host "  数据目录：$($layout.dataRoot)"
    } else {
        Write-Host '  数据库：MySQL'
    }
    Write-Host '  Python：http://127.0.0.1:8001'
    Write-Host '  后端：http://127.0.0.1:8080'
    Write-Host '  前端：http://127.0.0.1:5173'
    Write-Host "  日志目录：$logDir"
} catch {
    Write-Error $_ -ErrorAction Continue
    if ($services.Count -gt 0 -and (Test-Path -LiteralPath $stopScript)) {
        & $stopScript -Desktop:$Desktop -DataDirectory $DataDirectory
    }
    exit 1
}

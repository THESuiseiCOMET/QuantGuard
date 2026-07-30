[CmdletBinding()]
param(
    [string]$DesktopExecutable,
    [string]$ResourceRoot,
    [string]$WorkingRoot
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$tauriRoot = Join-Path $repoRoot 'quant-guard-frontend\src-tauri'
$executable = if ([string]::IsNullOrWhiteSpace($DesktopExecutable)) {
    Join-Path $tauriRoot 'target\release\quantguard-desktop.exe'
} else {
    [IO.Path]::GetFullPath($DesktopExecutable)
}
$resources = if ([string]::IsNullOrWhiteSpace($ResourceRoot)) {
    Join-Path $tauriRoot 'runtime-staging'
} else {
    [IO.Path]::GetFullPath($ResourceRoot)
}
$workRoot = if ([string]::IsNullOrWhiteSpace($WorkingRoot)) {
    Join-Path $tauriRoot 'target\desktop-lifecycle-smoke'
} else {
    [IO.Path]::GetFullPath($WorkingRoot)
}

foreach ($requiredPath in @(
    $executable,
    (Join-Path $resources 'market-data\quantguard-market-data.exe'),
    (Join-Path $resources 'backend\QuantGuardBackend.exe')
)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Tauri lifecycle prerequisite is missing: $requiredPath"
    }
}

$resolvedTauriRoot = [IO.Path]::GetFullPath($tauriRoot).TrimEnd('\') + '\'
$resolvedWorkRoot = [IO.Path]::GetFullPath($workRoot)
if (-not $resolvedWorkRoot.StartsWith($resolvedTauriRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Lifecycle smoke working directory must stay inside $tauriRoot"
}
if (Test-Path -LiteralPath $resolvedWorkRoot) {
    Remove-Item -LiteralPath $resolvedWorkRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $resolvedWorkRoot -Force | Out-Null

function Invoke-LifecycleScenario {
    param(
        [string]$Name,
        [string[]]$AdditionalArguments = @(),
        [int]$TimeoutSeconds = 240
    )

    $scenarioRoot = Join-Path $resolvedWorkRoot $Name
    $dataRoot = Join-Path $scenarioRoot '用户 数据'
    $stdoutPath = Join-Path $scenarioRoot 'stdout.log'
    $stderrPath = Join-Path $scenarioRoot 'stderr.log'
    New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null

    $arguments = @(
        '--runtime-smoke',
        '--resource-root', $resources,
        '--data-root', $dataRoot,
        '--occupy-default-ports'
    ) + $AdditionalArguments
    $quotedArguments = $arguments | ForEach-Object {
        '"{0}"' -f $_.Replace('"', '\"')
    }
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $executable
    $startInfo.Arguments = $quotedArguments -join ' '
    $startInfo.WorkingDirectory = $scenarioRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Tauri lifecycle scenario '$Name' could not start."
    }
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        $process.Kill()
        $process.WaitForExit()
        throw "Tauri lifecycle scenario '$Name' timed out after $TimeoutSeconds seconds."
    }
    $process.StandardOutput.ReadToEnd() | Set-Content -LiteralPath $stdoutPath -Encoding UTF8
    $process.StandardError.ReadToEnd() | Set-Content -LiteralPath $stderrPath -Encoding UTF8
    $exitCode = $process.ExitCode
    if ($exitCode -ne 0) {
        $stderr = if (Test-Path -LiteralPath $stderrPath) {
            Get-Content -LiteralPath $stderrPath -Raw
        } else {
            ''
        }
        throw "Tauri lifecycle scenario '$Name' failed with exit code ${exitCode}: $stderr"
    }

    Write-Host "  Passed: $Name"
}

Write-Host 'Running Tauri desktop lifecycle smoke tests...'
Invoke-LifecycleScenario -Name 'ready-restart-persistence'
Invoke-LifecycleScenario -Name 'python-startup-failure' -AdditionalArguments @(
    '--simulate-failure', 'python'
)
Invoke-LifecycleScenario -Name 'backend-startup-failure' -AdditionalArguments @(
    '--simulate-failure', 'backend'
)
Write-Host "Tauri lifecycle smoke tests passed. Logs: $resolvedWorkRoot"

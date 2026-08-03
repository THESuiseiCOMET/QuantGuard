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

function Get-DescendantProcessIds {
    param([int]$RootProcessId)

    $childrenByParent = @{}
    foreach ($candidate in @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue)) {
        $parentId = [int]$candidate.ParentProcessId
        if (-not $childrenByParent.ContainsKey($parentId)) {
            $childrenByParent[$parentId] = [Collections.Generic.List[int]]::new()
        }
        $childrenByParent[$parentId].Add([int]$candidate.ProcessId)
    }

    $result = [Collections.Generic.List[int]]::new()
    $pending = [Collections.Generic.Queue[int]]::new()
    $pending.Enqueue($RootProcessId)
    while ($pending.Count -gt 0) {
        $parentId = $pending.Dequeue()
        if (-not $childrenByParent.ContainsKey($parentId)) {
            continue
        }
        foreach ($childId in $childrenByParent[$parentId]) {
            $result.Add($childId)
            $pending.Enqueue($childId)
        }
    }
    return $result.ToArray()
}

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
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $visibleChildWindows = [Collections.Generic.HashSet[string]]::new()
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while (-not $process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        foreach ($childId in @(Get-DescendantProcessIds -RootProcessId $process.Id)) {
            $child = Get-Process -Id $childId -ErrorAction SilentlyContinue
            if ($null -ne $child -and $child.MainWindowHandle -ne [IntPtr]::Zero) {
                $visibleChildWindows.Add("$($child.ProcessName) ($childId)") | Out-Null
            }
        }
        if (-not $process.HasExited) {
            Start-Sleep -Milliseconds 200
        }
    }
    $timedOut = -not $process.HasExited
    if ($timedOut) {
        $process.Kill($true)
        $process.WaitForExit()
    }
    $stdoutTask.GetAwaiter().GetResult() |
        Set-Content -LiteralPath $stdoutPath -Encoding UTF8
    $stderrTask.GetAwaiter().GetResult() |
        Set-Content -LiteralPath $stderrPath -Encoding UTF8
    if ($timedOut) {
        throw "Tauri lifecycle scenario '$Name' timed out after $TimeoutSeconds seconds."
    }
    if ($visibleChildWindows.Count -gt 0) {
        throw "Tauri lifecycle scenario '$Name' opened child windows: $($visibleChildWindows -join ', ')"
    }
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

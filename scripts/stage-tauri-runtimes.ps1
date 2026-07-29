[CmdletBinding()]
param(
    [string]$RuntimeRoot,
    [string]$StagingRoot
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'runtime-build-common.ps1')

$sourceRoot = if ([string]::IsNullOrWhiteSpace($RuntimeRoot)) {
    Join-Path $repoRoot 'dist\desktop-runtime'
} else {
    [IO.Path]::GetFullPath($RuntimeRoot)
}
$targetRoot = if ([string]::IsNullOrWhiteSpace($StagingRoot)) {
    Join-Path $repoRoot 'quant-guard-frontend\src-tauri\runtime-staging'
} else {
    [IO.Path]::GetFullPath($StagingRoot)
}
$tauriRoot = Join-Path $repoRoot 'quant-guard-frontend\src-tauri'
$pythonSource = Join-Path $sourceRoot 'market-data'
$backendSource = Join-Path $sourceRoot 'backend'

$requiredFiles = @(
    (Join-Path $pythonSource 'quantguard-market-data.exe'),
    (Join-Path $backendSource 'QuantGuardBackend.exe'),
    (Join-Path $backendSource 'runtime\bin\server\jvm.dll'),
    (Join-Path $backendSource 'runtime\release')
)
foreach ($requiredFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Desktop runtime staging prerequisite is missing: $requiredFile"
    }
}
if (-not (Get-ChildItem -LiteralPath $pythonSource -Recurse -File -Filter '*.dll')) {
    throw "PyInstaller onedir dependencies are missing from: $pythonSource"
}
if (-not (Get-ChildItem -LiteralPath (Join-Path $backendSource 'app') -File -Filter '*.jar')) {
    throw "Spring Boot JAR is missing from: $backendSource"
}

New-Item -ItemType Directory -Path $tauriRoot -Force | Out-Null
Remove-SafeBuildDirectory -Parent $tauriRoot -Candidate $targetRoot
New-Item -ItemType Directory -Path $targetRoot -Force | Out-Null

Copy-Item -LiteralPath $pythonSource -Destination $targetRoot -Recurse
Copy-Item -LiteralPath $backendSource -Destination $targetRoot -Recurse
Get-ChildItem -LiteralPath $targetRoot -Recurse -File | ForEach-Object {
    if ($_.IsReadOnly) {
        $_.IsReadOnly = $false
    }
}

$stagedPython = Join-Path $targetRoot 'market-data\quantguard-market-data.exe'
$stagedBackend = Join-Path $targetRoot 'backend\QuantGuardBackend.exe'
if (
    -not (Test-Path -LiteralPath $stagedPython -PathType Leaf) -or
    -not (Test-Path -LiteralPath $stagedBackend -PathType Leaf)
) {
    throw 'Tauri runtime staging did not produce both launchers.'
}

Write-Host 'Tauri runtime resources staged:'
Write-Host "  Source: $sourceRoot"
Write-Host "  Target: $targetRoot"
Write-Host ('  Size: {0:N2} MB' -f ((Get-DirectorySizeBytes $targetRoot) / 1MB))

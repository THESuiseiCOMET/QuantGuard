[CmdletBinding()]
param(
    [string]$OutputRoot
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'runtime-build-common.ps1')

$pythonProject = Join-Path $repoRoot 'quant-guard-market-data-python'
$pythonExe = Join-Path $pythonProject '.venv\Scripts\python.exe'
$specPath = Join-Path $pythonProject 'quantguard-market-data.spec'
$runtimeRoot = if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    Join-Path $repoRoot 'dist\desktop-runtime'
} else {
    [IO.Path]::GetFullPath($OutputRoot)
}
$targetPath = Join-Path $runtimeRoot 'market-data'
$workRoot = Join-Path $repoRoot '.runtime\build'
$workPath = Join-Path $workRoot 'pyinstaller'

if (-not (Test-Path -LiteralPath $pythonExe)) {
    throw "Python virtual environment is missing: $pythonExe"
}
if (-not (Test-Path -LiteralPath $specPath)) {
    throw "PyInstaller spec is missing: $specPath"
}

& $pythonExe -c 'import PyInstaller, akshare, baostock, fastapi, pandas, uvicorn'
Assert-LastExitCode 'Python packaging dependency check'

New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
New-Item -ItemType Directory -Path $workRoot -Force | Out-Null
Remove-SafeBuildDirectory -Parent $runtimeRoot -Candidate $targetPath
Remove-SafeBuildDirectory -Parent $workRoot -Candidate $workPath

Push-Location $pythonProject
try {
    & $pythonExe -m PyInstaller `
        --noconfirm `
        --clean `
        --distpath $runtimeRoot `
        --workpath $workPath `
        $specPath
    Assert-LastExitCode 'PyInstaller onedir build'
} finally {
    Pop-Location
}

$executable = Join-Path $targetPath 'quantguard-market-data.exe'
if (-not (Test-Path -LiteralPath $executable)) {
    throw "Python runtime executable was not created: $executable"
}

$version = (& $executable --version | Select-Object -Last 1).Trim()
Assert-LastExitCode 'Packaged Python version check'
$sizeBytes = Get-DirectorySizeBytes -Path $targetPath

Write-Host 'Python desktop runtime built:'
Write-Host "  Path: $targetPath"
Write-Host "  Executable: $executable"
Write-Host "  Version: $version"
Write-Host ('  Size: {0:N2} MB' -f ($sizeBytes / 1MB))

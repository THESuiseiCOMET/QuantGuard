[CmdletBinding()]
param(
    [switch]$SkipRuntimeBuild,
    [switch]$SkipE2E
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'runtime-build-common.ps1')

$frontendRoot = Join-Path $repoRoot 'quant-guard-frontend'
$tauriRoot = Join-Path $frontendRoot 'src-tauri'
$cargoBin = Join-Path $env:USERPROFILE '.cargo\bin'
$cargo = Join-Path $cargoBin 'cargo.exe'
$rustc = Join-Path $cargoBin 'rustc.exe'

foreach ($command in 'node.exe', 'npm.cmd') {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required desktop build command is unavailable: $command"
    }
}
foreach ($command in $cargo, $rustc) {
    if (-not (Test-Path -LiteralPath $command -PathType Leaf)) {
        throw "Rust MSVC toolchain is unavailable: $command"
    }
}
$env:PATH = "$cargoBin;$env:PATH"
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path -LiteralPath $vswhere -PathType Leaf)) {
    throw 'Visual Studio Build Tools detection is unavailable: vswhere.exe was not found.'
}
$visualStudioPath = & $vswhere `
    -latest `
    -products * `
    -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
    -property installationPath
if ([string]::IsNullOrWhiteSpace($visualStudioPath)) {
    throw 'Visual Studio C++ Build Tools for Windows desktop are unavailable.'
}

if (-not $SkipRuntimeBuild) {
    & (Join-Path $PSScriptRoot 'build-desktop-runtimes.ps1')
    Assert-LastExitCode 'Desktop runtime build'
}
& (Join-Path $PSScriptRoot 'stage-tauri-runtimes.ps1')

Push-Location $frontendRoot
try {
    & npm.cmd run format:check
    Assert-LastExitCode 'Frontend format check'
    & npm.cmd run type-check
    Assert-LastExitCode 'Frontend type check'
    & npm.cmd run lint
    Assert-LastExitCode 'Frontend lint'
    if (-not $SkipE2E) {
        & npm.cmd run test:e2e
        Assert-LastExitCode 'Frontend E2E tests'
    }
    & npm.cmd run build
    Assert-LastExitCode 'Frontend build'
} finally {
    Pop-Location
}

Push-Location $tauriRoot
try {
    & $cargo fmt --all -- --check
    Assert-LastExitCode 'Rust format check'
    & $cargo clippy --all-targets --all-features -- -D warnings
    Assert-LastExitCode 'Rust clippy'
    & $cargo test
    Assert-LastExitCode 'Rust tests'
} finally {
    Pop-Location
}

$targetRoot = Join-Path $tauriRoot 'target'
$releaseTarget = Join-Path $targetRoot 'release'
New-Item -ItemType Directory -Path $targetRoot -Force | Out-Null
Remove-SafeBuildDirectory -Parent $targetRoot -Candidate $releaseTarget

Push-Location $frontendRoot
try {
    & npm.cmd run tauri:build
    Assert-LastExitCode 'Tauri NSIS build'
} finally {
    Pop-Location
}

$desktopExecutable = Join-Path $tauriRoot 'target\release\quantguard-desktop.exe'
& (Join-Path $PSScriptRoot 'test-tauri-lifecycle.ps1') `
    -DesktopExecutable $desktopExecutable `
    -ResourceRoot (Join-Path $tauriRoot 'runtime-staging')

$bundleRoot = Join-Path $tauriRoot 'target\release\bundle\nsis'
$installer = @(Get-ChildItem -LiteralPath $bundleRoot -Filter '*-setup.exe' -File)
if ($installer.Count -ne 1) {
    throw "Expected one NSIS installer under $bundleRoot, found $($installer.Count)."
}

Write-Host 'QuantGuard Tauri desktop build complete:'
Write-Host "  Application: $desktopExecutable"
Write-Host "  Installer: $($installer[0].FullName)"
Write-Host ('  Installer size: {0:N2} MB' -f ($installer[0].Length / 1MB))

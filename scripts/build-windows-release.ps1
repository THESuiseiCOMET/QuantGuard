[CmdletBinding()]
param(
    [switch]$AllowDirty,
    [switch]$SkipDefenderScan
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'runtime-build-common.ps1')

$version = '1.2.0'
$frontendRoot = Join-Path $repoRoot 'quant-guard-frontend'
$pythonRoot = Join-Path $repoRoot 'quant-guard-market-data-python'
$tauriRoot = Join-Path $frontendRoot 'src-tauri'
$releaseRoot = Join-Path $repoRoot 'dist\windows-release'
$pythonExe = Join-Path $pythonRoot '.venv\Scripts\python.exe'
$installerName = "QuantGuard_${version}_x64-setup.exe"
$releaseInstaller = Join-Path $releaseRoot $installerName
$checksumPath = "$releaseInstaller.sha256"
$summaryPath = Join-Path $releaseRoot 'release-build-summary.md'
$cargoBin = Join-Path $env:USERPROFILE '.cargo\bin'
$env:PATH = "$cargoBin;$env:PATH"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Operation,
        [Parameter(Mandatory = $true)][scriptblock]$Command
    )

    & $Command
    Assert-LastExitCode $Operation
}

function Get-NativeVersion {
    param(
        [Parameter(Mandatory = $true)][string]$Operation,
        [Parameter(Mandatory = $true)][scriptblock]$Command
    )

    $output = & $Command 2>&1
    Assert-LastExitCode $Operation
    return ($output | Select-Object -First 1).ToString().Trim()
}

if (-not (Test-Path -LiteralPath $pythonExe -PathType Leaf)) {
    if (Get-Command py.exe -ErrorAction SilentlyContinue) {
        & py.exe -3.11 -m venv (Join-Path $pythonRoot '.venv')
    } elseif (Get-Command python.exe -ErrorAction SilentlyContinue) {
        & python.exe -m venv (Join-Path $pythonRoot '.venv')
    } else {
        throw 'Python 3.11 is required to create the release build environment.'
    }
    Assert-LastExitCode 'Python virtual environment creation'
}

Push-Location $repoRoot
try {
    $dirty = & git status --porcelain
    Assert-LastExitCode 'Git status check'
    if (-not $AllowDirty -and $dirty) {
        throw 'Release builds require a clean Git working tree. Commit or stash changes first.'
    }
    $commit = (& git rev-parse HEAD).Trim()
    Assert-LastExitCode 'Git commit lookup'
    $shortCommit = (& git rev-parse --short=7 HEAD).Trim()
    Assert-LastExitCode 'Git short commit lookup'
    $branchOutput = & git branch --show-current
    Assert-LastExitCode 'Git branch lookup'
    $branch = if ($branchOutput) {
        ($branchOutput | Select-Object -First 1).ToString().Trim()
    } elseif (-not [string]::IsNullOrWhiteSpace($env:GITHUB_HEAD_REF)) {
        $env:GITHUB_HEAD_REF.Trim()
    } else {
        'detached'
    }
} finally {
    Pop-Location
}

$package = Get-Content -LiteralPath (Join-Path $frontendRoot 'package.json') -Raw |
    ConvertFrom-Json
$tauriConfig = Get-Content -LiteralPath (Join-Path $tauriRoot 'tauri.conf.json') -Raw |
    ConvertFrom-Json
[xml]$backendPom = Get-Content -LiteralPath (Join-Path $repoRoot 'quant-guard-backend\pom.xml')
Push-Location $pythonRoot
try {
    $pythonVersion = (& $pythonExe -c 'from app import __version__; print(__version__)' 2>$null)
    Assert-LastExitCode 'Python application version check'
} finally {
    Pop-Location
}
if (
    $package.version -ne $version -or
    $tauriConfig.version -ne $version -or
    $backendPom.project.version -ne $version -or
    $pythonVersion.Trim() -ne $version
) {
    throw 'Frontend, Tauri, backend, and Python versions must all match 1.2.0.'
}

$nodeVersion = Get-NativeVersion 'Node.js version check' { node.exe --version }
$npmVersion = Get-NativeVersion 'npm version check' { npm.cmd --version }
$rustVersion = Get-NativeVersion 'Rust version check' { rustc.exe --version }
$cargoVersion = Get-NativeVersion 'Cargo version check' { cargo.exe --version }
$javaVersion = Get-NativeVersion 'Java version check' { java.exe --version }
$pythonRuntimeVersion = Get-NativeVersion 'Python version check' { & $pythonExe --version }

Write-Host "Building QuantGuard $version from $branch at $commit"
Write-Host "  Node.js: $nodeVersion"
Write-Host "  npm: $npmVersion"
Write-Host "  Rust: $rustVersion"
Write-Host "  Cargo: $cargoVersion"
Write-Host "  Java: $javaVersion"
Write-Host "  Python: $pythonRuntimeVersion"

Push-Location $pythonRoot
try {
    Invoke-Checked 'Python dependency installation' {
        & $pythonExe -m pip install --disable-pip-version-check `
            -r requirements.txt `
            -r requirements-build.txt
    }
} finally {
    Pop-Location
}

Push-Location $frontendRoot
try {
    Invoke-Checked 'Frontend locked dependency installation' { & npm.cmd ci }
    Invoke-Checked 'Playwright Chromium installation' {
        & npx.cmd playwright install chromium
    }
} finally {
    Pop-Location
}

& (Join-Path $PSScriptRoot 'build-tauri-desktop.ps1')
Assert-LastExitCode 'QuantGuard desktop build'

$bundleRoot = Join-Path $tauriRoot 'target\release\bundle\nsis'
$installers = @(Get-ChildItem -LiteralPath $bundleRoot -Filter '*-setup.exe' -File)
if ($installers.Count -ne 1) {
    throw "Expected one NSIS installer under $bundleRoot, found $($installers.Count)."
}

Remove-SafeBuildDirectory -Parent (Split-Path -Parent $releaseRoot) -Candidate $releaseRoot
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
Copy-Item -LiteralPath $installers[0].FullName -Destination $releaseInstaller

$hash = Get-FileHash -LiteralPath $releaseInstaller -Algorithm SHA256
"$($hash.Hash.ToLowerInvariant())  $installerName" |
    Set-Content -LiteralPath $checksumPath -Encoding ASCII

$signature = Get-AuthenticodeSignature -LiteralPath $releaseInstaller
$defenderStatus = 'not run'
if (-not $SkipDefenderScan) {
    if (Get-Command Start-MpScan -ErrorAction SilentlyContinue) {
        try {
            Start-MpScan -ScanType CustomScan -ScanPath $releaseInstaller
            $defenderStatus = 'custom scan command completed'
        } catch {
            $defenderStatus = "scan unavailable: $($_.Exception.Message)"
            Write-Warning $defenderStatus
        }
    } else {
        $defenderStatus = 'Windows Defender cmdlet unavailable'
    }
}

$sizeMb = (Get-Item -LiteralPath $releaseInstaller).Length / 1MB
@"
# QuantGuard Windows release build

- Version: $version
- Branch: $branch
- Commit: $commit
- Installer: $installerName
- Size: $($sizeMb.ToString('N2')) MB
- SHA-256: $($hash.Hash.ToLowerInvariant())
- Authenticode: $($signature.Status)
- Defender: $defenderStatus
- Node.js: $nodeVersion
- npm: $npmVersion
- Rust: $rustVersion
- Cargo: $cargoVersion
- Java: $javaVersion
- Python: $pythonRuntimeVersion

The installer is unsigned. Windows SmartScreen may display a warning.
"@ | Set-Content -LiteralPath $summaryPath -Encoding UTF8

Write-Host 'QuantGuard Windows release candidate built:'
Write-Host "  Installer: $releaseInstaller"
Write-Host ('  Size: {0:N2} MB' -f $sizeMb)
Write-Host "  SHA-256: $($hash.Hash.ToLowerInvariant())"
Write-Host "  Signature: $($signature.Status)"
Write-Host "  Defender: $defenderStatus"
Write-Host "  Summary: $summaryPath"

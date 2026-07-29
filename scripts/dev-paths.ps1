function Resolve-QuantGuardDesktopDataRoot {
    param([string]$DataDirectory)

    if (-not [string]::IsNullOrWhiteSpace($DataDirectory)) {
        return [IO.Path]::GetFullPath($DataDirectory)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:QUANTGUARD_DATA_DIR)) {
        return [IO.Path]::GetFullPath($env:QUANTGUARD_DATA_DIR)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:APPDATA)) {
        return Join-Path $env:APPDATA 'QuantGuard'
    }
    if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        return Join-Path $env:USERPROFILE 'QuantGuard'
    }
    throw 'Cannot resolve the desktop data directory. Set QUANTGUARD_DATA_DIR or provide -DataDirectory.'
}

function Get-QuantGuardRuntimeLayout {
    param(
        [string]$RepoRoot,
        [switch]$Desktop,
        [string]$DataDirectory
    )

    if ($Desktop) {
        $dataRoot = Resolve-QuantGuardDesktopDataRoot $DataDirectory
        return @{
            mode = 'Desktop'
            dataRoot = $dataRoot
            runtimeDir = Join-Path $dataRoot 'runtime'
            logDir = Join-Path $dataRoot 'logs'
        }
    }

    $runtimeDir = Join-Path $RepoRoot '.runtime'
    return @{
        mode = 'MySQL'
        dataRoot = $null
        runtimeDir = $runtimeDir
        logDir = Join-Path $runtimeDir 'logs'
    }
}

function Assert-PathWithin {
    param(
        [Parameter(Mandatory = $true)][string]$Parent,
        [Parameter(Mandatory = $true)][string]$Candidate
    )

    $parentPath = [IO.Path]::GetFullPath($Parent).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    ) + [IO.Path]::DirectorySeparatorChar
    $candidatePath = [IO.Path]::GetFullPath($Candidate)
    if (-not $candidatePath.StartsWith($parentPath, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside the expected parent: $candidatePath"
    }
    return $candidatePath
}

function Remove-SafeBuildDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Parent,
        [Parameter(Mandatory = $true)][string]$Candidate
    )

    $safePath = Assert-PathWithin -Parent $Parent -Candidate $Candidate
    if (Test-Path -LiteralPath $safePath) {
        Remove-Item -LiteralPath $safePath -Recurse -Force
    }
}

function Get-DirectorySizeBytes {
    param([Parameter(Mandatory = $true)][string]$Path)

    $sum = Get-ChildItem -LiteralPath $Path -Recurse -File |
        Measure-Object -Property Length -Sum
    if ($null -eq $sum.Sum) {
        return [long]0
    }
    return [long]$sum.Sum
}

function Assert-LastExitCode {
    param([Parameter(Mandatory = $true)][string]$Operation)

    if ($LASTEXITCODE -ne 0) {
        throw "$Operation failed with exit code $LASTEXITCODE."
    }
}

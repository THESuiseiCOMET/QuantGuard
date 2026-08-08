[CmdletBinding()]
param(
    [string]$OutputRoot
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'runtime-build-common.ps1')

$backendProject = Join-Path $repoRoot 'quant-guard-backend'
$mavenWrapper = Join-Path $backendProject 'mvnw.cmd'
$pomPath = Join-Path $backendProject 'pom.xml'
$runtimeRoot = if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    Join-Path $repoRoot 'dist\desktop-runtime'
} else {
    [IO.Path]::GetFullPath($OutputRoot)
}
$targetPath = Join-Path $runtimeRoot 'backend'
$workRoot = Join-Path $repoRoot '.runtime\build'
$inputPath = Join-Path $workRoot 'jpackage-input'
$jpackagePath = Join-Path $workRoot 'jpackage-output'

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw "Maven wrapper is missing: $mavenWrapper"
}
if (-not (Get-Command 'jpackage.exe' -ErrorAction SilentlyContinue)) {
    throw 'jpackage.exe is unavailable. Build with a full Java 21 JDK.'
}

[xml]$pom = Get-Content -LiteralPath $pomPath
$backendVersion = $pom.project.version
if ([string]::IsNullOrWhiteSpace($backendVersion)) {
    throw "Backend version is missing from $pomPath"
}

Push-Location $backendProject
try {
    & $mavenWrapper clean package '-DskipTests'
    Assert-LastExitCode 'Spring Boot package'
} finally {
    Pop-Location
}

$jars = @(Get-ChildItem -LiteralPath (Join-Path $backendProject 'target') -Filter '*.jar' -File |
    Where-Object { $_.Name -notlike '*.original' })
if ($jars.Count -ne 1) {
    throw "Expected exactly one executable Spring Boot JAR, found $($jars.Count)."
}
$jar = $jars[0]

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    $manifestEntry = $archive.GetEntry('META-INF/MANIFEST.MF')
    if ($null -eq $manifestEntry) {
        throw "JAR manifest is missing: $($jar.FullName)"
    }
    $reader = [IO.StreamReader]::new($manifestEntry.Open())
    try {
        $manifest = $reader.ReadToEnd()
    } finally {
        $reader.Dispose()
    }
} finally {
    $archive.Dispose()
}

$mainClassMatch = [regex]::Match($manifest, '(?m)^Main-Class:\s*(.+)\r?$')
$startClassMatch = [regex]::Match($manifest, '(?m)^Start-Class:\s*(.+)\r?$')
if (-not $mainClassMatch.Success -or -not $startClassMatch.Success) {
    throw 'Spring Boot JAR manifest must contain Main-Class and Start-Class.'
}
$mainClass = $mainClassMatch.Groups[1].Value.Trim()
$startClass = $startClassMatch.Groups[1].Value.Trim()
if ($mainClass -notlike 'org.springframework.boot.loader.launch.*') {
    throw "Unexpected Spring Boot launcher class: $mainClass"
}

New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
New-Item -ItemType Directory -Path $workRoot -Force | Out-Null
Remove-SafeBuildDirectory -Parent $runtimeRoot -Candidate $targetPath
Remove-SafeBuildDirectory -Parent $workRoot -Candidate $inputPath
Remove-SafeBuildDirectory -Parent $workRoot -Candidate $jpackagePath
New-Item -ItemType Directory -Path $inputPath -Force | Out-Null
New-Item -ItemType Directory -Path $jpackagePath -Force | Out-Null
Copy-Item -LiteralPath $jar.FullName -Destination $inputPath

$jpackageArguments = @(
    '--type', 'app-image',
    '--name', 'QuantGuardBackend',
    '--app-version', $backendVersion,
    '--vendor', 'vanyiung',
    '--input', $inputPath,
    '--main-jar', $jar.Name,
    '--main-class', $mainClass,
    '--dest', $jpackagePath,
    '--java-options', '-Dfile.encoding=UTF-8',
    '--java-options', '-Dspring.profiles.active=desktop',
    '--win-console'
)
& jpackage.exe @jpackageArguments
Assert-LastExitCode 'jpackage app-image build'

$generatedImage = Join-Path $jpackagePath 'QuantGuardBackend'
if (-not (Test-Path -LiteralPath $generatedImage)) {
    throw "jpackage app-image was not created: $generatedImage"
}
Move-Item -LiteralPath $generatedImage -Destination $targetPath

$launcher = Join-Path $targetPath 'QuantGuardBackend.exe'
$bundledJvm = Join-Path $targetPath 'runtime\bin\server\jvm.dll'
$runtimeRelease = Join-Path $targetPath 'runtime\release'
if (
    -not (Test-Path -LiteralPath $launcher) -or
    -not (Test-Path -LiteralPath $bundledJvm) -or
    -not (Test-Path -LiteralPath $runtimeRelease)
) {
    throw 'Backend app-image is incomplete: launcher or bundled JVM is missing.'
}
$runtimeVersion = (
    Get-Content -LiteralPath $runtimeRelease |
        Where-Object { $_ -like 'JAVA_VERSION=*' } |
        Select-Object -First 1
) -replace '^JAVA_VERSION="(.*)"$', '$1'

$sizeBytes = Get-DirectorySizeBytes -Path $targetPath
Write-Host 'Java desktop runtime built:'
Write-Host "  Path: $targetPath"
Write-Host "  Launcher: $launcher"
Write-Host "  JAR: $($jar.Name)"
Write-Host "  Main-Class: $mainClass"
Write-Host "  Start-Class: $startClass"
Write-Host "  Version: $backendVersion"
Write-Host "  Bundled Java: $runtimeVersion"
Write-Host ('  Size: {0:N2} MB' -f ($sizeBytes / 1MB))

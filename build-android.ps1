param([switch]$SkipExport, [switch]$LocalOnly)
$ErrorActionPreference = 'Stop'
if (-not $SkipExport) {
    & (Join-Path $PSScriptRoot '.venv/Scripts/python.exe') (Join-Path $PSScriptRoot 'tools/export_development.py')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
Push-Location (Join-Path $PSScriptRoot 'android')
try {
    $phaseGradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $phaseCachedGradle = Get-ChildItem (Join-Path $phaseGradleCache 'wrapper/dists/gradle-8.10.2-bin') -Filter gradle.bat -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    $phaseGradle = if ($phaseCachedGradle) { $phaseCachedGradle.FullName } else { Join-Path (Get-Location) 'gradlew.bat' }
    & $phaseGradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $phaseArtifacts = Join-Path $PSScriptRoot 'artifacts/checkpoint-ui'
    New-Item -ItemType Directory -Force -Path $phaseArtifacts | Out-Null
    Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination (Join-Path $phaseArtifacts 'fengshen-town-01-v21-debug.apk')
    Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $phaseArtifacts 'fengshen-town-01-v21-debug.apk')
} finally { Pop-Location }
if (-not $LocalOnly) {
    & (Join-Path $PSScriptRoot 'publish-apk.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'APK build succeeded but publication failed; do not report this release as delivered' }
}

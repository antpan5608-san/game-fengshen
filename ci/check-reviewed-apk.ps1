param([Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedSha256,
      [Parameter(Mandatory)][ValidatePattern('^[0-9]+$')][string]$BuildRunID)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$apks=@(Get-ChildItem (Join-Path $root 'artifacts/ci/*-release.apk'))
if($apks.Count -ne 1){throw 'Expected exactly one reviewed APK'}
$receipt=Get-Content (Join-Path $root 'artifacts/ci/apk-receipt.json') -Raw|ConvertFrom-Json
if($receipt.sourceCommit -ne $env:GITHUB_SHA -or $receipt.buildRunID -ne $BuildRunID -or $receipt.sha256 -ne $ExpectedSha256 -or (Get-FileHash $apks[0].FullName).Hash.ToLowerInvariant() -ne $ExpectedSha256){throw 'Review provenance or APK bytes mismatch'}
$verified=Join-Path $root 'artifacts/ci/reverified.json'
& python (Join-Path $root 'tools/ci_apk.py') verify --apk $apks[0].FullName --output $verified --code $receipt.versionCode --name $receipt.versionName
if($LASTEXITCODE -ne 0){throw 'Reviewed signature/content/version failed revalidation'}

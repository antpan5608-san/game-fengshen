param([Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedSha256,
      [Parameter(Mandatory)][ValidatePattern('^[0-9]+$')][string]$BuildRunID)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$apks=@(Get-ChildItem (Join-Path $root 'artifacts/ci/*-release.apk'))
if($apks.Count -ne 1){throw 'Expected exactly one reviewed APK'}
$receipt=Get-Content (Join-Path $root 'artifacts/ci/apk-receipt.json') -Raw|ConvertFrom-Json
if($receipt.sourceCommit -ne $env:GITHUB_SHA -or $receipt.buildRunID -ne $BuildRunID -or $receipt.sha256 -ne $ExpectedSha256 -or (Get-FileHash $apks[0].FullName).Hash.ToLowerInvariant() -ne $ExpectedSha256){throw 'Review provenance or APK bytes mismatch'}
$runtime=Get-Content (Join-Path $root 'artifacts/runtime-review/town02-runtime/runtime-receipt.json') -Raw|ConvertFrom-Json
if($runtime.sourceCommit -ne $env:GITHUB_SHA -or $runtime.buildRunID -ne $BuildRunID -or $runtime.sha256 -ne $ExpectedSha256 -or $runtime.contentHash -ne $receipt.contentHash -or $runtime.runtime -ne 'PASS' -or $runtime.upgrade -ne 'PASS' -or $runtime.normalHerbSupply -ne 'PASS' -or $runtime.touchUx -ne 'PASS' -or $runtime.phoneSizedLayout -ne 'PASS'){throw 'Runtime receipt must verify the same reviewed APK/source/content'}
$verified=Join-Path $root 'artifacts/ci/reverified.json'
& python (Join-Path $root 'tools/ci_apk.py') verify --apk $apks[0].FullName --output $verified --code $receipt.versionCode --name $receipt.versionName
if($LASTEXITCODE -ne 0){throw 'Reviewed signature/content/version failed revalidation'}

# A Nanhai candidate must prove the actual continuous route and the exact Boss/victory artifact.
if($runtime.nanhaiNormalRoute -ne 'PASS' -or $runtime.nanhaiBossVictory -ne 'PASS' -or $runtime.nanhaiOnceAndColdRestart -ne 'PASS'){
    throw 'Nanhai normal App route/Boss/once-and-restart gates are required'
}

if($runtime.mobileGrowth -ne 'PASS' -or $runtime.mobileEnemyInformation -ne 'PASS' -or $runtime.mobileDirectTouch -ne 'PASS' -or $runtime.mobileActionSnapshots -ne 'PASS'){
    throw 'Mobile growth/battle touch/snapshot gates must verify the same reviewed APK'
}

if($runtime.battleHerb -ne 'PASS'){throw 'This candidate must verify the scoped battle herb touch/order/save gate'}

if($runtime.worldCurrentServices -ne 'PASS'){throw 'Current village services must verify the reviewed APK'}
if($runtime.worldSeaNorth -ne 'PASS' -or $runtime.worldStatusAndAntidote -ne 'PASS' -or $runtime.worldSaveProtection -ne 'PASS'){throw 'World north/status/save gates must verify this exact APK'}

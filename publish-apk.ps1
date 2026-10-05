# Adapted from Language's publish-minor-apk.ps1; its hardcoded Language targets
# must never be used here. Shared credential/upgrade helpers remain read-only.
param(
    [string]$ApkPath = (Join-Path $PSScriptRoot 'artifacts/checkpoint-ui/fengshen-town-01-v21-debug.apk'),
    [switch]$CheckOnly,
    [string]$LanguageWorkspace = 'C:/Users/antpan/Documents/language',
    [string]$CredentialFile = (Join-Path $PSScriptRoot 'private-inputs/oss-ssh.clixml')
)
$ErrorActionPreference = 'Stop'
$bucket = 'kubernetes-fleetpilot'
$base = 'https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com'
$object = 'artifacts/fengshen-remake/app/fengshen-remake.apk.bin'
$metadataObject = 'artifacts/fengshen-remake/app/version.json'
$expectedSigner = '5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6'
$archive = Join-Path $PSScriptRoot 'artifacts/published'
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$aapt = Join-Path $sdk 'build-tools/35.0.0/aapt.exe'
$signer = Join-Path $sdk 'build-tools/35.0.0/apksigner.bat'
$badging = & $aapt dump badging $apk
if ($LASTEXITCODE -ne 0) { throw 'aapt failed' }
$package = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
if ($package -notmatch "^package: name='org.fengshen.dev' versionCode='([0-9]+)' versionName='([^']+)'") {
    throw 'Only the Fengshen package org.fengshen.dev may be published'
}
$code = [int]$Matches[1]; $name = $Matches[2]
$cert = & $signer verify --print-certs $apk
if ($LASTEXITCODE -ne 0 -or -not ($cert -match "Signer #1 certificate SHA-256 digest: $expectedSigner")) {
    throw 'APK signature invalid or changed: preserve the installed development signer'
}
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item -LiteralPath $apk).Length
$url = "${base}/${object}?v=$code"
if ($CheckOnly) {
    [ordered]@{status='LOCAL_CHECK_PASS'; package='org.fengshen.dev'; versionCode=$code; versionName=$name;
        signerSha256=$expectedSigner; sha256=$hash; sizeBytes=$size; apkUrl=$url;
        metadataUrl="${base}/${metadataObject}"; uploaded=$false} | ConvertTo-Json
    exit 0
}

$task=Get-Content (Join-Path $PSScriptRoot 'docs/current-task.md') -Raw
if($task -notmatch '(?m)^task_id:\s*([A-Z0-9-]+)'){throw 'Publication needs current task_id'}
$publicationTask=$Matches[1]
$inspection=Get-Content (Join-Path $PSScriptRoot 'reports/runtime-preflight.json') -Raw|ConvertFrom-Json -AsHashtable
if($inspection.task_id -ne $publicationTask -or $inspection.stage -ne 'preflight' -or
   ([DateTimeOffset]::UtcNow-[DateTimeOffset]::Parse($inspection.queriedAt)).TotalDays -gt 7){throw 'Run current task runtime preflight before publication; unrelated/stale audit is not accepted'}

& node (Join-Path $PSScriptRoot 'server/runtime-summary.mjs') --release-assessment (Join-Path $PSScriptRoot 'reports/runtime-preflight.json') (Join-Path $PSScriptRoot 'ci/runtime-nonblocking-issues.json') | Set-Content (Join-Path $PSScriptRoot 'reports/runtime-preflight-decision.json') -Encoding utf8NoBOM
if($LASTEXITCODE -ne 0){throw 'Runtime inspection unavailable or unresolved new/unknown/blocking errors; upload prohibited'}

if (-not $env:ALIYUN_ACCESS_KEY_ID -or -not $env:ALIYUN_ACCESS_KEY_SECRET) {
    if($env:GITHUB_ACTIONS -eq 'true'){throw 'Upload blocked: configure production GitHub OSS Secrets; no local credential fallback in CI'}
    $savedRemote = @{}
    foreach ($key in @('REMOTE_HOST','REMOTE_PORT','REMOTE_USER','REMOTE_PASS')) { $savedRemote[$key]=[Environment]::GetEnvironmentVariable($key) }
    try {
        if (-not $env:REMOTE_PASS -and (Test-Path -LiteralPath $CredentialFile)) {
            $credential = Import-Clixml -LiteralPath $CredentialFile
            if ($credential -isnot [System.Management.Automation.PSCredential]) { throw 'Expected a Windows-protected PSCredential' }
            $env:REMOTE_HOST='204.44.123.101'; $env:REMOTE_PORT='10080'
            $env:REMOTE_USER=$credential.UserName; $env:REMOTE_PASS=$credential.GetNetworkCredential().Password
        }
        if (-not $env:REMOTE_HOST -or -not $env:REMOTE_PASS) {
            throw 'Upload blocked: configure process ALIYUN_* OSS variables, REMOTE_HOST/REMOTE_PASS, or run ./configure-publish.ps1 locally. Never paste secrets into logs.'
        }
        $bridge = Join-Path $LanguageWorkspace 'tools/publish_apk_with_remote_oss_env.mjs'
        & node $bridge '/etc/opsfleet-language/server.env' $PSCommandPath -ApkPath $apk -LanguageWorkspace $LanguageWorkspace
        if ($LASTEXITCODE -ne 0) { throw 'Protected credential loader / publisher failed' }
    } finally {
        foreach ($key in $savedRemote.Keys) { [Environment]::SetEnvironmentVariable($key,$savedRemote[$key]) }
    }
    exit 0
}
if ($env:ALIYUN_OSS_BUCKET -ne $bucket -or $env:ALIYUN_OSS_PUBLIC_BASE_URL.TrimEnd('/') -ne $base -or
    $env:ALIYUN_OSS_ENDPOINT -notmatch '^(https?://)?oss-cn-beijing\.aliyuncs\.com/?$') {
    throw 'OSS destination differs from the approved platform; no upload performed'
}
$oss = (Get-Command ossutil -ErrorAction Stop).Source
New-Item -ItemType Directory -Force -Path $archive | Out-Null
$work = Join-Path $archive ('pending-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$oldEnv = @{}
foreach ($key in @('OSS_ACCESS_KEY_ID','OSS_ACCESS_KEY_SECRET','OSS_ENDPOINT','OSS_REGION')) { $oldEnv[$key]=[Environment]::GetEnvironmentVariable($key) }
try {
    $remoteMetadata = Join-Path $work 'remote.json'
    $status = & curl.exe -sSL --max-time 60 -w '%{http_code}' "${base}/${metadataObject}?check=$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())" -o $remoteMetadata
    if ($LASTEXITCODE -ne 0 -or $status -notin @('200','404')) { throw "Cannot check existing Fengshen release: HTTP $status" }
    if ($status -eq '200') {
        $old = Get-Content -LiteralPath $remoteMetadata -Raw | ConvertFrom-Json
        if ($old.package -ne 'org.fengshen.dev' -or $old.signerSha256 -ne $expectedSigner -or $old.versionCode -gt $code -or
            ($old.sha256 -ne $hash -and $old.versionCode -ge $code)) { throw 'Package/signer/version continuity failed; changed APK requires higher versionCode' }
        if ($old.sha256 -ne $hash) {
            $oldApk = Join-Path $work 'previous.apk'
            & curl.exe -fsSL --max-time 180 "${base}/${object}?v=$($old.versionCode)" -o $oldApk
            if ($LASTEXITCODE -ne 0 -or (Get-FileHash -LiteralPath $oldApk).Hash.ToLowerInvariant() -ne $old.sha256 -or
                (Get-Item -LiteralPath $oldApk).Length -ne $old.sizeBytes) { throw 'Cannot preserve verified previous APK' }
            & python (Join-Path $PSScriptRoot 'tools/ci_apk.py') upgrade --old-apk $oldApk --apk $apk
            if ($LASTEXITCODE -ne 0) { throw 'Upgrade compatibility failed' }
            Copy-Item -LiteralPath $oldApk -Destination (Join-Path $archive 'previous.apk')
            Copy-Item -LiteralPath $remoteMetadata -Destination (Join-Path $archive 'previous.json')
        } elseif(-not (Test-Path (Join-Path $archive 'previous.json'))) {
            # Idempotent retry on a fresh runner: preserve the previous release
            # from the trusted server inspection, rather than dropping its logs.
            $prior=$inspection.result.retention.allowedReleases|Where-Object {[int]$_.versionCode -lt $code}|Sort-Object versionCode -Descending|Select-Object -First 1
            if($prior){
                $prior.package='org.fengshen.dev';$prior.signerSha256=$expectedSigner
                $prior|ConvertTo-Json|Set-Content (Join-Path $archive 'previous.json') -Encoding utf8NoBOM
            }
        }
    }
    $metadata = [ordered]@{package='org.fengshen.dev'; channel='development'; versionCode=$code; versionName=$name;
        apkUrl=$url; sha256=$hash; sizeBytes=$size; signerSha256=$expectedSigner;
        publishedAt=[DateTime]::UtcNow.ToString('o'); notes='Fengshen scoped development build; existing game content and saves preserved. Original-content gaps and OnePlus 13T verification remain; see delivery report.'}
    $metadataPath = Join-Path $work 'version.json'
    $scopePath=Join-Path $PSScriptRoot 'ci/runtime-scope.json'
    if((Test-Path $scopePath) -and ((Get-Content $scopePath -Raw|ConvertFrom-Json).quality -eq 'PERSONAL_TEST')){
        $metadata['quality']='PERSONAL_TEST'
        $metadata['manual_acceptance']='PENDING'
        $metadata.notes='Personal test build; short isolated smoke and artifact checks passed. Complete story, real-device touch, sound and long-play acceptance PENDING; see personal test delivery.'
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8NoBOM
    $env:OSS_ACCESS_KEY_ID=$env:ALIYUN_ACCESS_KEY_ID
    $env:OSS_ACCESS_KEY_SECRET=$env:ALIYUN_ACCESS_KEY_SECRET
    $env:OSS_ENDPOINT=$env:ALIYUN_OSS_ENDPOINT
    $env:OSS_REGION='cn-beijing'
    # Match Language's binary-object distribution contract. An .apk filename
    # in Content-Disposition is rejected by the default OSS endpoint.
    & $oss cp -f $apk "oss://$bucket/$object" --content-type 'application/octet-stream' --cache-control 'no-cache'
    if ($LASTEXITCODE -ne 0) { throw 'Fengshen APK upload failed; remote result unverified' }
    $download = Join-Path $work 'verified.apk'
    & curl.exe -fsSL --retry 2 --retry-delay 2 --max-time 180 $url -o $download
    if ($LASTEXITCODE -ne 0 -or (Get-FileHash -LiteralPath $download).Hash.ToLowerInvariant() -ne $hash -or
        (Get-Item -LiteralPath $download).Length -ne $size) { throw 'Public APK checksum/size verification failed; metadata not updated' }
    & $oss cp -f $metadataPath "oss://$bucket/$metadataObject" --content-type 'application/json' --cache-control 'no-cache'
    if ($LASTEXITCODE -ne 0) { throw 'Fengshen version metadata upload failed' }
    $checkedMetadata = Join-Path $work 'verified.json'
    & curl.exe -fsSL --max-time 60 "${base}/${metadataObject}?v=$code" -o $checkedMetadata
    if ($LASTEXITCODE -ne 0 -or (Get-FileHash -LiteralPath $checkedMetadata).Hash -ne (Get-FileHash -LiteralPath $metadataPath).Hash) {
        throw 'Public metadata verification failed'
    }
    Copy-Item -LiteralPath $apk -Destination (Join-Path $archive 'current.apk')
    Copy-Item -LiteralPath $metadataPath -Destination (Join-Path $archive 'current.json')
    & (Join-Path $PSScriptRoot 'server/register-release.ps1')
    if($LASTEXITCODE -ne 0){throw 'APK published, but diagnostic version retention failed: retry register-release.ps1; delivery incomplete'}
    foreach ($file in @($remoteMetadata,$metadataPath,$download,$checkedMetadata,(Join-Path $work 'previous.apk'))) {
        if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    }
    Remove-Item -LiteralPath $work
    Write-Host "PUBLISHED_AND_VERIFIED: $url"
    Write-Host "SHA256: $hash; bytes: $size"
} finally {
    foreach ($key in $oldEnv.Keys) { [Environment]::SetEnvironmentVariable($key,$oldEnv[$key]) }
    # On failure keep this attempt's files for recovery; no credentials are written.
}

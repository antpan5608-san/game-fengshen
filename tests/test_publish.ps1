$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$publisher = Join-Path $root 'publish-apk.ps1'
$saved = @{}
$keys = @('ALIYUN_ACCESS_KEY_ID','ALIYUN_ACCESS_KEY_SECRET','ALIYUN_OSS_BUCKET','ALIYUN_OSS_PUBLIC_BASE_URL','ALIYUN_OSS_ENDPOINT','REMOTE_HOST','REMOTE_PASS')
foreach ($key in $keys) { $saved[$key]=[Environment]::GetEnvironmentVariable($key) }
function Invoke-Publisher([string[]]$Options) {
    $result = & pwsh -NoProfile -File $publisher @Options 2>&1
    return @{Code=$LASTEXITCODE; Output=($result | Out-String)}
}
try {
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key,$null) }
    $ok = Invoke-Publisher @('-CheckOnly')
    if ($ok.Code -ne 0) { throw $ok.Output }
    $check = $ok.Output | ConvertFrom-Json
    if ($check.package -ne 'org.fengshen.dev' -or $check.uploaded -or
        $check.apkUrl -ne "https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=$($check.versionCode)") { throw 'Wrong publication target' }
    'PASS: local APK verification / Fengshen-only target'
    $missing = Invoke-Publisher @('-CredentialFile',(Join-Path $root 'private-inputs/absent-test-credential.clixml'))
    if ($missing.Code -eq 0 -or $missing.Output -notmatch 'Upload blocked') { throw 'Missing credentials did not fail closed' }
    'PASS: missing credentials reject before network'
    $env:ALIYUN_ACCESS_KEY_ID='test-only'; $env:ALIYUN_ACCESS_KEY_SECRET='test-only'
    $env:ALIYUN_OSS_BUCKET='wrong-bucket'; $env:ALIYUN_OSS_PUBLIC_BASE_URL='https://invalid.example'
    $env:ALIYUN_OSS_ENDPOINT='oss-cn-beijing.aliyuncs.com'
    $wrong = Invoke-Publisher @()
    if ($wrong.Code -eq 0 -or $wrong.Output -notmatch 'OSS destination differs') { throw 'Wrong platform accepted' }
    'PASS: wrong platform rejects before upload'
    $invalid = Invoke-Publisher @('-CheckOnly','-ApkPath',(Join-Path $root 'README.md'))
    if ($invalid.Code -eq 0 -or $invalid.Output -notmatch 'aapt failed') { throw 'Invalid APK accepted' }
    'PASS: invalid APK rejected'
    $reportPath=Join-Path $root 'reports/runtime-preflight.json'
    $reportBytes=[IO.File]::ReadAllBytes($reportPath)
    try {
        $report=Get-Content $reportPath -Raw|ConvertFrom-Json -AsHashtable
        $report.task_id='UNRELATED-TEST'
        $report|ConvertTo-Json -Depth 30|Set-Content $reportPath -Encoding utf8NoBOM
        $mismatch=Invoke-Publisher @()
        if($mismatch.Code -eq 0 -or $mismatch.Output -notmatch 'current task runtime preflight'){throw 'Unrelated preflight accepted'}
        'PASS: unrelated task preflight rejected before network'
        $report.task_id=((Get-Content (Join-Path $root 'docs/current-task.md') -Raw) -replace '(?s).*?task_id:\s*([A-Z0-9-]+).*','$1')
        $report.queriedAt='2020-01-01T00:00:00Z'
        $report|ConvertTo-Json -Depth 30|Set-Content $reportPath -Encoding utf8NoBOM
        $stale=Invoke-Publisher @()
        if($stale.Code -eq 0 -or $stale.Output -notmatch 'current task runtime preflight'){throw 'Stale preflight accepted'}
        'PASS: stale preflight rejected before network'
    } finally {[IO.File]::WriteAllBytes($reportPath,$reportBytes)}
} finally {
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key,$saved[$key]) }
}
exit 0

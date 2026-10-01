param([ValidateSet('preflight','postflight')][string]$Stage='preflight',
      [switch]$SummaryOnly)
$ErrorActionPreference='Stop'
$taskText=Get-Content (Join-Path $PSScriptRoot 'docs/current-task.md') -Raw
if ($taskText -notmatch '(?m)^task_id:\s*([A-Z0-9-]+)') {throw 'Current task must name task_id before runtime inspection'}
$runtimeTask=$Matches[1]
$saved=@{}
foreach($k in @('REMOTE_HOST','REMOTE_PORT','REMOTE_USER','REMOTE_PASS')) {$saved[$k]=[Environment]::GetEnvironmentVariable($k)}
$result=$null
try {
    if(-not $env:REMOTE_PASS) {
        $credential=Import-Clixml (Join-Path $PSScriptRoot 'private-inputs/oss-ssh.clixml')
        $env:REMOTE_PASS=$credential.GetNetworkCredential().Password;$env:REMOTE_USER=$credential.UserName
    }
    $raw=& node (Join-Path $PSScriptRoot 'server/runtime-admin.mjs') summary
    if($LASTEXITCODE -ne 0) {throw 'Protected diagnostic query failed'}
    # app_start legitimately precedes content loading, so contentVersion may be
    # empty. Hashtable parsing supports this JSON map key without inventing a version.
    $result=($raw -join "`n")|ConvertFrom-Json -AsHashtable
} catch {$result=[ordered]@{status='UNAVAILABLE';reason='protected_query_or_configuration_unavailable'}}
finally {foreach($k in $saved.Keys){[Environment]::SetEnvironmentVariable($k,$saved[$k])}}
if($SummaryOnly) {
    # Preserve counts/coverage and trusted release authority, never individual events,
    # stacks, installation IDs or raw historical diagnostics in logs/artifacts.
    $summary=[ordered]@{}
    foreach($key in @('status','reason','queriedAt','queryRange','lastReportedAt','eventCount',
        'sessionCount','emulatorSessions','realDeviceSessions','testEventCount','errors','coverageLimits')) {
        if($result.Contains($key)) {$summary[$key]=$result[$key]}
    }
    if($result.Contains('retention')) {
        $summary.retention=[ordered]@{}
        foreach($key in @('allowedReleases','cleanupFailures','storedVersionCounts','releaseAuthority')) {
            if($result.retention.Contains($key)) {$summary.retention[$key]=$result.retention[$key]}
        }
    }
    $result=$summary
}
$report=[ordered]@{task_id=$runtimeTask;stage=$Stage;queriedAt=[DateTime]::UtcNow.ToString('o');environment='fleetpilots.com/fengshen-api';result=$result;
    coverageLimits=@('Only instrumented applications that actually upload are represented.','No real-device samples is not evidence of real-device health.','Historical v15/v16 have no client diagnostics.','Query does not change releases, logs, saves or accounts.')}
$dir=Join-Path $PSScriptRoot 'reports';New-Item -ItemType Directory -Force $dir|Out-Null
$report|ConvertTo-Json -Depth 30|Set-Content (Join-Path $dir "runtime-$Stage.json") -Encoding utf8NoBOM
$md=@("# Runtime $Stage",'',"Task: $runtimeTask", "UTC: $($report.queriedAt)","Environment: $($report.environment)","Status: $($result.status)",'','```json',($result|ConvertTo-Json -Depth 30),'```','',($report.coverageLimits -join "`n"))
$md|Set-Content (Join-Path $dir "runtime-$Stage.md") -Encoding utf8NoBOM
$report|ConvertTo-Json -Depth 30

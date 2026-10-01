param([string]$Current=(Join-Path $PSScriptRoot '../artifacts/published/current.json'),[string]$Previous=(Join-Path $PSScriptRoot '../artifacts/published/previous.json'))
$ErrorActionPreference='Stop'
$records=@()
foreach($file in @($Current,$Previous)) {
 if(Test-Path -LiteralPath $file) {
  $m=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
  if($m.package -ne 'org.fengshen.dev' -or $m.signerSha256 -ne '5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6'){throw 'Untrusted Fengshen release metadata'}
  $records+=@{versionCode=$m.versionCode;versionName=$m.versionName;sha256=$m.sha256;publishedAt=$m.publishedAt}
 }
}
$public=Invoke-RestMethod ('https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/version.json?check='+[DateTimeOffset]::UtcNow.ToUnixTimeSeconds())
if($records.Count -lt 1 -or $public.sha256 -ne $records[0].sha256 -or $public.versionCode -ne $records[0].versionCode){throw 'Release has not actually been published and verified'}
$payload=Join-Path $PSScriptRoot '../artifacts/published/diagnostic-releases.json'
@{releases=$records}|ConvertTo-Json -Depth 5|Set-Content $payload -Encoding utf8NoBOM
$saved=@{};foreach($k in @('REMOTE_PASS','REMOTE_USER')){$saved[$k]=[Environment]::GetEnvironmentVariable($k)}
try {
 if(-not $env:REMOTE_PASS){$c=Import-Clixml (Join-Path $PSScriptRoot '../private-inputs/oss-ssh.clixml');$env:REMOTE_PASS=$c.GetNetworkCredential().Password;$env:REMOTE_USER=$c.UserName}
 $raw=& node (Join-Path $PSScriptRoot 'runtime-admin.mjs') releases $payload
 if($LASTEXITCODE -ne 0){throw 'Release retention update failed'}
 $result=($raw -join "`n")|ConvertFrom-Json
 if($result.status -or $result.cleanupFailures -ne 0){throw 'Release cleanup incomplete; retry this script'}
 $result|ConvertTo-Json -Depth 15|Set-Content (Join-Path $PSScriptRoot '../reports/runtime-retention.json') -Encoding utf8NoBOM
 $result|ConvertTo-Json -Depth 15
} finally {foreach($k in $saved.Keys){[Environment]::SetEnvironmentVariable($k,$saved[$k])}}

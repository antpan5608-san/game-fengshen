param([switch]$Signing,[switch]$Server,[switch]$OssEnvironmentOnly,
      [string]$Repository='antpan5608-san/game-fengshen',
      [string]$GhPath='C:/Program Files/GitHub CLI/gh.exe',
      [string]$CredentialFile=(Join-Path $PSScriptRoot 'private-inputs/oss-ssh.clixml'))
$ErrorActionPreference='Stop'
function Set-ProtectedSecret([string]$Name,[string]$Value,[string]$Environment) {
    if([string]::IsNullOrEmpty($Value)){throw "Missing protected value for $Name"}
    $args=@('secret','set',$Name,'--repo',$Repository)
    if($Environment){$args+=@('--env',$Environment)}
    $Value|& $GhPath @args
    if($LASTEXITCODE -ne 0){throw "GitHub Secret configuration failed: $Name"}
    Write-Host "Configured Secret: $Name (value withheld)"
}
if($OssEnvironmentOnly) {
    Set-ProtectedSecret 'ALIYUN_ACCESS_KEY_ID' $env:ALIYUN_ACCESS_KEY_ID 'fengshen-production'
    Set-ProtectedSecret 'ALIYUN_ACCESS_KEY_SECRET' $env:ALIYUN_ACCESS_KEY_SECRET 'fengshen-production'
    exit 0
}
if($Signing) {
    $temporary=Join-Path ([IO.Path]::GetTempPath()) ('fengshen-signing-'+[guid]::NewGuid().ToString('N')+'.json')
    $saved=@{}
    foreach($k in @('FENGSHEN_SIGNING_EXPORT','FENGSHEN_KEYSTORE_PATH','FENGSHEN_KEYSTORE_PASSWORD','FENGSHEN_KEY_ALIAS','FENGSHEN_KEY_PASSWORD')){$saved[$k]=[Environment]::GetEnvironmentVariable($k)}
    try {
        $env:FENGSHEN_SIGNING_EXPORT=$temporary
        Push-Location (Join-Path $PSScriptRoot 'android')
        try {
            & ./gradlew.bat --no-daemon --console=plain -I ../ci/export-existing-signing.gradle :app:exportExistingSigningForCI
            if($LASTEXITCODE -ne 0){throw 'Existing signing configuration export failed'}
        } finally {Pop-Location}
        $cfg=Get-Content -LiteralPath $temporary -Raw|ConvertFrom-Json
        $env:FENGSHEN_KEYSTORE_PATH=$cfg.path
        $env:FENGSHEN_KEYSTORE_PASSWORD=$cfg.storePassword
        $env:FENGSHEN_KEY_ALIAS=$cfg.alias
        $env:FENGSHEN_KEY_PASSWORD=$cfg.keyPassword
        # Prove the exported key signs as the installed app BEFORE configuring Secrets.
        & (Join-Path $PSScriptRoot 'build-ci.ps1') -VersionCode 22 -VersionName '0.8.2-ci-release' -ContentApk (Join-Path $PSScriptRoot 'artifacts/checkpoint-ui/fengshen-town-01-v21-debug.apk')
        if($LASTEXITCODE -ne 0){throw 'Existing key/content verification failed'}
        Set-ProtectedSecret 'FENGSHEN_KEYSTORE_BASE64' ([Convert]::ToBase64String([IO.File]::ReadAllBytes($cfg.path))) ''
        Set-ProtectedSecret 'FENGSHEN_KEYSTORE_PASSWORD' $cfg.storePassword ''
        Set-ProtectedSecret 'FENGSHEN_KEY_ALIAS' $cfg.alias ''
        Set-ProtectedSecret 'FENGSHEN_KEY_PASSWORD' $cfg.keyPassword ''
    } finally {
        if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}
        foreach($k in $saved.Keys){[Environment]::SetEnvironmentVariable($k,$saved[$k])}
        $cfg=$null
    }
}
if($Server) {
    $saved=@{}
    foreach($k in @('REMOTE_HOST','REMOTE_PORT','REMOTE_USER','REMOTE_PASS')){$saved[$k]=[Environment]::GetEnvironmentVariable($k)}
    try {
        if(-not $env:REMOTE_PASS){$cred=Import-Clixml -LiteralPath $CredentialFile;$env:REMOTE_PASS=$cred.GetNetworkCredential().Password;$env:REMOTE_USER=$cred.UserName}
        if(-not $env:REMOTE_HOST){$env:REMOTE_HOST='204.44.123.101'}
        if(-not $env:REMOTE_PORT){$env:REMOTE_PORT='10080'}
        foreach($k in @('REMOTE_HOST','REMOTE_PORT','REMOTE_USER','REMOTE_PASS')){Set-ProtectedSecret $k ([Environment]::GetEnvironmentVariable($k)) 'fengshen-production'}
        if($env:ALIYUN_ACCESS_KEY_ID -and $env:ALIYUN_ACCESS_KEY_SECRET) {
            Set-ProtectedSecret 'ALIYUN_ACCESS_KEY_ID' $env:ALIYUN_ACCESS_KEY_ID 'fengshen-production'
            Set-ProtectedSecret 'ALIYUN_ACCESS_KEY_SECRET' $env:ALIYUN_ACCESS_KEY_SECRET 'fengshen-production'
        } else {
            # Existing credential helper is READ ONLY; it does not publish or modify Language.
            & node 'C:/Users/antpan/Documents/language/tools/publish_apk_with_remote_oss_env.mjs' '/etc/opsfleet-language/server.env' $PSCommandPath -OssEnvironmentOnly -Repository $Repository -GhPath $GhPath
            if($LASTEXITCODE -ne 0){throw 'Protected existing OSS credential loader failed'}
        }
    } finally {foreach($k in $saved.Keys){[Environment]::SetEnvironmentVariable($k,$saved[$k])}}
}
if(-not $Signing -and -not $Server){throw 'Specify -Signing or -Server; no secrets are printed'}

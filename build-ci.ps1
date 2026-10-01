param(
    [Parameter(Mandatory)][ValidateRange(22,2100000000)][int]$VersionCode,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._-]{1,80}$')][string]$VersionName,
    [string]$ContentApk
)
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
if((Get-FileHash (Join-Path $root 'android/gradle/wrapper/gradle-wrapper.jar')).Hash.ToLowerInvariant() -ne '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'){throw 'Gradle wrapper differs from official 8.10.2 checksum'}
$dir=Join-Path $root 'artifacts/ci'
New-Item -ItemType Directory -Force $dir|Out-Null
$savedKey=$env:FENGSHEN_KEYSTORE_PATH
$temporaryKey=$null
Push-Location $root
try {
    if(-not $env:FENGSHEN_KEYSTORE_PATH) {
        if(-not $env:FENGSHEN_KEYSTORE_BASE64){throw 'Configure FENGSHEN_KEYSTORE_BASE64 or a protected local FENGSHEN_KEYSTORE_PATH'}
        $temporaryKey=Join-Path ([IO.Path]::GetTempPath()) ('fengshen-signing-'+[guid]::NewGuid().ToString('N')+'.keystore')
        [IO.File]::WriteAllBytes($temporaryKey,[Convert]::FromBase64String($env:FENGSHEN_KEYSTORE_BASE64))
        $env:FENGSHEN_KEYSTORE_PATH=$temporaryKey
    }
    foreach($key in @('FENGSHEN_KEYSTORE_PASSWORD','FENGSHEN_KEY_ALIAS','FENGSHEN_KEY_PASSWORD')) {
        if(-not [Environment]::GetEnvironmentVariable($key)){throw "Missing signing secret: $key"}
    }
    $restore=@('tools/ci_apk.py','restore','--code',"$VersionCode")
    if($ContentApk){$restore+=@('--apk',$ContentApk)}
    & python @restore
    if($LASTEXITCODE -ne 0){throw 'Existing content restoration failed'}
    & python -m unittest discover -s tests -p test_ci_apk.py
    if($LASTEXITCODE -ne 0){throw 'CI safety tests failed'}
    Push-Location (Join-Path $root 'android')
    try {
        & ./gradlew.bat --no-daemon --console=plain "-PfengshenVersionCode=$VersionCode" "-PfengshenVersionName=$VersionName" :app:testReleaseUnitTest :app:assembleRelease
        if($LASTEXITCODE -ne 0){throw 'Release build or unit tests failed; upload prohibited'}
    } finally {Pop-Location}
    $apk=Join-Path $dir "fengshen-remake-v$VersionCode-release.apk"
    Copy-Item (Join-Path $root 'android/app/build/outputs/apk/release/app-release.apk') $apk
    & python tools/ci_apk.py verify --apk $apk --output (Join-Path $dir 'apk-receipt.json') --code $VersionCode --name $VersionName
    if($LASTEXITCODE -ne 0){throw 'Signed APK/content/version verification failed'}
    (Get-FileHash $apk).Hash.ToLowerInvariant()+'  '+(Split-Path $apk -Leaf)|Set-Content (Join-Path $dir 'SHA256SUMS') -Encoding ascii
    Write-Host "READY_FOR_REVIEW: $apk (no server upload performed)"
} finally {
    $env:FENGSHEN_KEYSTORE_PATH=$savedKey
    if($temporaryKey -and (Test-Path -LiteralPath $temporaryKey)){Remove-Item -LiteralPath $temporaryKey}
    Pop-Location
}

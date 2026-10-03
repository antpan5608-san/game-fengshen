param(
    [Parameter(Mandatory)][ValidateRange(22,2100000000)][int]$VersionCode,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9._-]{1,80}$')][string]$VersionName,
    [string]$ContentApk,
    [switch]$RuntimeTests
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
    & python -m unittest discover -s tests -p test_town02_export.py
    if($LASTEXITCODE -ne 0){throw 'Scoped content export tests failed'}
    & python -m unittest discover -s tests -p test_nanhai_export.py
    if($LASTEXITCODE -ne 0){throw 'Nanhai scoped content gates failed'}
    & python -m unittest discover -s tests -p test_world_export.py
    if($LASTEXITCODE -ne 0){throw 'World content/service gates failed'}
    & python -m unittest discover -s tests -p test_world_north_export.py
    if($LASTEXITCODE -ne 0){throw 'World north/status content gates failed'}
    & python -m unittest discover -s tests -p test_world_inventory.py
    if($LASTEXITCODE -ne 0){throw 'Original world inventory gates failed'}
    & python -m unittest discover -s tests -p test_record_app_boundary.py
    if($LASTEXITCODE -ne 0){throw 'Strict App recording save-boundary gates failed'}
    & python -m unittest discover -s tests -p test_world_growth_export.py
    if($LASTEXITCODE -ne 0){throw 'Original owner growth gates failed'}
    & python -m unittest discover -s tests -p test_world_west_services_export.py
    if($LASTEXITCODE -ne 0){throw 'Shared village services and West palace content gates failed'}
    & python -m unittest discover -s tests -p test_world_north_palace_export.py
    if($LASTEXITCODE -ne 0){throw 'North palace guarded key and original encounter gates failed'}
    & python -m unittest discover -s tests -p test_world_cave85_export.py
    if($LASTEXITCODE -ne 0){throw 'World cave85 content tests failed'}
    & python -m unittest discover -s tests -p test_world_scene_mechanism.py
    if ($LASTEXITCODE -ne 0) { throw 'World dynamic scene export tests failed' }
    & python -m unittest discover -s tests -p test_world_xiaolongnv_resources.py
    if($LASTEXITCODE -ne 0){throw 'Joined character original resource gates failed'}
    & python -m unittest discover -s tests -p test_world_east_palace_resources.py
    if($LASTEXITCODE -ne 0){throw 'East palace original resource and transition gates failed'}
    & python -m unittest discover -s tests -p test_world_east_export.py
    if($LASTEXITCODE -ne 0){throw 'East content export and critical state gates failed'}
    & python -m unittest discover -s tests -p test_world_hell_encounters_export.py
    if($LASTEXITCODE -ne 0){throw 'Hell encounter and status gates failed'}
    & python -m unittest discover -s tests -p test_world_village2_export.py
    if($LASTEXITCODE -ne 0){throw 'Village2 original services and definitions gates failed'}
    & python -m unittest discover -s tests -p test_world_village3_export.py
    if($LASTEXITCODE -ne 0){throw 'Village3 pinned checkpoint, service, sprite and directed-return gates failed'}
    & python -m unittest discover -s tests -p test_world_forest101_export.py
    if ($LASTEXITCODE -ne 0) { throw 'Forest base regression failed' }
    & python -m unittest discover -s tests -p test_world_forest_direction_export.py
    if($LASTEXITCODE -ne 0){throw 'Forest101 original movement, complete region and restore gates failed'}
    & python -m unittest discover -s tests -p test_world_continent_barrier_export.py
    if ($LASTEXITCODE -ne 0) { throw 'Original world actor conditions gates failed' }
    & python -m unittest discover -s tests -p test_world_continent_bridge_export.py
    if ($LASTEXITCODE -ne 0) { throw 'Original continent bridges and full-zone gates failed' }
    & python -m unittest discover -s tests -p test_world_clinic_export.py
    if ($LASTEXITCODE -ne 0) { throw 'Scoped medical-room export gates failed' }
    & python -m unittest discover -s tests -p test_world_first_hall_export.py
    if($LASTEXITCODE -ne 0){throw 'Original first hall content and state gates failed'}
    & python -m unittest discover -s tests -p test_world_behavior1.py
    if($LASTEXITCODE -ne 0){throw 'Original behavior1 special attack evidence gates failed'}
    & python -m unittest discover -s tests -p test_world_hall_batch_script.py
    if($LASTEXITCODE -ne 0){throw 'Original per-hall finalization and gate filter evidence gates failed'}
    & python -m unittest discover -s tests -p test_world_hall_batch_terrain.py
    if($LASTEXITCODE -ne 0){throw 'Original hall terrain and actual encounter gates failed'}
    & python -m unittest discover -s tests -p test_world_hall_batch_npc.py
    if($LASTEXITCODE -ne 0){throw 'Original hall NPC identity and visible pose gates failed'}
    & python -m unittest discover -s tests -p test_world_chest_grants.py
    if($LASTEXITCODE -ne 0){throw 'Original chest reward and inventory boundary evidence gates failed'}
    & python -m unittest discover -s tests -p test_world_ice_identities.py
    if($LASTEXITCODE -ne 0){throw 'Original all-identity ice damage evidence gates failed'}
    & python -m unittest discover -s tests -p test_world_single_special.py
    if($LASTEXITCODE -ne 0){throw 'Original single-target special attack evidence gates failed'}
    & python -m unittest discover -s tests -p test_world_status16.py
    if($LASTEXITCODE -ne 0){throw 'Original status10 hit priority and defeat evidence gates failed'}
    & python -m unittest discover -s tests -p test_world_second_hall_export.py
    if($LASTEXITCODE -ne 0){throw 'Original second hall content and independent state gates failed'}
    & python -m unittest discover -s tests -p test_world_hall_batch_export.py
    if($LASTEXITCODE -ne 0){throw 'Original Hell batch content, chest and independent state gates failed'}
    & python -m unittest discover -s tests -p test_world_seventh_hall_export.py
    if($LASTEXITCODE -ne 0){throw 'Original seventh hall and field protection content gates failed'}
    & python -m unittest discover -s tests -p test_world_seventh_side_export.py
    if($LASTEXITCODE -ne 0){throw 'Original side-room geometry, returns, groups and visible NPC gates failed'}
    & python -m unittest discover -s tests -p test_world_final_hall_export.py
    if($LASTEXITCODE -ne 0){throw 'Original final hall and durable rebirth content gates failed'}
    & python -m unittest discover -s tests -p test_world_evidence_checkout.py
    if($LASTEXITCODE -ne 0){throw 'Strict original CPU table byte hashes failed'}
    Push-Location (Join-Path $root 'android')
    try {
        $runtime=@();if($RuntimeTests){$runtime=@('-PfengshenInstrumentRelease=true',':app:assembleReleaseAndroidTest')}
        & ./gradlew.bat --no-daemon --console=plain "-PfengshenVersionCode=$VersionCode" "-PfengshenVersionName=$VersionName" :app:testReleaseUnitTest :app:assembleRelease @runtime
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

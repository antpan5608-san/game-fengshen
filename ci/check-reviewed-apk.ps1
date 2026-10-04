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
if(($runtime.completedStages -join ',') -ne 'base,world,continuation'){throw 'All same-candidate normal runtime stages must pass'}
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

if($runtime.worldWestPalace -ne 'PASS' -or $runtime.worldSharedVillageServices -ne 'PASS' -or $runtime.worldTerrainRestore -ne 'PASS'){throw 'World palace/services/terrain restart gates must verify this exact APK'}

if($runtime.worldNorthPalace -ne 'PASS' -or $runtime.worldPearlUseAndColdRestart -ne 'PASS'){throw 'North palace/independent pearl/normal crossing/cold restart gates must verify this exact APK'}
if($runtime.worldCave85Normal -ne 'PASS' -or $runtime.worldCave85OnceAndColdRestart -ne 'PASS'){throw 'Cave85 normal story/once-only rewards/cold restart gates must verify this exact APK'}
if($runtime.worldEastPalaceNormal -ne 'PASS' -or $runtime.worldEastPartyAndColdRestart -ne 'PASS'){throw 'East palace mechanism/Boss/followup/party/normal battle/cold restart gates must verify this exact APK'}
if($runtime.worldHellVillageNormal -ne 'PASS' -or $runtime.worldHellVillageColdRestart -ne 'PASS' -or $runtime.worldWholly08Controller -ne 'PASS'){throw 'Hell partitions/shared village services/08 command progression/cold restart gates must verify this exact APK'}
if($runtime.worldFirstHallNormal -ne 'PASS' -or $runtime.worldFirstHallColdRestart -ne 'PASS'){throw 'First hall normal Qin battle/flags/collision gate/cold restart must verify this exact APK'}
if($runtime.worldSecondHallNormal -ne 'PASS' -or $runtime.worldSecondHallColdRestart -ne 'PASS'){throw 'Second hall normal Chu battle/independent flags/open gate/cold restart must verify this exact APK'}

if($runtime.worldHallBatchNormal -ne 'PASS' -or $runtime.worldHallBatchColdRestart -ne 'PASS'){throw 'Hell hall batch actual normal routes/chests/battles/independent flags/cold restart must verify this exact APK'}
if($runtime.worldFinalHallsNormal -ne 'PASS' -or $runtime.worldRebirthDialogueAndColdRestart -ne 'PASS'){throw 'Final halls, protection, side rooms, actual rebirth dialogue and cold continuation must verify this exact APK'}
if($runtime.worldVillageThreeServicesAndColdRestart -ne 'PASS'){throw 'Village3 normal trades, equipment, lodging, original dialogue and cold reentry must verify this exact APK'}
if($runtime.worldContinentBridgeAndZone16Normal -ne 'PASS' -or $runtime.worldContinentBridgeColdRestart -ne 'PASS'){throw 'Original bridges, complete zone16 encounters and actual cold return must verify this exact APK'}
if($runtime.worldForest101Normal -ne 'PASS' -or $runtime.worldForest101ColdRestart -ne 'PASS'){throw 'Original forest101 route, full zone17 and actual cold return must verify this exact APK'}
if($runtime.worldMedicalControlledCommands -ne 'PASS' -or $runtime.worldMedicalNormalEntryAndColdRestart -ne 'PASS'){throw 'Medical real entry/cold restart and separately controlled commands must verify this exact APK'}

if($runtime.worldTree107Normal -ne 'PASS' -or $runtime.worldTree107ColdRestart -ne 'PASS'){throw 'Actual tree actor contact, four floors, original chests, Yang talk and cold return must verify this exact APK'}

if($runtime.worldRoom171Normal -ne 'PASS' -or $runtime.worldRoom171GiftColdRestart -ne 'PASS'){throw 'Original room171 route, teacher gift before dialogue, repeat and cold return must verify this exact APK'}

if($runtime.worldYangJoinNormal -ne 'PASS' -or $runtime.worldYangThreePartyAndColdRestart -ne 'PASS'){throw 'Actual teacher signal, Yang use/dialogues, original third-actor battle and cold restart must verify this exact APK'}

if($runtime.worldVillageFourServicesTalkNormal -ne 'PASS' -or $runtime.worldVillageFourColdRestart -ne 'PASS'){throw 'Actual village4 bridges, caller services, original NPC text/conditions and cold return must verify this exact APK'}
if($runtime.worldVillageFiveServicesTalkNormal -ne 'PASS' -or $runtime.worldVillageFiveColdRestart -ne 'PASS'){throw 'Actual village5 caller stocks, services, NPC dialogue and original return must verify this exact APK'}
if($runtime.worldFixedFerryIslandNormal -ne 'PASS' -or $runtime.worldFixedFerryColdRestartAndReverse -ne 'PASS'){throw 'Actual fixed boat, complete island encounters, cold save and independent reverse must verify this exact APK'}

if($runtime.worldIslandOriginalLayersAndFourVillainsNormal -ne 'PASS' -or $runtime.worldIslandOnceChestsAndColdRestart -ne 'PASS'){throw 'Actual island layers, original composite fight/flags/chests and cold return must verify this exact APK'}

if($runtime.worldCave87FlowerNormal -ne 'PASS' -or $runtime.worldCave87DepartureAndColdRestart -ne 'PASS'){throw 'Actual cave87 route/flower Boss/seven dialogues/away actor and cold return must verify this exact APK'}

if($runtime.worldVillageSixServicesTalkNormal -ne 'PASS' -or $runtime.worldVillageSixColdRestart -ne 'PASS'){throw 'Actual village6 normal route, caller prices/services/dialogues/hidden medicine and cold return must verify this exact APK'}
if($runtime.worldNightEightGiftAndCaveNormal -ne 'PASS' -or $runtime.worldNightEightColdRestart -ne 'PASS'){throw 'Actual teacher164 gift, original dark cave, reusable light, rope chest and external cold restart must verify this exact APK'}

if($runtime.worldQueenRouteAndBindingNormal -ne 'PASS' -or $runtime.worldQueenHuangOnceAndColdRestart -ne 'PASS'){throw 'Actual original Queen route, binding actor order, Huang gift and external cold restart must verify this exact APK'}

param(
    [string]$Base = 'https://fleetpilots.com/fengshen-api/v1',
    [string]$PasswordFile = (Join-Path $PSScriptRoot '../private-inputs/cloud-account-password.txt')
)
$ErrorActionPreference = 'Stop'
$password = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $PasswordFile).Path).Trim()
$loginBody = @{ username='antpan'; password=$password } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Uri "$Base/session" -Method Post -ContentType 'application/json' -Body $loginBody
if (-not $login.token -or $login.username -ne 'antpan') { throw 'Login contract failed' }
$headers = @{ Authorization="Bearer $($login.token)" }
$before = Invoke-WebRequest -Uri "$Base/save/main" -Headers $headers -SkipHttpErrorCheck
if ($before.StatusCode -notin @(200,404)) { throw "Unexpected initial save status $($before.StatusCode)" }
if ($before.StatusCode -eq 200) {
    $remote = $before.Content | ConvertFrom-Json
    Write-Host "Existing personal save retained: revision=$($remote.revision)"
} else {
    $snapshot = [ordered]@{
        saveSchemaVersion=1;contentVersion='opening-to-world-b1';mapId=114;x=136;y=344;direction='DOWN'
        characters=@(@{id='nezha';level=1;experience=0;hp=20;maxHp=20;mp=0;strength=8;stamina=4;agility=2;spirit=4})
        inventory=@{};flags=@{}
    }
    $put = @{baseRevision=0;snapshot=$snapshot} | ConvertTo-Json -Depth 8 -Compress
    $written = Invoke-RestMethod -Uri "$Base/save/main" -Method Put -Headers $headers -ContentType 'application/json' -Body $put
    if ($written.revision -ne 1 -or $written.snapshot.characters[0].level -ne 1) { throw 'Initial PostgreSQL write failed' }
    $conflict = Invoke-WebRequest -Uri "$Base/save/main" -Method Put -Headers $headers -ContentType 'application/json' -Body $put -SkipHttpErrorCheck
    if ($conflict.StatusCode -ne 409) { throw "Expected stale-write 409, got $($conflict.StatusCode)" }
    Write-Host 'PostgreSQL write and revision conflict: PASS'
}
# Simulate loss of all local gameplay state: only credentials remain, then fetch a fresh snapshot.
Remove-Variable snapshot,put,written -ErrorAction SilentlyContinue
$restored = Invoke-RestMethod -Uri "$Base/save/main" -Headers $headers
if ($restored.snapshot.mapId -ne 114 -or $restored.snapshot.characters[0].id -ne 'nezha' -or $restored.sha256.Length -ne 64) {
    throw 'Cloud recovery did not reconstruct the saved player state'
}
Write-Host "Cloud recovery after simulated local loss: PASS; revision=$($restored.revision)"
Invoke-RestMethod -Uri "$Base/session" -Method Delete -Headers $headers | Out-Null
$after = Invoke-WebRequest -Uri "$Base/save/main" -Headers $headers -SkipHttpErrorCheck
if ($after.StatusCode -ne 401) { throw 'Revoked token remained valid' }
Write-Host 'Session revocation: PASS'

$ErrorActionPreference='Stop'
$zip=Join-Path $env:RUNNER_TEMP 'fengshen-ossutil-2.4.0.zip'
Invoke-WebRequest 'https://gosspublic.alicdn.com/ossutil/v2/2.4.0/ossutil-2.4.0-windows-amd64.zip' -OutFile $zip
if((Get-FileHash $zip).Hash.ToLowerInvariant() -ne 'dd68cffb62d88e59ff3d7fde6055ac7b8e2197e2adce7e540bcd6c706a2c4a49'){throw 'ossutil official checksum mismatch'}
$destination=Join-Path $env:RUNNER_TEMP 'fengshen-ossutil'
Expand-Archive -LiteralPath $zip -DestinationPath $destination
$exe=Get-ChildItem $destination -Recurse -Filter ossutil.exe|Select-Object -First 1
if(-not $exe){throw 'ossutil executable missing'}
$exe.DirectoryName|Add-Content $env:GITHUB_PATH

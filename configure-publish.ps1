# Run interactively in PowerShell. Windows DPAPI binds the saved credential
# to this Windows user; neither plaintext password nor OSS keys are stored.
$ErrorActionPreference = 'Stop'
$credential = Get-Credential -UserName 'root' -Message 'Language VM SSH credentials (204.44.123.101:10080); used read-only to load OSS credentials'
if (-not $credential) { throw 'Cancelled; no credentials saved' }
$directory = Join-Path $PSScriptRoot 'private-inputs'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$credential | Export-Clixml -LiteralPath (Join-Path $directory 'oss-ssh.clixml')
Write-Host 'Windows-protected credential saved locally. Run ./publish-apk.ps1 to publish Fengshen only.'

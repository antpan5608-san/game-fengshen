param(
    [Parameter(Position=0)][string]$Command = 'help',
    [Parameter(ValueFromRemainingArguments=$true)][string[]]$CommandArgs
)
$ErrorActionPreference = 'Stop'
$env:PYTHONIOENCODING = 'utf-8'
$phasePython = Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
if ($Command -eq 'setup') {
    if (-not (Test-Path -LiteralPath $phasePython)) {
        & python -m venv (Join-Path $PSScriptRoot '.venv')
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    & $phasePython -m pip install -r (Join-Path $PSScriptRoot 'requirements-forensics-lock.txt')
    exit $LASTEXITCODE
}
if (-not (Test-Path -LiteralPath $phasePython)) {
    throw 'Run ./phase1.ps1 setup first (Python 3.12+ required).'
}
if ($Command -eq 'help') { & $phasePython (Join-Path $PSScriptRoot 'tools/phase1.py') --help }
elseif ($Command -eq 'test') { & $phasePython (Join-Path $PSScriptRoot 'tools/run_phase1_tests.py') }
else { & $phasePython (Join-Path $PSScriptRoot 'tools/phase1.py') $Command @CommandArgs }
exit $LASTEXITCODE

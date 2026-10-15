param(
    [ValidateSet('backend', 'frontend')]
    [string]$Service = 'backend'
)

$ErrorActionPreference = 'Stop'
if ($Service -eq 'backend') {
    & (Join-Path $PSScriptRoot 'backend/start-local.ps1')
} else {
    Push-Location -LiteralPath (Join-Path $PSScriptRoot 'frontend')
    try {
        & npm.cmd run dev
        if ($LASTEXITCODE -ne 0) { throw "npm terminou com codigo $LASTEXITCODE." }
    } finally {
        Pop-Location
    }
}

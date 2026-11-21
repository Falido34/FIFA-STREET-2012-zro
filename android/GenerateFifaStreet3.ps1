[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$GameData, [string]$Generator, [string]$OutputRoot)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$Generator) { $Generator = Join-Path $repo '.android-work/rexglue/out/win-amd64/rexglue.exe' }
if (!$OutputRoot) { $OutputRoot = Join-Path $repo '.android-work/fifastreet3' }
if (!(Test-Path -LiteralPath $Generator -PathType Leaf)) { throw "Pinned host code generator is not built: $Generator" }
$GameData = (Resolve-Path -LiteralPath $GameData).Path
$entry = Join-Path $GameData 'default.xex'
if (!(Test-Path -LiteralPath $entry -PathType Leaf)) { throw "FIFA Street 3 entrypoint not found: $entry" }
$stream = [IO.File]::OpenRead($entry)
try {
    $magic = [byte[]]::new(4)
    if ($stream.Read($magic, 0, 4) -ne 4 -or [Text.Encoding]::ASCII.GetString($magic) -ne 'XEX2') {
        throw "Invalid XEX2 header: $entry"
    }
} finally { $stream.Dispose() }

# This output directory is intentionally separate from the FIFA Street 2012
# generated sources. Only the confirmed default.xex is included; missing
# modules are not guessed or borrowed from another title.
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$data = $GameData.Replace('\', '/').Replace('"', '\"')
$manifest = [IO.File]::ReadAllText((Join-Path $repo 'game-template/fifastreet3_manifest.toml'))
$manifest = $manifest.Replace('GAME_ROOT_PLACEHOLDER', $data)
$manifest = $manifest.Replace("`r`n", "`n").TrimEnd("`r", "`n") + "`n"
$manifestPath = Join-Path $OutputRoot 'fifastreet3_manifest.toml'
[IO.File]::WriteAllText($manifestPath, $manifest, [Text.UTF8Encoding]::new($false))
Push-Location $OutputRoot
try {
    & $Generator --log-file (Join-Path $OutputRoot 'codegen.log') codegen $manifestPath
    if ($LASTEXITCODE -ne 0) { throw "FIFA Street 3 code generation failed: exit $LASTEXITCODE" }
} finally { Pop-Location }
$generated = Join-Path $OutputRoot 'generated'
if (Test-Path -LiteralPath $generated) {
    & python (Join-Path $PSScriptRoot 'CompactRegistration.py') $generated
    if ($LASTEXITCODE -ne 0) { throw 'FIFA Street 3 generated registration validation failed.' }
}
Write-Output "FIFA Street 3 isolated codegen output: $generated"
Write-Warning "This is an initial entrypoint-only generation. It does not establish runtime compatibility or a playable Android build."

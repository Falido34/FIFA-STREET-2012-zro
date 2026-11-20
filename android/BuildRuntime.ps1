[CmdletBinding()]
param(
    [string]$ToolsRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) '.tools'),
    [string]$SdkRoot,
    [string]$NdkRoot,
    [string]$CMake,
    [string]$Ninja,
    [int]$Jobs = 6
)
$ErrorActionPreference = 'Stop'
if ($Jobs -lt 1) { throw 'Jobs must be positive.' }
if (!$SdkRoot) { $SdkRoot = Join-Path $ToolsRoot 'android-sdk' }
if (!$NdkRoot) { $NdkRoot = Join-Path $SdkRoot 'ndk/28.2.13676358' }
if (!$CMake) { $CMake = Join-Path $ToolsRoot 'w64devkit/bin/cmake.exe' }
if (!$Ninja) { $Ninja = Join-Path $ToolsRoot 'w64devkit/bin/ninja.exe' }
$repo = Split-Path $PSScriptRoot -Parent
$work = Join-Path $repo '.android-work'
$source = Join-Path $work 'rexglue'
$adrenoSource = Join-Path $work 'adrenotools'
$adrenoPin = '8fae8ce254dfc1344527e05301e43f37dea2df80'
$adrenoPatch = Join-Path $PSScriptRoot 'rexglue-adrenotools.patch'
$build = Join-Path $work 'build-rex-arm64'
$pin = 'c94f5ebdcb3c9d1a460ca48e04f9758448f8d518'
$gamePatch = Join-Path $repo 'patches/rexglue-local-changes.patch'
$androidPatch = Join-Path $PSScriptRoot 'rexglue-android.patch'
$performancePatch = Join-Path $PSScriptRoot 'rexglue-performance.patch'
$mobilePatch = Join-Path $PSScriptRoot 'rexglue-mobile.patch'
$shaderPatch = Join-Path $PSScriptRoot 'rexglue-shader-opt.patch'
foreach ($path in @($gamePatch, $androidPatch, $performancePatch, $mobilePatch, $shaderPatch, $adrenoPatch)) {
    if (!(Test-Path -LiteralPath $path)) { throw "Missing prerequisite: $path. No software was installed." }
}
function Invoke-Checked([string]$Tool, [string[]]$Arguments) {
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}
New-Item -ItemType Directory -Path $work -Force | Out-Null
if (!(Test-Path -LiteralPath "$adrenoSource/.git")) {
    if (Test-Path -LiteralPath $adrenoSource) { throw 'Adreno Tools source destination exists and is not a Git checkout.' }
    Invoke-Checked git @('clone', '--filter=blob:none', '--no-checkout', 'https://github.com/bylaws/libadrenotools.git', $adrenoSource)
    Invoke-Checked git @('-C', $adrenoSource, 'config', 'core.autocrlf', 'false')
    Invoke-Checked git @('-C', $adrenoSource, '-c', 'core.longpaths=true', 'checkout', '--detach', $adrenoPin)
    Invoke-Checked git @('-C', $adrenoSource, '-c', 'core.longpaths=true', 'submodule', 'update', '--init', '--depth', '1', '--jobs', "$Jobs")
}
$adrenoHead = (& git -C $adrenoSource rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $adrenoHead -ne $adrenoPin) { throw "Adreno Tools must be at pinned commit $adrenoPin." }
if (!(Test-Path -LiteralPath "$source/.git")) {
    if (Test-Path -LiteralPath $source) { throw 'Source destination exists and is not a Git checkout.' }
    Invoke-Checked git @('clone', '--filter=blob:none', '--no-checkout', 'https://github.com/rexglue/rexglue-sdk.git', $source)
    # Keep upstream LF blobs unchanged so pinned patch contexts remain valid on Windows.
    Invoke-Checked git @('-C', $source, 'config', 'core.autocrlf', 'false')
    Invoke-Checked git @('-C', $source, '-c', 'core.longpaths=true', 'checkout', '--detach', $pin)
}
$head = (& git -C $source rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $head -ne $pin) { throw "ReXGlue must be at pinned commit $pin. Existing checkout was preserved." }
& git -C $source -c core.whitespace=cr-at-eol apply --ignore-space-change --ignore-whitespace --reverse --check $androidPatch 2>$null
if ($LASTEXITCODE -ne 0) {
    Invoke-Checked git @('-C', $source, '-c', 'core.whitespace=cr-at-eol', 'apply', '--ignore-space-change', '--ignore-whitespace', '--check', $gamePatch)
    Invoke-Checked git @('-C', $source, '-c', 'core.whitespace=cr-at-eol', 'apply', '--ignore-space-change', '--ignore-whitespace', '--whitespace=nowarn', $gamePatch)
    Invoke-Checked git @('-C', $source, '-c', 'core.whitespace=cr-at-eol', 'apply', '--ignore-space-change', '--ignore-whitespace', '--check', $androidPatch)
    Invoke-Checked git @('-C', $source, '-c', 'core.whitespace=cr-at-eol', 'apply', '--ignore-space-change', '--ignore-whitespace', $androidPatch)
}
& git -C $source -c core.whitespace=cr-at-eol apply --ignore-space-change --ignore-whitespace --reverse --check $adrenoPatch 2>$null
if ($LASTEXITCODE -ne 0) {
    Invoke-Checked git @('-C', $source, '-c', 'core.whitespace=cr-at-eol', 'apply', '--ignore-space-change', '--ignore-whitespace', '--check', $adrenoPatch)
    Invoke-Checked git @('-C', $source, '-c', 'core.whitespace=cr-at-eol', 'apply', '--ignore-space-change', '--ignore-whitespace', '--whitespace=nowarn', $adrenoPatch)
}
& "$PSScriptRoot/ApplyPerformancePatches.ps1" -Source $source
$modules = @('cli11', 'libmspack', 'FFmpeg', 'tomlplusplus', 'simde', 'xxHash', 'spdlog', 'fmt', 'utfcpp', 'imgui', 'sdl3', 'vulkan-headers', 'vulkan-memory-allocator', 'spirv-headers', 'spirv-tools', 'glslang', 'o1heap', 'inja')
Invoke-Checked git (@('-C', $source, '-c', 'core.longpaths=true', 'submodule', 'update', '--init', '--depth', '1', '--jobs', "$Jobs", '--') + @($modules | ForEach-Object { "thirdparty/$_" }))
# Android ReXGlue is compiled by BuildGame.ps1 in the same CMake tree as the
# game modules. Building it here as well created a second full native build.
Write-Output "ReXGlue source and Android patches prepared; native targets will be built once with the game."

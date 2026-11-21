[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$GameData,
    [string]$ToolsRoot=(Join-Path (Split-Path $PSScriptRoot -Parent) '.tools'),
    [string]$Generator,
    [int]$Jobs=8
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$source=Join-Path $repo '.android-work/rexglue'
$output=Join-Path $repo '.android-work/fifastreet3'
$build=Join-Path $repo '.android-work/build-fifastreet3-arm64'
$ndk=Join-Path $ToolsRoot 'android-sdk/ndk/28.2.13676358'
$cmake=Join-Path $ToolsRoot 'w64devkit/bin/cmake.exe'
$ninja=Join-Path $ToolsRoot 'w64devkit/bin/ninja.exe'
foreach($tool in @($cmake,$ninja,(Join-Path $ndk 'build/cmake/android.toolchain.cmake'))) {
    if(!(Test-Path -LiteralPath $tool)) { throw "Required Android build tool not found: $tool" }
}
if(!(Test-Path -LiteralPath $source)) { throw "ReXGlue SDK source is missing: $source" }
if(!$Generator) { $Generator=Join-Path $source 'out/win-amd64/rexglue.exe' }
if(!(Test-Path -LiteralPath $Generator -PathType Leaf)) {
    & "$PSScriptRoot/BuildHostGenerator.ps1" -ToolsRoot $ToolsRoot -Jobs $Jobs
    if($LASTEXITCODE -ne 0) { throw 'ReXGlue host generator build failed.' }
}
if(!(Test-Path -LiteralPath $Generator -PathType Leaf)) {
    throw "ReXGlue host generator was not produced: $Generator"
}
# Stage the independent project before codegen. The generator writes a
# game-specific manifest into $output; staging afterwards would overwrite it
# with the template placeholder and break the CMake-triggered codegen step.
$template=Join-Path $repo 'game-template/fifastreet3'
New-Item -ItemType Directory -Force -Path $output | Out-Null
Copy-Item -Path (Join-Path $template '*') -Destination $output -Recurse -Force

& "$PSScriptRoot/GenerateFifaStreet3.ps1" -GameData $GameData -Generator $Generator -OutputRoot $output
if($LASTEXITCODE -ne 0) { throw 'FIFA Street 3 code generation failed.' }
if(!(Test-Path -LiteralPath (Join-Path $output 'generated/default/sources.cmake'))) {
    throw 'Validated ReXGlue entrypoint sources are missing after staging.'
}
& $cmake -S $output -B $build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release "-DREXSDK_DIR=$source" "-DREXGLUE_HOST_CODEGEN_EXECUTABLE=$Generator" "-DREXGLUE_OUTPUT_DIRECTORY=$build/runtime" "-DREX_ADRENOTOOLS_SOURCE_DIR=$(Join-Path $repo '.android-work/adrenotools')" -DREXGLUE_USE_D3D12=OFF -DREXGLUE_USE_VULKAN=ON -DREXGLUE_ENABLE_TRACY=OFF -DREXGLUE_ENABLE_PERF_COUNTERS=OFF -DREXGLUE_BUILD_TESTS=OFF
if($LASTEXITCODE -ne 0) { throw 'FIFA Street 3 Android CMake configuration failed.' }
& $cmake --build $build --target fifastreet3 --parallel $Jobs
if($LASTEXITCODE -ne 0) { throw 'FIFA Street 3 ARM64 native compilation failed.' }
$library=@(Get-ChildItem -LiteralPath $build -Filter 'libfifastreet3.so' -File -Recurse | Select-Object -First 1)
if(!$library) { throw 'CMake completed but libfifastreet3.so was not found.' }
Write-Output "FIFA Street 3 ARM64 native library: $($library[0].FullName)"
Write-Warning 'This builds an isolated native library only; Android APK packaging and runtime compatibility are not yet enabled.'

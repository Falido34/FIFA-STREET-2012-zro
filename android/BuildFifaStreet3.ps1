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

# Collect the runtime shared libraries into a deterministic staging directory.
# The app cannot load the game entrypoint by itself: ReXGlue runtime and Xenos
# Vulkan backend must be shipped beside it in the APK's arm64-v8a directory.
$runtimeStage=Join-Path $repo '.android-work/fifastreet3-apk-libs'
if(Test-Path -LiteralPath $runtimeStage) { Remove-Item -LiteralPath $runtimeStage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $runtimeStage | Out-Null
Copy-Item -LiteralPath $library[0].FullName -Destination (Join-Path $runtimeStage 'libfifastreet3.so')
$runtimeRoot=Join-Path $build 'runtime'
if(!(Test-Path -LiteralPath $runtimeRoot)) { throw "ReXGlue runtime output directory is missing: $runtimeRoot" }
$runtimeLibraries=@(Get-ChildItem -LiteralPath $runtimeRoot -Filter '*.so' -File -Recurse)
foreach($name in @('librexruntime.so','librexgpu-xenos.so')) {
    $match=@($runtimeLibraries | Where-Object Name -eq $name | Select-Object -First 1)
    if(!$match) { throw "Required FIFA Street 3 runtime dependency was not produced: $name" }
    Copy-Item -LiteralPath $match[0].FullName -Destination (Join-Path $runtimeStage $name)
}
# Build the SDL3 Android JNI bridge from the SDK's pinned vendored source.
# ReXGlue links against SDL headers, but does not build/package SDL itself.
$sdlSource=Join-Path $source 'thirdparty/sdl3'
$sdlBuild=Join-Path $repo '.android-work/build-sdl3-arm64'
if(!(Test-Path -LiteralPath (Join-Path $sdlSource 'CMakeLists.txt'))) {
    throw "Pinned SDL3 source tree is missing: $sdlSource"
}
& $cmake -S $sdlSource -B $sdlBuild -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release -DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST_LIBRARY=OFF -DSDL_TESTS=OFF
if($LASTEXITCODE -ne 0) { throw 'SDL3 Android CMake configuration failed.' }
& $cmake --build $sdlBuild --target SDL3-shared --parallel $Jobs
if($LASTEXITCODE -ne 0) { throw 'SDL3 Android shared library compilation failed.' }
$sdl=@(Get-ChildItem -LiteralPath $sdlBuild -Filter 'libSDL3.so' -File -Recurse | Select-Object -First 1)
if(!$sdl) { throw 'SDL3 Android build completed but libSDL3.so was not found.' }
Copy-Item -LiteralPath $sdl[0].FullName -Destination (Join-Path $runtimeStage 'libSDL3.so')

Write-Output "FIFA Street 3 ARM64 entrypoint: $($library[0].FullName)"
Write-Output "FIFA Street 3 APK native dependencies staged: $runtimeStage"
Get-ChildItem -LiteralPath $runtimeStage -Filter '*.so' -File | ForEach-Object { Write-Output ("APK_NATIVE_LIB=" + $_.FullName) }
Write-Warning 'The native libraries are staged; Android activity wiring, game-folder import, and APK runtime validation remain separate steps.'

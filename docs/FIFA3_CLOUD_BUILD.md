# FIFA Street 3 cloud build from Android

The workflow downloads the ZIP from the existing FIFA3 release:
https://github.com/Falido34/FIFA-STREET-2012-files./releases/download/FIFA3/4541083B.zip
It does not need a token to download this public release.

Generated game code and native binaries should stay in a separate **private** repository.

## One-time setup on GitHub mobile/web

1. Create a repository named `FIFA-STREET-2012-output` under the same GitHub account and set visibility to **Private**.
2. Create a fine-grained personal access token limited to that private output repository. Grant **Contents: Read and write** only. The workflow uses it only to publish the ARM64 library and build log privately.
3. In this public project repository, open Settings → Secrets and variables → Actions → New repository secret. Name it `FIFA3_OUTPUT_TOKEN` and paste the token value.

## Run

Open Actions → **Build FIFA Street 3 ARM64 (release input)** → Run workflow.

The workflow downloads and validates `default.xex`, builds the pinned ReXGlue host generator, runs the isolated FIFA Street 3 codegen and ARM64 CMake build, then publishes the native library and codegen log as a **prerelease in the private output repository**.

## Important limits

A successful workflow only proves that the isolated native library compiled. It does not prove that the game is playable or that the library can yet be packaged into the main Android APK. The ZIP is whatever is currently in the linked release; the workflow does not invent or borrow missing modules from FIFA Street 2012.

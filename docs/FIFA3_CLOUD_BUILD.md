# FIFA Street 3 cloud build from Android

The project source repository is public. Game files and generated binaries must stay in a separate **private** repository.

## One-time setup on GitHub mobile/web

1. Create a repository named `FIFA-STREET-2012-input` under the same GitHub account and set visibility to **Private**.
2. Upload the user's own `4541083B.zip` at the repository root. Do not upload it to this public project repository.
3. Create a fine-grained personal access token limited to the private `FIFA-STREET-2012-input` repository. Grant **Contents: Read and write** only. The write permission is used to publish the resulting ARM64 library privately.
4. In this public project repository, open Settings → Secrets and variables → Actions → New repository secret. Name it `FIFA3_INPUT_TOKEN` and paste the token value.

## Run

Open Actions → **Build FIFA Street 3 ARM64 (private input)** → Run workflow.

The workflow checks that the input repository is private, downloads and validates `default.xex`, builds the pinned ReXGlue host generator, runs the isolated FIFA Street 3 codegen and ARM64 CMake build, then publishes the native library and codegen log as a **prerelease in the private input repository**. It does not upload game files, generated source, or the native library as public artifacts.

## Important limits

A successful workflow only proves that the isolated native library compiled. It does not prove that the game is playable or that the library can yet be packaged into the main Android APK. The current ZIP contains only the files actually supplied; the workflow does not invent or borrow missing modules from FIFA Street 2012.

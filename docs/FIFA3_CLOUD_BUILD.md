# FIFA Street 3 cloud build from Android

The workflow downloads the ZIP from the existing FIFA3 release:
https://github.com/Falido34/FIFA-STREET-2012-files./releases/download/FIFA3/4541083B.zip
The source repository is private, so the workflow authenticates the download with a dedicated read-only token.

Generated game code and native binaries should stay in a separate **private** repository.

## One-time setup on GitHub mobile/web

1. Create a repository named `FIFA-STREET-2012-output` under the same GitHub account and set visibility to **Private**.
2. Create a fine-grained personal access token with access only to `FIFA-STREET-2012-files.`. Grant **Contents: Read-only**. Add it in this project repository as the Actions secret `FIFA3_INPUT_TOKEN`.
3. Create a second fine-grained token with access only to `FIFA-STREET-2012-output`. Grant **Contents: Read and write**. Add it as the Actions secret `FIFA3_OUTPUT_TOKEN`.
4. Both secrets belong in the source project repository: Settings → Secrets and variables → Actions → New repository secret. Never paste either token into chat.

## Run

Open Actions → **Build FIFA Street 3 ARM64 (release input)** → Run workflow.

The workflow downloads and validates `default.xex`, builds the pinned ReXGlue host generator, runs the isolated FIFA Street 3 codegen and ARM64 CMake build, then publishes the native library and codegen log as a **prerelease in the private output repository**.

## Important limits

A successful workflow only proves that the isolated native library compiled. It does not prove that the game is playable or that the library can yet be packaged into the main Android APK. The ZIP is whatever is currently in the linked release; the workflow does not invent or borrow missing modules from FIFA Street 2012.

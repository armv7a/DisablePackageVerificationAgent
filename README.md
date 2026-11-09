# Disable Package Verification Agent

Extracted from [CorePatch](https://github.com/LSPosed/CorePatch). Disables installation verification agents such as Play Protect. Does **not** bypass APK signatures.

## Source

Extracted from CorePatch [ddf87bb5](https://github.com/LSPosed/CorePatch/commit/ddf87bb5fb2bb2eb962992103abf0b225952d738), modified 2026-11-09 to keep only these hooks:

- [PackageManagerServiceHook.kt](https://github.com/LSPosed/CorePatch/blob/ddf87bb5fb2bb2eb962992103abf0b225952d738/app/src/main/java/org/lsposed/corepatch/hook/PackageManagerServiceHook.kt#L44-L50) (Android 9–12L)
- [VerificationParamsHook.kt](https://github.com/LSPosed/CorePatch/blob/ddf87bb5fb2bb2eb962992103abf0b225952d738/app/src/main/java/org/lsposed/corepatch/hook/VerificationParamsHook.kt) (Android 13)
- [VerifyingSessionHook.kt](https://github.com/LSPosed/CorePatch/blob/ddf87bb5fb2bb2eb962992103abf0b225952d738/app/src/main/java/org/lsposed/corepatch/hook/VerifyingSessionHook.kt) (Android 14+)

## License

[GPL-2.0-only](<LICENSE>), as required by upstream. The Gradle wrapper is [Apache-2.0](<LICENSES/Apache-2.0.txt>).

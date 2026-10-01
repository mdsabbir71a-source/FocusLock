# FocusLock recovery — approved v1.1.25

Current package: `io.focuslock.app`; version 1.1.25; version code 225.
The user approved this build on 2026-10-01. It has no account/sign-in requirement.

Use the latest source in https://github.com/mdsabbir71a-source/FocusLock, or
restore `FocusLock-v1.1.25-Complete-Backup.zip` from the FocusLock Project.
Read `RELEASE_MANIFEST.json` in the backup for exact source hashes and contents.
Do not use an earlier v1.0.89 archive as the current source.

## Build

Use JDK 17, Gradle 8.10.2, Android SDK platform 36 and build-tools 36.0.0.
Run `gradle assembleDebug` for local testing. For production run
`gradle assembleRelease bundleRelease` with the existing production signing
secrets set as specified in `app/build.gradle`. Never generate a replacement
production key to update an existing Play app.

## Preserved resources

All Android Java, assets, drawables, layouts, native onboarding/permissions
artwork, eight current lock designs, build files, backend migrations/functions,
original HTML design references, and GitHub workflows are in this repository.
The complete backup additionally preserves the approved test APK, Git history,
available website/admin source snapshots, and checksums.

Signing secrets stay outside source archives. The production signing key is
restored only from the existing GitHub Actions secrets. The no-login build does
not sync signed-in accounts; the existing admin/backend remain separate.

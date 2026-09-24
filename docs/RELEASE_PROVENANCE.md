# FocusLock release provenance

## Current maintained release line

- **Version:** 1.0.89 (version code 189)
- **Source branch:** `rebuild/v1.0.89`
- **Behavioral reference:** `FocusLock-v1.0.88-Faster-Locking-Test.apk`
- **Package:** `com.focuslock.app`

The original editable project that produced the v1.0.88 test APK was not
available in the Project archives or Git history. This source line is therefore
a deliberate, maintainable rebuild using that working APK as the behavioral and
visual reference. It must never be represented as the original v1.0.88 source.

## Required preservation after an approved release

1. Commit the reviewed source to the `rebuild/v1.0.89` branch and merge/push it
   to `main` only after build verification.
2. Create a clean source archive named `FocusLock-v<version>-Source.zip`.
3. Save that archive to the FocusLock Project and retain the exact Git commit
   hash in the release notes.
4. Keep signing keys and passphrases out of Git and all source archives.

This makes the Git repository the live source of truth and the Project archive
the independent recovery copy.

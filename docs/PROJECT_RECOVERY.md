# FocusLock source recovery

## Use this source in a future chat

Ask for **“FocusLock v1.0.89 source from the FocusLock Project”**. The exact
archive is stored in the Project as `FocusLock-v1.0.89-Source.zip`.

Unzip it, open the extracted folder in Android Studio, and continue from the
version shown in `app/build.gradle` (`versionCode 189`, `versionName 1.0.89`).

## What is included

- Android source for package `com.focuslock.app`
- Current eight lock-card assets
- Account and analytics assets
- Build configuration and release documentation

## What is deliberately excluded

- Production signing keys and passphrases
- Built APK/AAB files and transient build output

Never commit or archive those signing secrets alongside the source. A future
release must use the same separate production key and the alias
`focuslock-production` to update installed copies.

## Source-of-truth rule

GitHub repository: `mdsabbir71a-source/FocusLock`.

After every approved code change, commit and push the source, then replace the
Project archive with a freshly verified source ZIP. This gives future chats two
clear recovery locations: GitHub for the active source and this Project archive
for independent recovery.

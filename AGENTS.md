# FocusLock release baseline

The minimum acceptable app baseline is version 1.1.32 (version code 232), including the detailed Firebase analytics and redesigned announcement dialog. The verified working analytics source is commit 350970ea118b24e95b083ba5da70da52b8dbbe7c; the restored baseline is commit a383ea617cc2329f424c25dbe51fba7881b100a9.

When asked for the latest app, inspect current branches, releases, and build history and use the newest verified source that retains these features. Never assume an older main branch or version number is the latest. Confirm the signing identity and build output; increment version code appropriately and verify Play Console availability before claiming a release is deployable. Preserve the app's existing behavior and keep Firebase configuration and signing credentials out of source control. Document any unverified device behavior and publication status.

## Latest tested continuation point (8 November 2026)

Version 1.1.34 (version code 234), commit 4129fe1d95212f237e48895b99470d9cbecf6cfc, includes v1.1.32 analytics and announcements plus compatibility monitor recovery. The user reported the v1.1.34 blocking test worked on their phone. This does not establish reliability on every Android device. The signed test APK is FocusLock-v1.1.34-Blocking-Test.apk, SHA-256 3fab0ec97f416bd1901600e5b578a977ce98999400a4f7e7c26e3d0b4e0d6374. Verify current Play version code and signing identity before release. Do not claim Play publication without confirming it in Play Console. Preserve Firebase and signing secrets.

## Latest tested continuation point (9 November 2026)

Version 1.1.36 (version code 236), source merged in commit 298796dc670eaf2a6768fc5652c9da8ced331c4b, includes the v1.1.34 blocking recovery, v1.1.35 timer and announcement design, and optional announcement title/message fields. The user reported the announcement change works. The public-announcement edge function was deployed with backwards compatibility; do not claim full delivery on all devices. Signed test APK: FocusLock-v1.1.36-Announcement-Test.apk, SHA-256 28b685e268a006d1a7ef3fc4adc3b0bc9695fec8924ed42f066a0c32afb782e9. Signed AAB SHA-256 86e69469c5eb51c4e36c6125b5f0fa91364ef6b80910f1404ac7f2f300ff5cee. Google Play upload and version-code availability have not been verified; check Play Console before claiming publication. Preserve Firebase configuration and signing secrets.

## Repeat-announcement work (9 November 2026)

Version 1.1.37 (code 237), merged in 02b1411b5f1aa9016169e9fa81ae06789c3a9831, adds optional repeat-until-link announcements on top of v1.1.36. Signed test APK SHA-256 dd742ee3ad2a848d1065bfd547ab78ebe4ee168720c134ac22f30dc3f503e4c3. The public-announcement service and database setting are deployed, default off. The redesigned FocusLock Control Center has the switch in Site version 6 (source 92a5b6f197a0105801f1bfa74bdf5d602a997220). Android build and signature verification passed; end-to-end phone verification and Google Play publication remain unconfirmed. Older app versions do not implement repeat behavior.

# FocusLock release baseline

The minimum acceptable app baseline is version 1.1.32 (version code 232), including the detailed Firebase analytics and redesigned announcement dialog. The verified working analytics source is commit 350970ea118b24e95b083ba5da70da52b8dbbe7c; the restored baseline is commit a383ea617cc2329f424c25dbe51fba7881b100a9.

When asked for the latest app, inspect current branches, releases, and build history and use the newest verified source that retains these features. Never assume an older main branch or version number is the latest. Confirm the signing identity and build output; increment version code appropriately and verify Play Console availability before claiming a release is deployable. Preserve the app's existing behavior and keep Firebase configuration and signing credentials out of source control. Document any unverified device behavior and publication status.

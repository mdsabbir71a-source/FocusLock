# FocusLock for Android

FocusLock lets a user choose distracting apps, set a short commitment countdown, and then block those apps for a chosen duration.

## MVP behavior

1. Enable FocusLock under Android **Settings → Accessibility**.
2. Select one or more installed apps.
3. Enter a countdown in minutes and a lock duration in minutes.
4. Tap **Start commitment**.
5. When the countdown finishes, opening a selected app shows a full-screen lock page until the timer expires.

## Run it

Open this folder in a recent Android Studio version, allow Gradle sync to finish, connect an Android 8.0+ phone, and click **Run**.

## Build an APK without Android Studio

Upload the contents of this folder to a GitHub repository. The included GitHub Actions workflow builds automatically. Open the repository's **Actions** tab, select **Build FocusLock APK**, open the latest successful run, and download the **FocusLock-APK** artifact.

## Important production notes

- Accessibility-based blocking is intentionally disclosed during onboarding and no screen content is retrieved.
- `QUERY_ALL_PACKAGES` is restricted by Google Play policy. Before publishing, replace the general app picker with a curated social-app list or submit the required policy declaration.
- A technically determined user can disable Accessibility permission or uninstall the app. Device-owner mode would be needed for a tamper-resistant parental-control edition.
- Battery-optimization behavior differs by manufacturer, so test on Samsung, Xiaomi, Oppo/Realme, and Pixel devices.

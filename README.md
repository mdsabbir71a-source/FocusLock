# FocusLock for Android

FocusLock lets a user choose distracting apps, give each app an allowed amount of actual foreground usage, and then block that app for a chosen duration when its allowance is consumed.

## MVP behavior

1. Allow **Usage Access** and **Display Over Other Apps** from the buttons in FocusLock.
2. Select one or more installed apps.
3. Enter allowed foreground usage in minutes and a lock duration in minutes.
4. Tap **Start commitment**.
5. Only time actually spent inside each selected app counts. When an app consumes its allowance, it is sent to the background and locked for the configured period.

## Run it

Open this folder in a recent Android Studio version, allow Gradle sync to finish, connect an Android 8.0+ phone, and click **Run**.

## Build an APK without Android Studio

Upload the contents of this folder to a GitHub repository. The included GitHub Actions workflow builds automatically. Open the repository's **Actions** tab, select **Build FocusLock APK**, open the latest successful run, and download the **FocusLock-APK** artifact.

## Important production notes

- The private-test build avoids Accessibility permission. It uses event-based Usage Access to identify only newly opened apps, preventing false lock screens on Home or unrelated apps.
- Display Over Other Apps permission allows the foreground monitor to open the dedicated lock activity when a blocked app is launched; it no longer leaves a persistent overlay on screen.
- `QUERY_ALL_PACKAGES` is restricted by Google Play policy. Before publishing, replace the general app picker with a curated social-app list or submit the required policy declaration.
- A technically determined user can disable Accessibility permission or uninstall the app. Device-owner mode would be needed for a tamper-resistant parental-control edition.
- Battery-optimization behavior differs by manufacturer, so test on Samsung, Xiaomi, Oppo/Realme, and Pixel devices.

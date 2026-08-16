# FocusLock for Android

FocusLock lets a user choose distracting apps, give each app an allowed amount of actual foreground usage, and then block that app for a chosen duration when its allowance is consumed.

Version 0.5 introduces a calm, light interface inspired by the supplied FocusLock concept: warmer surfaces, clearer permission cards, a visual app grid, compact boundary controls, and a dedicated breathing-focused pause screen. The underlying per-app timing and blocking behavior is unchanged.

Version 0.6 adds Easy Setup. FocusLock guides the user through Android's special Usage Access and overlay screens, requests ordinary notification permission with a system popup, and offers one-tap Adult Protection through a DNS-only local VPN. Only DNS requests enter the VPN; ordinary app traffic stays on its normal connection. Cloudflare Family (`1.1.1.3`) supplies adult-domain and malware filtering. Because Android permits only one selected VPN, this mode cannot run alongside another VPN; the original Private DNS configuration remains recognized as an alternative.

Version 0.7 adds a first-launch permission walkthrough, a master on/off control, a simpler botanical dashboard, lightweight native animations, and eleven rotating gentle reminders on the blocked-app screen.

Version 0.8 introduces the minimalist botanical pause logo, automatically scrolls to app and timer selection after onboarding, supports minute-and-second limits, and makes the boundary button reactivate after every unsaved app or time change before fading once saved.

Version 0.9 simplifies the dashboard into three guided steps, adds first-boundary coaching, introduces private on-device progress analytics (pauses, protected time, and active-day streak), and upgrades the blocker with eight rotating faceless nature line-art scenes plus floating, breathing, pulse, and staggered entrance animations.

Version 0.10 adds a persistent four-step walkthrough that moves with the user from permissions to app selection, timer setup, and saving. Selecting the first app automatically reveals the timer step, valid timer edits reveal the final save step, and the walkthrough can be replayed from **How it works?** without changing saved settings.

Version 0.11 removes FocusLock's local VPN service and uses Android Private DNS for optional adult-site and malware filtering instead. Android shows one system confirmation because ordinary apps cannot change device-wide DNS secretly. This avoids the VPN icon, VPN tunnel, and conflict with the phone's VPN slot. Progress analytics now live in an animated right-side drawer, and the main screen has a shorter, clearer setup structure.

Version 0.12 replaces the manual Private DNS flow with FocusLock Safe Browser. Adult-domain rules and strict Google, Bing, DuckDuckGo, and Yahoo search parameters apply automatically inside the browser, with no VPN, DNS setup, or special permission. The browser also disables file/content access, third-party cookies, mixed HTTP content, geolocation, popup windows, and WebView debugging. Protection is intentionally limited to FocusLock Safe Browser; other browsers and apps remain unaffected.

Version 0.13 replaces Safe Browser with two full-device protection choices so they can be tested side by side. **Option 1** opens Android Private DNS, copies `family.cloudflare-dns.com`, and uses no VPN indicator after the one-time paste-and-save setup. **Option 2** is a one-tap DNS-only local VPN using Cloudflare Family (`1.1.1.3`); Android displays its required VPN/key indicator. FocusLock guides safe switching so only one option is used at a time.

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
- Adult-domain protection is optional and device-wide: Private DNS avoids the VPN slot but needs one manual paste, while DNS-only VPN needs Android's approval and visibly occupies the VPN slot. Neither method inspects browsing history or routes ordinary app traffic through FocusLock.

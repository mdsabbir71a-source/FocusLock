package com.focuslock.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(107, 114, 128);
    private static final int FAINT = Color.rgb(156, 163, 175);
    private static final int VIOLET = Color.rgb(52, 116, 76);
    private static final int SOFT_VIOLET = Color.rgb(240, 248, 239);
    private static final int BORDER = Color.rgb(220, 233, 220);
    private static final int GREEN = Color.rgb(45, 130, 78);
    private static final String FAMILY_DNS = "family.cloudflare-dns.com";
    private static final int REQUEST_VPN = 41;
    private static final int REQUEST_NOTIFICATIONS = 42;

    private final List<CheckBox> appChecks = new ArrayList<>();
    private TextView status;
    private TextView selectedCount;
    private TextView adultStatus;
    private EditText graceInput;
    private EditText graceSecondsInput;
    private EditText durationInput;
    private EditText durationSecondsInput;
    private LinearLayout permissionRow;
    private Button protectionButton;
    private Button masterButton;
    private ImageView headerLogo;
    private Button saveButton;
    private ScrollView mainScroll;
    private LinearLayout contentRoot;
    private View permissionSectionAnchor;
    private View appSectionAnchor;
    private View settingsAnchor;
    private TextView analyticsPauses;
    private TextView analyticsTime;
    private TextView analyticsStreak;
    private Button easySetupButton;
    private TextView permissionNote;
    private LinearLayout guideCard;
    private TextView guideTitle;
    private TextView guideBody;
    private TextView guideHint;
    private final Handler guideHandler = new Handler();
    private int currentGuideStep;
    private boolean guidedSetup;
    private boolean skipNotificationPrompt;
    private boolean newGuideIntro;
    private boolean scrollAfterVpn;
    private int waitingForSpecialPermission;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences onboarding = getSharedPreferences("focuslock_onboarding", MODE_PRIVATE);
        newGuideIntro = !onboarding.getBoolean("interactive_guide_v10_seen", false);
        if (newGuideIntro) {
            onboarding.edit().putBoolean("interactive_guide_v10_seen", true).putBoolean("guide_complete", false).apply();
        }
        setContentView(buildUi());
        boolean welcomed = onboarding.getBoolean("welcome_seen", false);
        if (!welcomed) {
            new Handler().postDelayed(this::showFirstLaunchSetup, 550);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshAdultStatus();
        refreshPermissionCards();
        refreshMasterButton();
        refreshAnalytics();
        if (waitingForSpecialPermission != 0) {
            int returningFrom = waitingForSpecialPermission;
            waitingForSpecialPermission = 0;
            new Handler().postDelayed(() -> {
                boolean allowed = returningFrom == 1 ? usageAccessEnabled() : Settings.canDrawOverlays(this);
                if (!allowed) {
                    guidedSetup = false;
                    toast("That permission was not enabled. Tap Easy Setup whenever you're ready.");
                } else {
                    continueEasySetup();
                }
            }, 300);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(248, 251, 246));

        LinearLayout root = column();
        contentRoot = root;
        root.setPadding(dp(20), dp(18), dp(20), dp(36));
        scroll.addView(root, matchWrap());

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        headerLogo = new ImageView(this);
        headerLogo.setImageResource(com.focuslock.app.R.drawable.focuslock_logo);
        headerLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(headerLogo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView brand = text("FocusLock", 16, INK, true);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        brandLp.leftMargin = dp(10);
        header.addView(brand, brandLp);
        TextView kind = text("How it works  ?", 11, VIOLET, true);
        kind.setGravity(Gravity.CENTER);
        kind.setPadding(dp(12), dp(7), dp(12), dp(7));
        kind.setBackground(shape(SOFT_VIOLET, BORDER, 18));
        kind.setOnClickListener(v -> restartGuide());
        header.addView(kind);
        root.addView(header, matchWrap());

        TextView headline = text("Protect your attention.", 29, INK, true);
        root.addView(headline, topMargin(34));
        TextView headline2 = text("Let better things grow.", 25, VIOLET, true);
        root.addView(headline2, topMargin(0));
        TextView intro = text("A calm place for healthier screen habits — simple boundaries, gentle pauses, and more room to breathe.", 13, MUTED, false);
        intro.setLineSpacing(0, 1.18f);
        root.addView(intro, topMargin(12));

        LinearLayout master = row();
        master.setGravity(Gravity.CENTER_VERTICAL);
        master.setPadding(dp(15), dp(14), dp(12), dp(14));
        master.setBackground(shape(Color.WHITE, BORDER, 20));
        LinearLayout masterCopy = column();
        masterCopy.addView(text("🌿  Master control", 14, INK, true));
        masterCopy.addView(text("App limits + FocusLock DNS", 10, MUTED, false), topMargin(3));
        master.addView(masterCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        masterButton = button("ON", GREEN, Color.WHITE);
        masterButton.setMinWidth(dp(72));
        masterButton.setOnClickListener(v -> toggleMaster());
        master.addView(masterButton);
        root.addView(master, topMargin(20));
        reveal(master, 120);

        status = text("No boundary active yet", 12, MUTED, true);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        status.setBackground(shape(Color.WHITE, BORDER, 16));
        root.addView(status, topMargin(10));

        LinearLayout progress = column();
        progress.setPadding(dp(15), dp(15), dp(15), dp(15));
        progress.setBackground(shape(Color.WHITE, BORDER, 20));
        progress.addView(text("Your progress", 15, INK, true));
        progress.addView(text("Small boundaries become meaningful change.", 10, MUTED, false), topMargin(3));
        LinearLayout stats = row();
        analyticsPauses = progressStat("0", "PAUSES");
        analyticsTime = progressStat("0m", "PROTECTED");
        analyticsStreak = progressStat("0", "DAY STREAK");
        stats.addView(analyticsPauses, new LinearLayout.LayoutParams(0, dp(68), 1f));
        stats.addView(analyticsTime, new LinearLayout.LayoutParams(0, dp(68), 1f));
        stats.addView(analyticsStreak, new LinearLayout.LayoutParams(0, dp(68), 1f));
        progress.addView(stats, topMargin(12));
        root.addView(progress, topMargin(10));
        reveal(progress, 220);
        refreshAnalytics();

        TextView protectionLabel = section("STEP 1  •  SET UP");
        permissionSectionAnchor = protectionLabel;
        root.addView(protectionLabel, topMargin(26));
        LinearLayout protection = column();
        protection.setPadding(dp(16), dp(15), dp(16), dp(15));
        protection.setBackground(shape(SOFT_VIOLET, BORDER, 20));
        TextView protectionTitle = text("🍃  Adult Protection", 15, INK, true);
        protection.addView(protectionTitle);
        adultStatus = text("Checking device protection…", 11, VIOLET, true);
        protection.addView(adultStatus, topMargin(5));
        TextView protectionCopy = text("One approval enables lightweight DNS-only filtering. Normal app traffic never passes through FocusLock.", 12, MUTED, false);
        protectionCopy.setLineSpacing(0, 1.15f);
        protection.addView(protectionCopy, topMargin(8));
        protectionButton = button("Enable with one tap  →", VIOLET, Color.WHITE);
        protectionButton.setOnClickListener(v -> toggleAdultProtection());
        protection.addView(protectionButton, topMargin(12));
        root.addView(protection, topMargin(9));
        reveal(protection, 320);

        root.addView(section("REQUIRED APPROVALS"), topMargin(16));
        permissionRow = row();
        root.addView(permissionRow, topMargin(9));
        refreshPermissionCards();
        easySetupButton = button("Guide me through setup   →", INK, Color.WHITE);
        easySetupButton.setTextSize(13);
        easySetupButton.setOnClickListener(v -> startEasySetup());
        root.addView(easySetupButton, topMargin(10));
        permissionNote = text("FocusLock opens each exact Android approval screen and continues automatically when you return.", 10, FAINT, false);
        permissionNote.setGravity(Gravity.CENTER);
        root.addView(permissionNote, topMargin(7));

        guideCard = column();
        guideCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        guideCard.setBackground(shape(Color.rgb(39, 91, 59), Color.rgb(39, 91, 59), 20));
        guideTitle = text("STEP 2 OF 4", 10, Color.rgb(190, 226, 198), true);
        guideTitle.setLetterSpacing(.12f);
        guideBody = text("Choose at least one app below ↓", 16, Color.WHITE, true);
        guideBody.setLineSpacing(0, 1.15f);
        guideCard.addView(guideTitle);
        guideCard.addView(guideBody, topMargin(6));
        guideHint = text("FocusLock affects only the apps you select.", 11, Color.rgb(220, 235, 222), false);
        guideCard.addView(guideHint, topMargin(6));
        guideCard.setOnClickListener(v -> advanceGuideManually());
        root.addView(guideCard, topMargin(22));
        guideCard.setVisibility(getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).getBoolean("guide_complete", false) ? View.GONE : View.VISIBLE);

        LinearLayout chooseHeader = row();
        chooseHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView choose = text("2  •  Choose apps", 17, INK, true);
        chooseHeader.addView(choose, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        selectedCount = text("0 selected", 11, VIOLET, true);
        selectedCount.setPadding(dp(9), dp(5), dp(9), dp(5));
        selectedCount.setBackground(shape(SOFT_VIOLET, BORDER, 14));
        chooseHeader.addView(selectedCount);
        root.addView(chooseHeader, topMargin(28));
        appSectionAnchor = chooseHeader;
        TextView hint = text("Each app gets its own allowance. Only selected apps are affected.", 11, MUTED, false);
        root.addView(hint, topMargin(6));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        addLaunchableApps(grid);
        root.addView(grid, topMargin(12));
        reveal(grid, 420);

        root.addView(section("STEP 3  •  SET YOUR BOUNDARY"), topMargin(28));
        LinearLayout settings = row();
        settingsAnchor = settings;
        LinearLayout useCard = timeCard("USE FOR", "Minutes : seconds");
        graceInput = numberInput(String.valueOf(LockStore.allowance(this) / 60_000));
        graceSecondsInput = numberInput(String.valueOf((LockStore.allowance(this) % 60_000) / 1000));
        useCard.addView(timeInputRow(graceInput, graceSecondsInput), topMargin(8));
        LinearLayout pauseCard = timeCard("PAUSE FOR", "Minutes : seconds");
        durationInput = numberInput(String.valueOf(LockStore.lockDuration(this) / 60_000));
        durationSecondsInput = numberInput(String.valueOf((LockStore.lockDuration(this) % 60_000) / 1000));
        pauseCard.addView(timeInputRow(durationInput, durationSecondsInput), topMargin(8));
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half1.rightMargin = dp(5);
        settings.addView(useCard, half1);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(5);
        settings.addView(pauseCard, half2);
        root.addView(settings, topMargin(10));
        reveal(settings, 500);

        saveButton = button("Save & start boundary   →", INK, Color.WHITE);
        saveButton.setTextSize(14);
        saveButton.setOnClickListener(v -> startCommitment());
        root.addView(saveButton, topMargin(18));
        if (LockStore.packages(this).isEmpty()) markDirty(); else markSaved();
        TextView foot = text("Kind boundary • no activity leaves your device", 10, FAINT, false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, topMargin(10));
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(500).start();
        headerLogo.animate().rotation(4f).scaleX(1.04f).scaleY(1.04f).setDuration(1200).withEndAction(() ->
                headerLogo.animate().rotation(-3f).scaleX(1f).scaleY(1f).setDuration(1200).start()).start();
        mainScroll.post(this::resumeGuide);
        return scroll;
    }

    private void showFirstLaunchSetup() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Welcome to FocusLock 🌿")
                .setMessage("Let's prepare everything now. FocusLock will open each required Android permission in order. Approve it, return here, and the next one will appear automatically.")
                .setPositiveButton("Begin setup", (dialog, which) -> {
                    getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).edit().putBoolean("welcome_seen", true).apply();
                    startEasySetup();
                })
                .setCancelable(false)
                .show();
    }

    private void toggleMaster() {
        boolean enable = !LockStore.isEnabled(this);
        LockStore.setEnabled(this, enable);
        if (!enable) {
            stopService(new Intent(this, FocusMonitorService.class));
            stopService(new Intent(this, FamilyDnsVpnService.class).setAction(FamilyDnsVpnService.ACTION_STOP));
            toast("FocusLock is paused. Your choices are still saved.");
        } else {
            if (!usageAccessEnabled() || !Settings.canDrawOverlays(this)) {
                startEasySetup();
            } else {
                startSavedMonitoring();
                if (!adultProtectionActive()) requestAdultProtection();
                toast("FocusLock is back on.");
            }
        }
        refreshMasterButton();
        refreshStatus();
    }

    private void refreshMasterButton() {
        if (masterButton == null) return;
        boolean enabled = LockStore.isEnabled(this);
        masterButton.setText(enabled ? "ON  ●" : "OFF  ○");
        masterButton.setTextColor(enabled ? Color.WHITE : MUTED);
        masterButton.setBackground(shape(enabled ? GREEN : Color.rgb(238, 241, 236), enabled ? GREEN : BORDER, 22));
    }

    private void startSavedMonitoring() {
        if (LockStore.packages(this).isEmpty()) return;
        Intent monitor = new Intent(this, FocusMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(monitor); else startService(monitor);
    }

    private void refreshPermissionCards() {
        if (permissionRow == null) return;
        permissionRow.removeAllViews();
        boolean usage = usageAccessEnabled();
        boolean overlay = Settings.canDrawOverlays(this);
        View usageCard = permissionCard("Usage Access", "Counts only real time spent in selected apps.", usage, v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        View overlayCard = permissionCard("Gentle Lock", "Shows the pause screen when a limit is reached.", overlay, v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        left.rightMargin = dp(5);
        permissionRow.addView(usageCard, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        right.leftMargin = dp(5);
        permissionRow.addView(overlayCard, right);
        boolean approvalsReady = usage && overlay && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        permissionRow.setVisibility(approvalsReady ? View.GONE : View.VISIBLE);
        if (easySetupButton != null) {
            easySetupButton.setText(approvalsReady ? "Permissions ready  ✓" : "Guide me through setup   →");
            easySetupButton.setAlpha(approvalsReady ? .45f : 1f);
            easySetupButton.setEnabled(!approvalsReady);
        }
        if (permissionNote != null) permissionNote.setVisibility(approvalsReady ? View.GONE : View.VISIBLE);
    }

    private View permissionCard(String title, String copy, boolean enabled, View.OnClickListener click) {
        LinearLayout card = column();
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        card.setBackground(shape(Color.WHITE, enabled ? Color.rgb(167, 243, 208) : BORDER, 18));
        TextView state = text(enabled ? "●  READY" : "○  NEEDED", 9, enabled ? GREEN : VIOLET, true);
        card.addView(state);
        card.addView(text(title, 13, INK, true), topMargin(8));
        TextView detail = text(copy, 10, MUTED, false);
        detail.setMinHeight(dp(42));
        card.addView(detail, topMargin(5));
        Button action = button(enabled ? "Enabled  ✓" : "Allow", enabled ? Color.rgb(236, 253, 245) : INK, enabled ? GREEN : Color.WHITE);
        action.setEnabled(!enabled);
        action.setOnClickListener(click);
        card.addView(action, topMargin(8));
        return card;
    }

    private void addLaunchableApps(GridLayout grid) {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(launcher, 0);
        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        for (ResolveInfo info : resolved) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(getPackageName())) unique.put(pkg, info);
        }
        List<ResolveInfo> apps = new ArrayList<>(unique.values());
        apps.sort(Comparator.comparing(a -> a.loadLabel(pm).toString().toLowerCase()));
        Set<String> saved = LockStore.packages(this);
        for (ResolveInfo info : apps) {
            String pkg = info.activityInfo.packageName;
            CheckBox check = new CheckBox(this);
            check.setTag(pkg);
            check.setText(info.loadLabel(pm));
            check.setTextSize(10);
            check.setTextColor(INK);
            check.setGravity(Gravity.CENTER);
            check.setButtonDrawable(null);
            check.setPadding(dp(6), dp(10), dp(6), dp(8));
            check.setMaxLines(2);
            try {
                Drawable icon = info.loadIcon(pm);
                icon.setBounds(0, 0, dp(34), dp(34));
                check.setCompoundDrawables(null, icon, null, null);
                check.setCompoundDrawablePadding(dp(7));
            } catch (Exception ignored) {}
            check.setChecked(saved.contains(pkg));
            styleAppTile(check);
            check.setOnCheckedChangeListener((button, checked) -> {
                styleAppTile(check);
                refreshSelectedCount();
                markDirty();
                check.animate().scaleX(checked ? 1.04f : 1f).scaleY(checked ? 1.04f : 1f).setDuration(180).start();
                if (selectedAppCount() == 0) {
                    updateGuideStep(2, appSectionAnchor);
                } else if (checked) {
                    maybeGuideToTime();
                }
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(96);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(check, lp);
            appChecks.add(check);
        }
        refreshSelectedCount();
    }

    private void styleAppTile(CheckBox check) {
        check.setBackground(shape(check.isChecked() ? SOFT_VIOLET : Color.WHITE, check.isChecked() ? VIOLET : BORDER, 18));
        if (Build.VERSION.SDK_INT >= 21) check.setBackgroundTintList(null);
    }

    private void refreshSelectedCount() {
        if (selectedCount == null) return;
        int count = selectedAppCount();
        selectedCount.setText(count + (count == 1 ? " selected" : " selected"));
    }

    private int selectedAppCount() {
        int count = 0;
        for (CheckBox check : appChecks) if (check.isChecked()) count++;
        return count;
    }

    private void startCommitment() {
        Set<String> selected = new HashSet<>();
        for (CheckBox check : appChecks) if (check.isChecked()) selected.add((String) check.getTag());
        if (selected.isEmpty()) {
            toast("Choose at least one app first.");
            updateGuideStep(2, appSectionAnchor);
            return;
        }
        long grace = parseDuration(graceInput, graceSecondsInput, "use time");
        long duration = parseDuration(durationInput, durationSecondsInput, "pause time");
        if (grace < 1 || duration < 1) {
            updateGuideStep(3, settingsAnchor);
            return;
        }
        if (!usageAccessEnabled() || !Settings.canDrawOverlays(this)) { toast("Let's finish the required permissions first."); startEasySetup(); return; }
        LockStore.configure(this, selected, grace, duration);
        LockStore.setEnabled(this, true);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        Intent monitor = new Intent(this, FocusMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(monitor); else startService(monitor);
        toast("Your gentle boundary is active.");
        markSaved();
        updateGuideStep(5, saveButton);
        getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).edit().putBoolean("guide_complete", true).apply();
        guideHandler.postDelayed(() -> {
            if (guideCard != null && !isFinishing()) {
                guideCard.animate().alpha(0f).setDuration(300).withEndAction(() -> guideCard.setVisibility(View.GONE)).start();
            }
        }, 1800);
        refreshMasterButton();
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        Set<String> packages = LockStore.packages(this);
        if (!LockStore.isEnabled(this)) {
            status.setText("○  FocusLock paused — settings are safely saved");
            status.setTextColor(MUTED);
        } else if (packages.isEmpty()) {
            status.setText("○  No boundary active yet");
            status.setTextColor(MUTED);
        } else {
            status.setText("●  Boundary active  •  " + packages.size() + " apps  •  " + friendly(LockStore.allowance(this)) + " use then " + friendly(LockStore.lockDuration(this)) + " pause");
            status.setTextColor(GREEN);
        }
    }

    private void showAdultProtectionIntro() {
        if (isFinishing() || adultProtectionActive()) return;
        new AlertDialog.Builder(this)
                .setTitle("Protect this phone from adult sites")
                .setMessage("One Android VPN approval can enable adult-site and malware filtering across this phone. FocusLock routes only DNS lookups through the filter—not your normal app traffic, messages, photos, or passwords. Android permits one active VPN at a time.")
                .setPositiveButton("Allow", (d, w) -> requestAdultProtection())
                .setNegativeButton("Not now", null)
                .show();
    }

    private void requestAdultProtection() {
        LockStore.setEnabled(this, true);
        refreshMasterButton();
        Intent approval = VpnService.prepare(this);
        if (approval != null) {
            startActivityForResult(approval, REQUEST_VPN);
        } else {
            startAdultProtectionService();
        }
    }

    private void toggleAdultProtection() {
        if (FamilyDnsVpnService.isActive()) {
            stopService(new Intent(this, FamilyDnsVpnService.class).setAction(FamilyDnsVpnService.ACTION_STOP));
            new Handler().postDelayed(() -> { refreshAdultStatus(); toast("Adult Protection is off."); }, 250);
        } else if (privateDnsActive()) {
            toast("Protection is supplied by Android Private DNS. Change it from the phone's Private DNS settings.");
            try { startActivity(new Intent("android.settings.PRIVATE_DNS_SETTINGS")); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
        } else {
            requestAdultProtection();
        }
    }

    private void startAdultProtectionService() {
        Intent service = new Intent(this, FamilyDnsVpnService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        new Handler().postDelayed(() -> {
            refreshAdultStatus();
            toast("Adult Protection is active.");
        }, 500);
    }

    private void startEasySetup() {
        guidedSetup = true;
        skipNotificationPrompt = false;
        continueEasySetup();
    }

    private void continueEasySetup() {
        if (!guidedSetup) return;
        if (!usageAccessEnabled()) {
            waitingForSpecialPermission = 1;
            toast("Turn on Permit usage access, then return to FocusLock.");
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            waitingForSpecialPermission = 2;
            toast("Turn on Allow display over other apps, then return.");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !skipNotificationPrompt
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        if (!adultProtectionActive()) {
            scrollAfterVpn = true;
            guidedSetup = false;
            requestAdultProtection();
            return;
        }
        guidedSetup = false;
        skipNotificationPrompt = false;
        toast("Everything is ready. You can start your boundary now.");
        scrollToBoundarySetup();
    }

    private boolean adultProtectionActive() {
        if (FamilyDnsVpnService.isActive()) return true;
        return privateDnsActive();
    }

    private boolean privateDnsActive() {
        try {
            String mode = Settings.Global.getString(getContentResolver(), "private_dns_mode");
            String specifier = Settings.Global.getString(getContentResolver(), "private_dns_specifier");
            return "hostname".equals(mode) && FAMILY_DNS.equalsIgnoreCase(specifier == null ? "" : specifier.trim());
        } catch (Exception e) { return false; }
    }

    private void refreshAdultStatus() {
        if (adultStatus == null) return;
        boolean active = adultProtectionActive();
        adultStatus.setText(active ? "●  ACTIVE ON THIS DEVICE" : "○  SETUP REQUIRED");
        adultStatus.setTextColor(active ? GREEN : VIOLET);
        if (protectionButton != null) {
            if (FamilyDnsVpnService.isActive()) protectionButton.setText("Turn protection off");
            else if (privateDnsActive()) protectionButton.setText("Managed by Private DNS  ✓");
            else protectionButton.setText("Enable with one tap  →");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) startAdultProtectionService();
            else toast("Adult Protection was not enabled.");
            if (scrollAfterVpn) {
                scrollAfterVpn = false;
                new Handler().postDelayed(this::scrollToBoundarySetup, 650);
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (guidedSetup) {
                if (!granted) {
                    skipNotificationPrompt = true;
                    toast("Notifications are off, but FocusLock can still run.");
                }
                continueEasySetup();
            } else if (!granted) {
                toast("Notifications are off, but FocusLock can still run.");
            }
        }
    }

    private boolean usageAccessEnabled() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        ApplicationInfo info = getApplicationInfo();
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, info.uid, getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private long parseDuration(EditText minutesInput, EditText secondsInput, String label) {
        try {
            int minutes = Integer.parseInt(minutesInput.getText().toString().trim());
            int seconds = Integer.parseInt(secondsInput.getText().toString().trim());
            if (minutes >= 0 && seconds >= 0 && seconds <= 59 && (minutes > 0 || seconds > 0)) return (minutes * 60L + seconds) * 1000L;
        } catch (Exception ignored) {}
        toast("Enter a " + label + " above zero, with seconds between 0 and 59.");
        return -1;
    }

    private void scrollToBoundarySetup() {
        if (mainScroll == null || appSectionAnchor == null) return;
        updateGuideStep(2, appSectionAnchor);
    }

    private void maybeGuideToTime() {
        if (getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).getBoolean("guide_complete", false)) return;
        if (currentGuideStep >= 3 || selectedAppCount() == 0) return;
        guideHandler.postDelayed(() -> {
            if (selectedAppCount() > 0 && currentGuideStep < 3) updateGuideStep(3, settingsAnchor);
        }, 420);
    }

    private void maybeGuideToSave() {
        if (getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).getBoolean("guide_complete", false)) return;
        if (currentGuideStep != 3 || selectedAppCount() == 0) return;
        guideHandler.postDelayed(() -> {
            if (currentGuideStep == 3 && readDurationSilently(graceInput, graceSecondsInput) > 0
                    && readDurationSilently(durationInput, durationSecondsInput) > 0) {
                updateGuideStep(4, saveButton);
            }
        }, 850);
    }

    private void resumeGuide() {
        if (guideCard == null) return;
        if (getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).getBoolean("guide_complete", false)) {
            guideCard.setVisibility(View.GONE);
            return;
        }
        boolean permissionsReady = usageAccessEnabled() && Settings.canDrawOverlays(this)
                && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        if (!permissionsReady || !adultProtectionActive()) updateGuideStep(1, permissionSectionAnchor);
        else if (newGuideIntro || selectedAppCount() == 0) {
            newGuideIntro = false;
            updateGuideStep(2, appSectionAnchor);
        }
        else updateGuideStep(3, settingsAnchor);
    }

    private void restartGuide() {
        getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).edit()
                .putBoolean("guide_complete", false)
                .remove("app_tip_seen")
                .remove("time_tip_seen")
                .remove("finish_seen")
                .apply();
        currentGuideStep = 0;
        boolean notificationsReady = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (!usageAccessEnabled() || !Settings.canDrawOverlays(this) || !notificationsReady || !adultProtectionActive()) {
            updateGuideStep(1, permissionSectionAnchor);
            guideHandler.postDelayed(this::startEasySetup, 450);
        } else {
            updateGuideStep(2, appSectionAnchor);
        }
    }

    private void advanceGuideManually() {
        if (currentGuideStep == 1) {
            startEasySetup();
        } else if (currentGuideStep == 2 && selectedAppCount() > 0) {
            updateGuideStep(3, settingsAnchor);
        } else if (currentGuideStep == 3 && readDurationSilently(graceInput, graceSecondsInput) > 0
                && readDurationSilently(durationInput, durationSecondsInput) > 0) {
            updateGuideStep(4, saveButton);
        }
    }

    private void updateGuideStep(int step, View target) {
        if (guideCard == null || target == null || contentRoot == null) return;
        currentGuideStep = step;
        guideCard.animate().cancel();
        guideCard.setAlpha(1f);
        guideCard.setVisibility(View.VISIBLE);
        int guideGreen = step == 5 ? GREEN : Color.rgb(39, 91, 59);
        guideCard.setBackground(shape(guideGreen, guideGreen, 20));
        if (step == 1) {
            guideTitle.setText("STEP 1 OF 4");
            guideBody.setText("Allow the setup requests ↓");
            guideHint.setText("Tap this green guide or “Guide me through setup,” then approve each Android screen.");
        } else if (step == 2) {
            guideTitle.setText("STEP 2 OF 4");
            guideBody.setText("Choose at least one app ↓");
            guideHint.setText(selectedAppCount() == 0
                    ? "Tap an app below. Only apps you select will be affected."
                    : "You already have an app selected. Tap this card to continue.");
        } else if (step == 3) {
            guideTitle.setText("STEP 3 OF 4");
            guideBody.setText("Now set both timers ↓");
            guideHint.setText("USE FOR is time before blocking; PAUSE FOR is lock time. If the values already look right, tap this card.");
        } else if (step == 4) {
            guideTitle.setText("STEP 4 OF 4");
            guideBody.setText("Tap “Save changes & start” ↓");
            guideHint.setText("This activates the boundary. The button fades after your changes are saved.");
        } else {
            guideTitle.setText("YOU’RE READY  ✓");
            guideBody.setText("Your boundary is active");
            guideHint.setText("FocusLock will guide you again whenever you tap “How it works?”.");
        }

        ViewGroup parent = (ViewGroup) guideCard.getParent();
        if (parent != null) parent.removeView(guideCard);
        int index = contentRoot.indexOfChild(target);
        contentRoot.addView(guideCard, Math.max(0, index), topMargin(12));
        guideCard.setTranslationY(dp(10));
        guideCard.animate().translationY(0f).setDuration(280).start();
        mainScroll.postDelayed(() -> mainScroll.smoothScrollTo(0, Math.max(0, guideCard.getTop() - dp(16))), 90);
        if (step == 5) target.animate().alpha(.38f).setDuration(360).start();
        else pulseTarget(target);
    }

    private void pulseTarget(View target) {
        target.animate().cancel();
        target.setAlpha(.55f);
        target.setScaleX(.98f);
        target.setScaleY(.98f);
        target.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(180).setDuration(520).start();
    }

    private long readDurationSilently(EditText minutesInput, EditText secondsInput) {
        if (minutesInput == null || secondsInput == null) return -1;
        try {
            int minutes = Integer.parseInt(minutesInput.getText().toString().trim());
            int seconds = Integer.parseInt(secondsInput.getText().toString().trim());
            if (minutes >= 0 && seconds >= 0 && seconds <= 59 && (minutes > 0 || seconds > 0)) {
                return (minutes * 60L + seconds) * 1000L;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void refreshAnalytics() {
        if (analyticsPauses == null) return;
        analyticsPauses.setText(LockStore.totalPauses(this) + "\nPAUSES");
        analyticsTime.setText(formatProtected(LockStore.protectedTime(this)) + "\nPROTECTED");
        analyticsStreak.setText(LockStore.streak(this) + "\nDAY STREAK");
        analyticsPauses.animate().scaleX(1.04f).scaleY(1.04f).setDuration(180).withEndAction(() -> analyticsPauses.animate().scaleX(1f).scaleY(1f).setDuration(180).start()).start();
    }

    private String formatProtected(long ms) {
        long minutes = ms / 60_000;
        if (minutes >= 60) return (minutes / 60) + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m";
        return Math.max(0, ms / 1000) + "s";
    }

    private TextView progressStat(String value, String label) {
        TextView stat = text(value + "\n" + label, 15, INK, true);
        stat.setGravity(Gravity.CENTER);
        stat.setLineSpacing(dp(3), 1f);
        stat.setBackground(shape(SOFT_VIOLET, BORDER, 16));
        stat.setPadding(dp(3), dp(9), dp(3), dp(9));
        return stat;
    }

    private void reveal(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(dp(18));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(520).start();
    }

    private void markDirty() {
        if (saveButton == null) return;
        saveButton.setEnabled(true);
        saveButton.animate().alpha(1f).setDuration(180).start();
        saveButton.setText("Save changes & start   →");
    }

    private void markSaved() {
        if (saveButton == null) return;
        saveButton.setText("Boundary saved  ✓");
        saveButton.setEnabled(false);
        saveButton.animate().alpha(.38f).setDuration(420).start();
    }

    private LinearLayout timeCard(String label, String detail) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(shape(Color.WHITE, BORDER, 18));
        card.addView(text(label, 10, FAINT, true));
        card.addView(text(detail, 11, MUTED, false), topMargin(4));
        return card;
    }

    private EditText numberInput(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextSize(24);
        input.setTextColor(INK);
        input.setGravity(Gravity.CENTER);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setPadding(dp(8), dp(7), dp(8), dp(7));
        input.setBackground(shape(Color.rgb(249, 250, 251), BORDER, 14));
        if (Build.VERSION.SDK_INT >= 21) input.setBackgroundTintList(null);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                markDirty();
                maybeGuideToSave();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        return input;
    }

    private LinearLayout timeInputRow(EditText minutes, EditText seconds) {
        LinearLayout fields = row();
        fields.setGravity(Gravity.CENTER_VERTICAL);
        fields.addView(minutes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView colon = text(":", 22, MUTED, true);
        colon.setGravity(Gravity.CENTER);
        fields.addView(colon, new LinearLayout.LayoutParams(dp(18), ViewGroup.LayoutParams.WRAP_CONTENT));
        fields.addView(seconds, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return fields;
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(shape(background, background, 22));
        if (Build.VERSION.SDK_INT >= 21) button.setBackgroundTintList(null);
        return button;
    }

    private TextView section(String label) {
        TextView view = text(label, 10, FAINT, true);
        view.setLetterSpacing(.14f);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams topMargin(int margin) { LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(margin); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String friendly(long ms) {
        long totalSeconds = Math.max(1, ms / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 60 && minutes % 60 == 0 && seconds == 0) return (minutes / 60) + "h";
        if (minutes == 0) return seconds + "s";
        return seconds == 0 ? minutes + "m" : minutes + "m " + seconds + "s";
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}

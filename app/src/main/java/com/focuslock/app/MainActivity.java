package com.focuslock.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
    private static final int VIOLET = Color.rgb(139, 92, 246);
    private static final int SOFT_VIOLET = Color.rgb(245, 243, 255);
    private static final int BORDER = Color.rgb(237, 233, 254);
    private static final int GREEN = Color.rgb(16, 185, 129);
    private static final String FAMILY_DNS = "family.cloudflare-dns.com";
    private static final int REQUEST_VPN = 41;
    private static final int REQUEST_NOTIFICATIONS = 42;

    private final List<CheckBox> appChecks = new ArrayList<>();
    private TextView status;
    private TextView selectedCount;
    private TextView adultStatus;
    private EditText graceInput;
    private EditText durationInput;
    private LinearLayout permissionRow;
    private Button protectionButton;
    private boolean guidedSetup;
    private int waitingForSpecialPermission;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        if (!adultProtectionActive()) {
            new Handler().postDelayed(this::showAdultProtectionIntro, 650);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshAdultStatus();
        refreshPermissionCards();
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
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(254, 254, 252));

        LinearLayout root = column();
        root.setPadding(dp(20), dp(18), dp(20), dp(36));
        scroll.addView(root, matchWrap());

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(com.focuslock.app.R.drawable.focuslock_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView brand = text("FocusLock", 16, INK, true);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        brandLp.leftMargin = dp(10);
        header.addView(brand, brandLp);
        TextView kind = text("calm mode  ✦", 11, VIOLET, true);
        kind.setGravity(Gravity.CENTER);
        kind.setPadding(dp(12), dp(7), dp(12), dp(7));
        kind.setBackground(shape(SOFT_VIOLET, BORDER, 18));
        header.addView(kind);
        root.addView(header, matchWrap());

        TextView headline = text("A little boundary", 30, INK, true);
        root.addView(headline, topMargin(34));
        TextView headline2 = text("goes a long way.", 30, FAINT, true);
        root.addView(headline2, topMargin(0));
        TextView intro = text("Choose what deserves a pause. FocusLock will gently step in only when your limit is reached.", 13, MUTED, false);
        intro.setLineSpacing(0, 1.18f);
        root.addView(intro, topMargin(12));

        status = text("No boundary active yet", 12, MUTED, true);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        status.setBackground(shape(Color.WHITE, BORDER, 16));
        root.addView(status, topMargin(18));

        TextView protectionLabel = section("PROTECTION");
        root.addView(protectionLabel, topMargin(26));
        LinearLayout protection = column();
        protection.setPadding(dp(16), dp(15), dp(16), dp(15));
        protection.setBackground(shape(SOFT_VIOLET, BORDER, 20));
        TextView protectionTitle = text("Adult Protection", 15, INK, true);
        protection.addView(protectionTitle);
        adultStatus = text("Checking device protection…", 11, VIOLET, true);
        protection.addView(adultStatus, topMargin(5));
        TextView protectionCopy = text("One approval enables lightweight DNS-only filtering. Normal app traffic never passes through FocusLock.", 12, MUTED, false);
        protectionCopy.setLineSpacing(0, 1.15f);
        protection.addView(protectionCopy, topMargin(8));
        protectionButton = button("Enable with one tap  →", VIOLET, Color.WHITE);
        protectionButton.setOnClickListener(v -> requestAdultProtection());
        protection.addView(protectionButton, topMargin(12));
        root.addView(protection, topMargin(9));

        root.addView(section("PERMISSIONS"), topMargin(26));
        permissionRow = row();
        root.addView(permissionRow, topMargin(9));
        refreshPermissionCards();
        Button easySetup = button("Allow required permissions   →", INK, Color.WHITE);
        easySetup.setTextSize(13);
        easySetup.setOnClickListener(v -> startEasySetup());
        root.addView(easySetup, topMargin(10));
        TextView permissionNote = text("FocusLock opens each exact Android approval screen and continues automatically when you return.", 10, FAINT, false);
        permissionNote.setGravity(Gravity.CENTER);
        root.addView(permissionNote, topMargin(7));

        LinearLayout chooseHeader = row();
        chooseHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView choose = text("Choose apps to pause", 17, INK, true);
        chooseHeader.addView(choose, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        selectedCount = text("0 selected", 11, VIOLET, true);
        selectedCount.setPadding(dp(9), dp(5), dp(9), dp(5));
        selectedCount.setBackground(shape(SOFT_VIOLET, BORDER, 14));
        chooseHeader.addView(selectedCount);
        root.addView(chooseHeader, topMargin(28));
        TextView hint = text("Each app gets its own allowance. Only selected apps are affected.", 11, MUTED, false);
        root.addView(hint, topMargin(6));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        addLaunchableApps(grid);
        root.addView(grid, topMargin(12));

        root.addView(section("YOUR BOUNDARY"), topMargin(28));
        LinearLayout settings = row();
        LinearLayout useCard = timeCard("USE FOR", "Minutes of actual use");
        graceInput = numberInput(String.valueOf(Math.max(1, LockStore.allowance(this) / 60_000)));
        useCard.addView(graceInput, topMargin(8));
        LinearLayout pauseCard = timeCard("PAUSE FOR", "Minutes locked");
        durationInput = numberInput(String.valueOf(Math.max(1, LockStore.lockDuration(this) / 60_000)));
        pauseCard.addView(durationInput, topMargin(8));
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half1.rightMargin = dp(5);
        settings.addView(useCard, half1);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(5);
        settings.addView(pauseCard, half2);
        root.addView(settings, topMargin(10));

        Button start = button("Start gentle boundary   →", INK, Color.WHITE);
        start.setTextSize(14);
        start.setOnClickListener(v -> startCommitment());
        root.addView(start, topMargin(18));
        TextView foot = text("Kind boundary • no activity leaves your device", 10, FAINT, false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, topMargin(10));
        return scroll;
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
        int count = 0;
        for (CheckBox check : appChecks) if (check.isChecked()) count++;
        selectedCount.setText(count + (count == 1 ? " selected" : " selected"));
    }

    private void startCommitment() {
        Set<String> selected = new HashSet<>();
        for (CheckBox check : appChecks) if (check.isChecked()) selected.add((String) check.getTag());
        if (selected.isEmpty()) { toast("Choose at least one app first."); return; }
        int grace = parsePositive(graceInput, "use time");
        int duration = parsePositive(durationInput, "pause time");
        if (grace < 1 || duration < 1) return;
        if (!usageAccessEnabled() || !Settings.canDrawOverlays(this)) { toast("Let's finish the required permissions first."); startEasySetup(); return; }
        LockStore.configure(this, selected, grace * 60_000L, duration * 60_000L);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        Intent monitor = new Intent(this, FocusMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(monitor); else startService(monitor);
        toast("Your gentle boundary is active.");
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        Set<String> packages = LockStore.packages(this);
        if (packages.isEmpty()) {
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
        Intent approval = VpnService.prepare(this);
        if (approval != null) {
            startActivityForResult(approval, REQUEST_VPN);
        } else {
            startAdultProtectionService();
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
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        if (!adultProtectionActive()) {
            guidedSetup = false;
            requestAdultProtection();
            return;
        }
        guidedSetup = false;
        toast("Everything is ready. You can start your boundary now.");
    }

    private boolean adultProtectionActive() {
        if (FamilyDnsVpnService.isActive()) return true;
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
        if (protectionButton != null) protectionButton.setText(active ? "Protection enabled  ✓" : "Enable with one tap  →");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) startAdultProtectionService();
            else toast("Adult Protection was not enabled.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (guidedSetup) continueEasySetup();
            else if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) toast("Notifications are off, but FocusLock can still run.");
        }
    }

    private boolean usageAccessEnabled() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        ApplicationInfo info = getApplicationInfo();
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, info.uid, getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private int parsePositive(EditText input, String label) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value > 0) return value;
        } catch (Exception ignored) {}
        toast("Enter a positive " + label + " in minutes.");
        return -1;
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
        return input;
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
    private String friendly(long ms) { long minutes = Math.max(1, ms / 60_000); return minutes >= 60 && minutes % 60 == 0 ? (minutes / 60) + "h" : minutes + "m"; }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}

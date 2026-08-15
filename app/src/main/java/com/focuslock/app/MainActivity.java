package com.focuslock.app;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private final List<CheckBox> appChecks = new ArrayList<>();
    private TextView status;
    private EditText graceInput;
    private EditText durationInput;
    private TextView adultStatus;
    private static final String FAMILY_DNS = "family.cloudflare-dns.com";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        if (!adultProtectionActive()) new Handler(android.os.Looper.getMainLooper()).postDelayed(this::showAdultProtectionIntro, 350);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshAdultStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(36));
        root.setBackgroundColor(Color.rgb(14, 11, 22));
        scroll.addView(root);

        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.HORIZONTAL); hero.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.focuslock_logo); hero.addView(logo, new LinearLayout.LayoutParams(dp(82), dp(82)));
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setPadding(dp(12), 0, 0, 0);
        brand.addView(text("FocusLock", 30, true));
        TextView intro = text("Decide now. Scroll less later.", 15, false); intro.setTextColor(Color.rgb(180, 172, 199)); brand.addView(intro);
        hero.addView(brand); root.addView(hero, margins(0, 0, 0, dp(20)));

        LinearLayout protection = new LinearLayout(this); protection.setOrientation(LinearLayout.VERTICAL); protection.setPadding(dp(16), dp(14), dp(16), dp(14)); protection.setBackground(panel(Color.rgb(33, 25, 51), 18));
        TextView protectionTitle = text("Adult Protection", 19, true); protection.addView(protectionTitle);
        adultStatus = text("Checking protection…", 14, true); adultStatus.setPadding(0, dp(5), 0, dp(5)); protection.addView(adultStatus);
        TextView protectionCopy = text("Blocks known adult and malware domains device-wide using encrypted Family DNS. FocusLock never receives your browsing traffic.", 14, false); protectionCopy.setTextColor(Color.rgb(180, 172, 199)); protection.addView(protectionCopy);
        Button protect = button("Set up device protection"); protect.setOnClickListener(v -> openAdultProtectionSetup()); protection.addView(protect, margins(0, dp(10), 0, 0));
        root.addView(protection, margins(0, 0, 0, dp(16)));

        status = text("", 16, true);
        status.setPadding(dp(16), dp(14), dp(16), dp(14));
        status.setBackground(panel(Color.rgb(36, 28, 56), 18));
        root.addView(status, margins(0, 0, 0, dp(18)));

        root.addView(text("1. Allow app blocking", 20, true));
        TextView permissionHelp = text("FocusLock needs Usage Access to detect the current app and Display Over Other Apps to show the lock screen. It does not read screen content.", 14, false);
        permissionHelp.setTextColor(Color.rgb(180, 172, 199));
        root.addView(permissionHelp, margins(0, dp(5), 0, dp(10)));
        Button usage = button("1A. Allow Usage Access");
        usage.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        root.addView(usage, margins(0, 0, 0, dp(8)));
        Button overlay = button("1B. Allow Display Over Apps");
        overlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        root.addView(overlay, margins(0, 0, 0, dp(22)));

        root.addView(text("2. Choose apps to block", 20, true));
        LinearLayout appPanel = new LinearLayout(this); appPanel.setOrientation(LinearLayout.VERTICAL); appPanel.setPadding(dp(12), dp(8), dp(12), dp(8)); appPanel.setBackground(panel(Color.rgb(26, 21, 40), 18));
        addLaunchableApps(appPanel); root.addView(appPanel, margins(0, dp(10), 0, 0));

        root.addView(text("3. Set your commitment", 20, true), margins(0, dp(20), 0, dp(8)));
        graceInput = numberInput("Allowed usage before lock (minutes)", "1");
        durationInput = numberInput("Lock duration (minutes)", "60");
        root.addView(graceInput, margins(0, dp(5), 0, dp(10)));
        root.addView(durationInput, margins(0, 0, 0, dp(14)));

        Button start = button("Start commitment");
        start.setTextSize(17); start.setTextColor(Color.WHITE); start.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(124, 82, 240)));
        start.setPadding(0, dp(15), 0, dp(15));
        start.setOnClickListener(v -> startCommitment());
        root.addView(start);
        return scroll;
    }

    private void addLaunchableApps(LinearLayout root) {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ApplicationInfo> apps = new ArrayList<>();
        for (android.content.pm.ResolveInfo info : pm.queryIntentActivities(launcher, 0)) {
            ApplicationInfo app = info.activityInfo.applicationInfo;
            if (!app.packageName.equals(getPackageName()) && apps.stream().noneMatch(a -> a.packageName.equals(app.packageName))) apps.add(app);
        }
        Collections.sort(apps, Comparator.comparing(a -> pm.getApplicationLabel(a).toString().toLowerCase()));
        Set<String> saved = LockStore.packages(this);
        for (ApplicationInfo app : apps) {
            CheckBox check = new CheckBox(this);
            check.setText(pm.getApplicationLabel(app));
            check.setTag(app.packageName);
            check.setChecked(saved.contains(app.packageName));
            check.setTextSize(16);
            check.setTextColor(Color.rgb(239, 235, 247));
            check.setButtonTintList(ColorStateList.valueOf(Color.rgb(139, 92, 246)));
            check.setPadding(0, dp(5), 0, dp(5));
            appChecks.add(check);
            root.addView(check);
        }
    }

    private void startCommitment() {
        Set<String> selected = new HashSet<>();
        for (CheckBox check : appChecks) if (check.isChecked()) selected.add((String) check.getTag());
        if (selected.isEmpty()) { Toast.makeText(this, "Choose at least one app", Toast.LENGTH_SHORT).show(); return; }
        int grace = parsePositive(graceInput, 1);
        int duration = parsePositive(durationInput, 60);
        if (!usageAccessEnabled() || !Settings.canDrawOverlays(this)) { Toast.makeText(this, "Allow both Usage Access and Display Over Apps first", Toast.LENGTH_LONG).show(); return; }
        LockStore.configure(this, selected, grace * 60_000L, duration * 60_000L);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        Intent monitor = new Intent(this, FocusMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(monitor); else startService(monitor);
        Toast.makeText(this, "Usage monitoring started", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        Set<String> selected = LockStore.packages(this);
        if (selected.isEmpty()) status.setText("Ready for a new commitment");
        else status.setText("Monitoring " + selected.size() + " app" + (selected.size() == 1 ? "" : "s") + " • " + friendly(LockStore.allowance(this)) + " usage allowed each");
    }

    private void showAdultProtectionIntro() {
        new AlertDialog.Builder(this)
                .setTitle("Protect this device")
                .setMessage("FocusLock can block known adult and malware domains across browsers and apps. Android requires one system setting. DNS requests will be handled by Cloudflare Family DNS; FocusLock does not receive them.")
                .setPositiveButton("Set up now", (dialog, which) -> openAdultProtectionSetup())
                .setNegativeButton("Later", null)
                .show();
    }

    private void openAdultProtectionSetup() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("FocusLock Family DNS", FAMILY_DNS));
        Toast.makeText(this, "Copied. Choose Private DNS provider hostname, paste it, then Save.", Toast.LENGTH_LONG).show();
        try { startActivity(new Intent("android.settings.PRIVATE_DNS_SETTINGS")); }
        catch (Exception unavailable) { startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
    }

    private boolean adultProtectionActive() {
        String mode = Settings.Global.getString(getContentResolver(), "private_dns_mode");
        String host = Settings.Global.getString(getContentResolver(), "private_dns_specifier");
        return "hostname".equals(mode) && FAMILY_DNS.equalsIgnoreCase(host);
    }

    private void refreshAdultStatus() {
        if (adultStatus == null) return;
        if (adultProtectionActive()) { adultStatus.setText("● ACTIVE — adult sites filtered"); adultStatus.setTextColor(Color.rgb(91, 222, 151)); }
        else { adultStatus.setText("● SETUP REQUIRED"); adultStatus.setTextColor(Color.rgb(255, 126, 101)); }
    }

    private boolean usageAccessEnabled() {
        AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private int parsePositive(EditText field, int fallback) {
        try { return Math.max(1, Integer.parseInt(field.getText().toString())); } catch (Exception e) { return fallback; }
    }
    private String friendly(long millis) {
        long seconds = Math.max(0, millis / 1000), minutes = seconds / 60;
        return minutes > 0 ? minutes + "m " + (seconds % 60) + "s" : seconds + "s";
    }
    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(Color.rgb(244, 240, 250));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD); return view;
    }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(58, 43, 88))); return b; }
    private EditText numberInput(String hint, String value) {
        EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setInputType(InputType.TYPE_CLASS_NUMBER); e.setTextSize(16); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.rgb(150, 142, 170)); e.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(139, 92, 246))); return e;
    }
    private GradientDrawable panel(int color, int radiusDp) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g; }
    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(l, t, r, b); return p;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}

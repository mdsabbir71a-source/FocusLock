package com.focuslock.app;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Build;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(36));
        root.setBackgroundColor(Color.rgb(247, 242, 250));
        scroll.addView(root);

        TextView title = text("FocusLock", 32, true);
        root.addView(title);
        TextView intro = text("Decide now. Scroll less later.", 17, false);
        intro.setTextColor(Color.DKGRAY);
        root.addView(intro, margins(dp(0), dp(4), dp(0), dp(22)));

        status = text("", 16, true);
        status.setPadding(dp(16), dp(14), dp(16), dp(14));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status, margins(0, 0, 0, dp(18)));

        root.addView(text("1. Allow app blocking", 20, true));
        TextView permissionHelp = text("FocusLock needs Usage Access to detect the current app and Display Over Other Apps to show the lock screen. It does not read screen content.", 14, false);
        permissionHelp.setTextColor(Color.DKGRAY);
        root.addView(permissionHelp, margins(0, dp(5), 0, dp(10)));
        Button usage = button("1A. Allow Usage Access");
        usage.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        root.addView(usage, margins(0, 0, 0, dp(8)));
        Button overlay = button("1B. Allow Display Over Apps");
        overlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        root.addView(overlay, margins(0, 0, 0, dp(22)));

        root.addView(text("2. Choose apps to block", 20, true));
        addLaunchableApps(root);

        root.addView(text("3. Set your commitment", 20, true), margins(0, dp(20), 0, dp(8)));
        graceInput = numberInput("Countdown before lock (minutes)", "1");
        durationInput = numberInput("Lock duration (minutes)", "60");
        root.addView(graceInput, margins(0, dp(5), 0, dp(10)));
        root.addView(durationInput, margins(0, 0, 0, dp(14)));

        Button start = button("Start commitment");
        start.setTextSize(17);
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
        long start = System.currentTimeMillis() + grace * 60_000L;
        LockStore.saveSelection(this, selected);
        LockStore.schedule(this, start, start + duration * 60_000L);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        Intent monitor = new Intent(this, FocusMonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(monitor); else startService(monitor);
        Toast.makeText(this, "Commitment started", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        long now = System.currentTimeMillis(), start = LockStore.start(this), end = LockStore.end(this);
        if (now < start) status.setText("Countdown: lock begins in " + friendly(start - now));
        else if (now < end) status.setText("Locked: " + friendly(end - now) + " remaining");
        else status.setText("Ready for a new commitment");
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
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(Color.rgb(35, 32, 36));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD); return view;
    }
    private Button button(String value) { Button b = new Button(this); b.setText(value); b.setAllCaps(false); return b; }
    private EditText numberInput(String hint, String value) {
        EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setInputType(InputType.TYPE_CLASS_NUMBER); e.setTextSize(16); return e;
    }
    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(l, t, r, b); return p;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}

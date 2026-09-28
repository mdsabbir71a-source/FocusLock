package com.focuslock.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.text.InputType;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Full-screen, local-only account and analytics surfaces. They never participate in locking. */
public final class SectionActivity extends Activity {
    public static final String EXTRA_SECTION = "section";
    private WebView view;
    private WebView analyticsView;
    private WebView accountView;
    private boolean account;
    private FrameLayout shell;
    private View nativeNavigation;
    // These bundled pages do not change while the process is alive. Caching
    // their prepared HTML removes repeated asset I/O between sections.
    private static String analyticsHtml;
    private static String accountHtml;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LockStore.pruneUninstalled(this);
        account = "account".equals(getIntent().getStringExtra(EXTRA_SECTION));
        shell = new FrameLayout(this);
        shell.setBackgroundColor(Color.rgb(247, 245, 239));
        setContentView(shell);
        analyticsView = createSectionView();
        accountView = createSectionView();
        view = account ? accountView : analyticsView;
        shell.addView(analyticsView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        shell.addView(accountView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        analyticsView.setVisibility(account ? View.INVISIBLE : View.VISIBLE);
        accountView.setVisibility(account ? View.VISIBLE : View.INVISIBLE);
        refreshNativeNavigation();
        analyticsView.loadDataWithBaseURL("https://focuslock.local/", pageHtml(false), "text/html", "UTF-8", null);
        accountView.loadDataWithBaseURL("https://focuslock.local/", pageHtml(true), "text/html", "UTF-8", null);
    }

    private WebView createSectionView() {
        WebView page = new WebView(this);
        page.setBackgroundColor(Color.rgb(247, 245, 239));
        WebSettings settings = page.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        // Both section pages are bundled in the app. Blocking network loads
        // avoids an unnecessary wait when opening Analytics or Account.
        settings.setBlockNetworkLoads(true);
        page.addJavascriptInterface(new AccountBridge(), "FocusLock");
        page.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView web, String url) { return handle(url); }
            @Override public boolean shouldOverrideUrlLoading(WebView web, WebResourceRequest request) { return handle(request.getUrl().toString()); }
            @Override public void onPageFinished(WebView web, String url) { bind(web); }
        });
        return page;
    }

    private boolean handle(String url) {
        if (url == null || !url.startsWith("focuslock://")) return false;
        if (url.startsWith("focuslock://home")) goHome();
        else if (url.startsWith("focuslock://insights")) switchSection(false);
        else if (url.startsWith("focuslock://account")) switchSection(true);
        return true;
    }

    private void switchSection(boolean showAccount) {
        if (account == showAccount) return;
        WebView previous = view;
        account = showAccount;
        view = account ? accountView : analyticsView;
        previous.setVisibility(View.INVISIBLE);
        view.setVisibility(View.VISIBLE);
        view.setAlpha(.96f);
        view.setTranslationY(dp(4));
        view.animate().cancel();
        view.animate().alpha(1f).translationY(0f).setDuration(120).start();
        refreshNativeNavigation();
        bind(view);
    }

    private String pageHtml(boolean accountPage) {
        String cached = accountPage ? accountHtml : analyticsHtml;
        if (cached != null) return cached;
        String name = accountPage ? "account" : "analytics";
        try (InputStream in = getAssets().open("sections/" + name + ".html"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int count;
            while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
            String html = out.toString("UTF-8").replace("</head>", bridge() + "</head>");
            if (accountPage) accountHtml = html; else analyticsHtml = html;
            return html;
        } catch (Exception ignored) { return "<html><head>" + bridge() + "</head><body></body></html>"; }
    }

    private static String bridge() { return "<style>html,body{width:100%;min-height:100%;-webkit-tap-highlight-color:transparent;-webkit-user-select:none;user-select:none}body{padding:0!important;display:block!important;background:#F7F5EF!important}.phone{width:100vw!important;height:100vh!important;max-width:none!important;border-radius:0!important;box-shadow:none!important}.status,nav{display:none!important}.app{padding-bottom:96px!important}.scroll{padding-bottom:96px!important}[data-action]{transition:transform .14s ease,background .16s ease}[data-action]:active{transform:scale(.985)}</style><script>document.addEventListener('click',function(e){var row=e.target.closest('[data-action]');if(row&&window.FocusLock){window.FocusLock.perform(row.dataset.action);}});window.setFocusLockAccount=function(email,provider){var name=document.querySelector('.profile strong'),detail=document.querySelector('.profile span'),avatar=document.querySelector('.avatar');if(name)name.textContent=email||'FocusLock user';if(detail)detail.textContent=provider==='google'?'Google account':'Signed in securely';if(avatar)avatar.textContent=(email||'F').charAt(0).toUpperCase();};</script>"; }
    private void bind(WebView target) {
        if (target == null) return;
        target.evaluateJavascript("if(window.setFocusLockData){window.setFocusLockData(" + pauseEventsJson() + "," + appsJson() + ");}", null);
        target.evaluateJavascript("if(window.setFocusLockAccount){window.setFocusLockAccount("
                + json(AccountStore.email(this)) + "," + json(AccountStore.provider(this)) + ");}", null);
    }

    /** Bridges the polished bundled Account page to real, native account actions. */
    private final class AccountBridge {
        @JavascriptInterface public void perform(String action) {
            runOnUiThread(() -> performAccountAction(action));
        }
    }

    private void performAccountAction(String action) {
        if (!account || action == null) return;
        if ("details".equals(action)) showAccountDetails();
        else if ("notifications".equals(action)) openNotificationSettings();
        else if ("faq".equals(action)) showFaq();
        else if ("privacy".equals(action)) openWebsitePath("/privacy.html");
        else if ("terms".equals(action)) openWebsitePath("/terms.html");
        else if ("support".equals(action)) showSupport();
        else if ("password".equals(action)) showChangePassword();
        else if ("signout".equals(action)) confirmSignOut();
        else if ("delete".equals(action)) confirmDeleteAccount();
    }

    private void showAccountDetails() {
        SecureSessionStore.Session session = SecureSessionStore.get(this);
        String email = AccountStore.email(this);
        String provider = AccountStore.provider(this);
        String accountId = session == null || session.userId.length() < 8
                ? "Unavailable" : session.userId.substring(0, 8) + "…";
        new AlertDialog.Builder(this).setTitle("Personal details")
                .setMessage("Email\n" + (email.isEmpty() ? "Not available on this device" : email)
                        + "\n\nSign-in method\n" + ("google".equalsIgnoreCase(provider) ? "Google" : "Email")
                        + "\n\nAccount ID\n" + accountId
                        + "\n\nApp version\n" + BuildConfig.VERSION_NAME)
                .setPositiveButton("Done", null).show();
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(Build.VERSION.SDK_INT >= 26
                    ? Settings.ACTION_APP_NOTIFICATION_SETTINGS : Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            if (Build.VERSION.SDK_INT >= 26) intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            else intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception error) { toast("Could not open notification settings."); }
    }

    private void showSupport() {
        new AlertDialog.Builder(this).setTitle("Help & support")
                .setMessage("Choose apps, set a use limit, and select a lock time. Only selected apps count toward your limit.\n\nIf protection stops, open Home and complete any permission repair prompts.")
                .setPositiveButton("Email support", (dialog, which) -> sendSupportEmail())
                .setNegativeButton("Done", null).show();
    }

    private void showChangePassword() {
        if ("google".equalsIgnoreCase(AccountStore.provider(this))) {
            String email = AccountStore.email(this);
            if (email.isEmpty()) { toast("Your account email is not available. Please sign in again."); return; }
            new AlertDialog.Builder(this).setTitle("Set a FocusLock password?")
                    .setMessage("We will email a secure link to " + email + ". It lets you set a FocusLock password without changing your Google password.")
                    .setPositiveButton("Send email", (dialog, which) -> SupabaseApi.requestPasswordReset(email, (sent, error) -> {
                        if (Boolean.TRUE.equals(sent)) toast("FocusLock sent a secure password setup link.");
                        else toast(error == null ? "Could not send the password setup email." : error);
                    }))
                    .setNegativeButton("Cancel", null).show();
            return;
        }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        fields.setPadding(padding, dp(4), padding, 0);
        EditText current = passwordField("Current password");
        EditText fresh = passwordField("New password (8+ characters)");
        EditText confirm = passwordField("Confirm new password");
        fields.addView(current);
        fields.addView(fresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fields.addView(confirm, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Change password").setView(fields)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String oldPassword = current.getText().toString();
            String password = fresh.getText().toString();
            if (oldPassword.isEmpty()) { current.setError("Enter your current password"); return; }
            if (password.length() < 8) { fresh.setError("Use at least 8 characters"); return; }
            if (!password.equals(confirm.getText().toString())) { confirm.setError("Passwords do not match"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            SupabaseApi.updatePassword(this, oldPassword, password, (saved, error) -> {
                if (!Boolean.TRUE.equals(saved)) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    current.setError(error == null ? "Could not update password" : error);
                } else { dialog.dismiss(); toast("Password updated."); }
            });
        }));
        dialog.show();
    }

    private EditText passwordField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setPadding(0, dp(10), 0, dp(10));
        return field;
    }

    private void showChangeEmail() {
        EditText field = new EditText(this);
        field.setHint("New email address");
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        int padding = dp(18);
        field.setPadding(padding, dp(12), padding, dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Change email")
                .setMessage("For security, confirmation emails may be sent to both addresses.")
                .setView(field).setPositiveButton("Send confirmation", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = field.getText().toString().trim();
            if (!email.contains("@")) { field.setError("Enter a valid email address"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            SupabaseApi.updateEmail(this, email, (saved, error) -> {
                if (Boolean.TRUE.equals(saved)) { dialog.dismiss(); toast("Check your email to confirm the change."); }
                else { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); field.setError(error == null ? "Could not change email" : error); }
            });
        }));
        dialog.show();
    }

    private void showFaq() {
        LinearLayout answers = new LinearLayout(this);
        answers.setOrientation(LinearLayout.VERTICAL);
        answers.setPadding(dp(20), dp(4), dp(20), dp(8));
        faqRow(answers, "How does FocusLock work?", "Choose the apps that distract you, set a usage limit, then choose how long they stay locked after the limit is reached.");
        faqRow(answers, "What counts toward my limit?", "Only time spent in the apps you selected counts. Time in other apps does not affect the timer.");
        faqRow(answers, "Why did an app not lock?", "FocusLock needs Usage Access, Accessibility, and Display over other apps. Return to Home and complete any permission repair prompt.");
        faqRow(answers, "Can I change my timers?", "Yes. Change either timer on Home, then tap Apply changes.");
        faqRow(answers, "What does Lock FocusLock during a pause do?", "When enabled, FocusLock itself is unavailable while one of your selected apps is locked. You can turn it off on Home.");
        faqRow(answers, "Does FocusLock use a VPN or read my browsing?", "No. FocusLock does not use a VPN and does not filter or read web browsing.");
        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(answers, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle("FocusLock FAQ").setView(scroll).setPositiveButton("Done", null).show();
    }

    private void faqRow(LinearLayout parent, String question, String answer) {
        TextView title = new TextView(this);
        title.setText(question);
        title.setTextColor(Color.rgb(19, 42, 28));
        title.setTextSize(14);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        parent.addView(title, topMargin(14));
        TextView copy = new TextView(this);
        copy.setText(answer);
        copy.setTextColor(Color.rgb(91, 107, 95));
        copy.setTextSize(13);
        copy.setLineSpacing(0, 1.16f);
        parent.addView(copy, topMargin(3));
    }

    private LinearLayout.LayoutParams topMargin(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(value);
        return params;
    }

    private void openWebsitePath(String path) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PUBLIC_SITE_URL + path))); }
        catch (Exception error) { toast("Could not open the FocusLock website."); }
    }

    private void confirmDeleteAccount() {
        EditText confirmation = new EditText(this);
        confirmation.setHint("Type DELETE");
        confirmation.setSingleLine(true);
        int padding = dp(18);
        confirmation.setPadding(padding, dp(12), padding, dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Permanently delete account?")
                .setMessage("This permanently deletes your FocusLock account and synced data. Type DELETE to confirm.")
                .setView(confirmation).setPositiveButton("Delete permanently", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!"DELETE".equals(confirmation.getText().toString().trim())) { confirmation.setError("Type DELETE exactly"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            SupabaseApi.deleteAccount(this, (deleted, error) -> {
                if (!Boolean.TRUE.equals(deleted)) { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); confirmation.setError(error == null ? "Could not delete account" : error); return; }
                stopService(new Intent(this, FocusMonitorService.class));
                LocalDataStore.clearAfterAccountDeletion(this);
                startActivity(new Intent(this, AuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            });
        }));
        dialog.show();
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this).setTitle("Sign out?")
                .setMessage("You will need to sign in again to use FocusLock on this device.")
                .setPositiveButton("Sign out", (dialog, which) -> {
                    SupabaseApi.logout(this);
                    startActivity(new Intent(this, AuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                    finish();
                }).setNegativeButton("Cancel", null).show();
    }

    private void sendSupportEmail() {
        Intent email = new Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:" + BuildConfig.SUPPORT_EMAIL));
        email.putExtra(Intent.EXTRA_SUBJECT, "FocusLock support — Android " + BuildConfig.VERSION_NAME);
        try { startActivity(email); } catch (Exception error) { toast("Email support at " + BuildConfig.SUPPORT_EMAIL); }
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    /** Native navigation stays above the WebView, so it can never scroll away
     * or be covered by Android's system navigation area. */
    private void refreshNativeNavigation() {
        if (shell == null) return;
        if (nativeNavigation != null) shell.removeView(nativeNavigation);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        // Match Home's bar exactly: same top/bottom spacing and icon baseline.
        nav.setPadding(dp(10), dp(8), dp(10), dp(10));
        nav.setBackgroundColor(Color.rgb(247, 245, 239));
        nav.addView(navButton(R.drawable.ic_nav_home, "Home", false, this::goHome),
                new LinearLayout.LayoutParams(0, dp(52), 1f));
        nav.addView(navButton(R.drawable.ic_nav_analytics, "Analytics", !account, v -> {
            switchSection(false);
        }), new LinearLayout.LayoutParams(0, dp(52), 1f));
        nav.addView(navButton(R.drawable.ic_nav_account, "Account", account, v -> {
            switchSection(true);
        }), new LinearLayout.LayoutParams(0, dp(52), 1f));
        nativeNavigation = nav;
        shell.addView(nav, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    private View navButton(int iconResource, String label, boolean active, View.OnClickListener click) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(4), dp(3), dp(4), dp(3));
        item.setBackgroundColor(Color.TRANSPARENT);
        item.setForeground(null);
        int tint = active ? Color.rgb(52, 116, 76) : Color.rgb(107, 114, 128);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(tint);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        item.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(25)));
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextColor(tint);
        text.setTextSize(10);
        text.setGravity(Gravity.CENTER);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        item.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
        item.setContentDescription(label);
        item.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                item.animate().cancel();
                item.animate().scaleX(.965f).scaleY(.965f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                item.animate().cancel();
                item.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
            return false;
        });
        item.setOnClickListener(click);
        return item;
    }

    private void goHome(View ignored) { goHome(); }
    private void goHome() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
        overridePendingTransition(0, 0);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private String pauseEventsJson() {
        StringBuilder json = new StringBuilder("[");
        for (FocusInsights.Pause pause : FocusInsights.pauses(this)) {
            if (json.length() > 1) json.append(',');
            json.append("{\"t\":").append(pause.timeMs).append(",\"p\":")
                    .append(json(pause.packageName)).append(",\"s\":").append(pause.savedMs).append('}');
        }
        return json.append(']').toString();
    }

    private String appsJson() {
        PackageManager manager = getPackageManager();
        Set<String> selected = LockStore.packages(this);
        ArrayList<String> packages = new ArrayList<>(selected);
        Collections.sort(packages);
        StringBuilder json = new StringBuilder("[");
        for (String packageName : packages) {
            try {
                if (json.length() > 1) json.append(',');
                CharSequence label = manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0));
                Drawable icon = manager.getApplicationIcon(packageName);
                json.append("{\"p\":").append(json(packageName)).append(",\"n\":")
                        .append(json(label == null ? packageName : label.toString())).append(",\"i\":")
                        .append(json(iconData(icon))).append('}');
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return json.append(']').toString();
    }

    private static String iconData(Drawable drawable) {
        int size = 48;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        return "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private static String json(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\"";
    }
    @Override public void onBackPressed() { startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); overridePendingTransition(0, 0); }
    @Override protected void onDestroy() {
        if (analyticsView != null) analyticsView.destroy();
        if (accountView != null) accountView.destroy();
        super.onDestroy();
    }
}

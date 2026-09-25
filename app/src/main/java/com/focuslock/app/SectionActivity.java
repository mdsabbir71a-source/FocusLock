package com.focuslock.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private boolean account;
    private FrameLayout shell;
    private View nativeNavigation;
    // These bundled pages do not change while the process is alive. Caching
    // their prepared HTML removes repeated asset I/O between sections.
    private static String analyticsHtml;
    private static String accountHtml;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        account = "account".equals(getIntent().getStringExtra(EXTRA_SECTION));
        view = new WebView(this);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        // Both section pages are bundled in the app. Blocking network loads
        // avoids an unnecessary wait when opening Analytics or Account.
        settings.setBlockNetworkLoads(true);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView web, String url) { return handle(url); }
            @Override public boolean shouldOverrideUrlLoading(WebView web, WebResourceRequest request) { return handle(request.getUrl().toString()); }
            @Override public void onPageFinished(WebView web, String url) { bind(); }
        });
        shell = new FrameLayout(this);
        shell.setBackgroundColor(Color.rgb(247, 245, 239));
        shell.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refreshNativeNavigation();
        setContentView(shell);
        view.loadDataWithBaseURL("https://focuslock.local/", pageHtml(), "text/html", "UTF-8", null);
    }

    private boolean handle(String url) {
        if (url == null || !url.startsWith("focuslock://")) return false;
        if (url.startsWith("focuslock://home")) goHome();
        else if (url.startsWith("focuslock://insights")) { if (account) { account=false; refreshNativeNavigation(); reload(); } }
        else if (url.startsWith("focuslock://account")) { if (!account) { account=true; refreshNativeNavigation(); reload(); } }
        return true;
    }

    private void reload() { view.loadDataWithBaseURL("https://focuslock.local/", pageHtml(), "text/html", "UTF-8", null); }
    private String pageHtml() {
        String cached = account ? accountHtml : analyticsHtml;
        if (cached != null) return cached;
        String name = account ? "account" : "analytics";
        try (InputStream in = getAssets().open("sections/" + name + ".html"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int count;
            while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
            String html = out.toString("UTF-8").replace("</head>", bridge() + "</head>");
            if (account) accountHtml = html; else analyticsHtml = html;
            return html;
        } catch (Exception ignored) { return "<html><head>" + bridge() + "</head><body></body></html>"; }
    }

    private static String bridge() { return "<style>html,body{width:100%;min-height:100%;-webkit-tap-highlight-color:transparent;-webkit-user-select:none;user-select:none}body{padding:0!important;display:block!important;background:#F7F5EF!important}.phone{width:100vw!important;height:100vh!important;max-width:none!important;border-radius:0!important;box-shadow:none!important}.status,nav{display:none!important}.app{padding-bottom:96px!important}.scroll{padding-bottom:96px!important}</style>"; }
    private void bind() {
        if (view == null) return;
        view.evaluateJavascript("if(window.setFocusLockData){window.setFocusLockData(" + pauseEventsJson() + "," + appsJson() + ");}", null);
    }

    /** Native navigation stays above the WebView, so it can never scroll away
     * or be covered by Android's system navigation area. */
    private void refreshNativeNavigation() {
        if (shell == null) return;
        if (nativeNavigation != null) shell.removeView(nativeNavigation);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(8), dp(10), dp(34));
        nav.setBackgroundColor(Color.rgb(247, 245, 239));
        nav.addView(navButton(R.drawable.ic_nav_home, "Home", false, this::goHome),
                new LinearLayout.LayoutParams(0, dp(52), 1f));
        nav.addView(navButton(R.drawable.ic_nav_analytics, "Analytics", !account, v -> {
            if (account) { account = false; refreshNativeNavigation(); reload(); }
        }), new LinearLayout.LayoutParams(0, dp(52), 1f));
        nav.addView(navButton(R.drawable.ic_nav_account, "Account", account, v -> {
            if (!account) { account = true; refreshNativeNavigation(); reload(); }
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
    @Override protected void onDestroy() { if (view != null) view.destroy(); super.onDestroy(); }
}

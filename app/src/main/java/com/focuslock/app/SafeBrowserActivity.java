package com.focuslock.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** A setup-free browser that applies local adult-domain blocking and strict search parameters. */
public class SafeBrowserActivity extends Activity {
    private static final int INK = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(107, 114, 128);
    private static final int GREEN = Color.rgb(45, 130, 78);
    private static final int PALE = Color.rgb(240, 248, 239);
    private static final int BORDER = Color.rgb(220, 233, 220);

    private static final Set<String> BLOCKED_DOMAINS = new HashSet<>(Arrays.asList(
            "pornhub.com", "xvideos.com", "xnxx.com", "redtube.com", "youporn.com",
            "tube8.com", "spankbang.com", "xhamster.com", "beeg.com", "tnaflix.com",
            "hclips.com", "drtuber.com", "porntrex.com", "porn.com", "sex.com",
            "brazzers.com", "bangbros.com", "realitykings.com", "naughtyamerica.com",
            "onlyfans.com", "fansly.com", "chaturbate.com", "stripchat.com", "camsoda.com",
            "livejasmin.com", "bongacams.com", "myfreecams.com", "cam4.com", "flirt4free.com",
            "hentaihaven.xxx", "nhentai.net", "rule34.xxx", "e621.net", "fapello.com"
    ));

    private static final String[] BLOCKED_HOST_PARTS = {
            "porn", "xxx", "adultvideo", "sexcam", "livecamsex", "hentai", "rule34",
            "xvideos", "xnxx", "redtube", "youporn", "xhamster", "chaturbate", "stripchat"
    };

    private WebView webView;
    private EditText address;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        configureBrowser();
        showHome();
    }

    private View buildUi() {
        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(248, 251, 246));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(13), dp(12), dp(10));
        TextView title = text("FocusLock Browser", 16, INK, true);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView active = text("●  SAFE", 10, GREEN, true);
        active.setPadding(dp(10), dp(6), dp(10), dp(6));
        active.setBackground(shape(PALE, BORDER, 16));
        top.addView(active);
        Button close = button("×", Color.WHITE, MUTED);
        close.setTextSize(22);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeLp.leftMargin = dp(6);
        top.addView(close, closeLp);
        root.addView(top, matchWrap());

        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(12), dp(4), dp(12), dp(9));
        Button back = button("‹", PALE, INK);
        back.setTextSize(24);
        back.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); else showHome(); });
        nav.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44)));
        Button home = button("⌂", PALE, INK);
        home.setTextSize(19);
        home.setOnClickListener(v -> showHome());
        LinearLayout.LayoutParams homeLp = new LinearLayout.LayoutParams(dp(42), dp(44));
        homeLp.leftMargin = dp(5);
        nav.addView(home, homeLp);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(13);
        address.setTextColor(INK);
        address.setHint("Search or enter a website");
        address.setHintTextColor(Color.rgb(156, 163, 175));
        address.setPadding(dp(13), dp(10), dp(13), dp(10));
        address.setBackground(shape(Color.WHITE, BORDER, 18));
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(address.getText().toString());
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams addressLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        addressLp.leftMargin = dp(6);
        nav.addView(address, addressLp);
        Button go = button("Go", GREEN, Color.WHITE);
        go.setOnClickListener(v -> navigate(address.getText().toString()));
        LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(dp(54), dp(44));
        goLp.leftMargin = dp(6);
        nav.addView(go, goLp);
        root.addView(nav, matchWrap());

        TextView notice = text("Adult domains are blocked automatically inside this browser", 10, GREEN, true);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(10), dp(7), dp(10), dp(7));
        notice.setBackgroundColor(PALE);
        root.addView(notice, matchWrap());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void configureBrowser() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSafeBrowsingEnabled(true);
        WebView.setWebContentsDebuggingEnabled(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(view, request.getUrl().toString());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(view, url);
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isBlocked(request.getUrl())) {
                    return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (url != null && !url.startsWith("data:") && !url.contains("focuslock.local")) address.setText(url);
            }
        });
    }

    private boolean handleNavigation(WebView view, String rawUrl) {
        Uri uri;
        try { uri = Uri.parse(rawUrl); }
        catch (Exception e) { return true; }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return true;
        if ("focuslock.local".equalsIgnoreCase(uri.getHost())) {
            view.post(SafeBrowserActivity.this::showHome);
            return true;
        }
        if (isBlocked(uri)) {
            view.post(this::showBlockedPage);
            return true;
        }
        String safer = enforceSafeSearch(rawUrl);
        if (!safer.equals(rawUrl)) {
            view.post(() -> view.loadUrl(safer));
            return true;
        }
        return false;
    }

    private void navigate(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) { showHome(); return; }
        String url;
        if (value.contains(" ") || (!value.contains(".") && !value.startsWith("http"))) {
            url = "https://www.google.com/search?safe=active&q=" + Uri.encode(value);
        } else {
            url = value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") ? value : "https://" + value;
        }
        Uri uri;
        try { uri = Uri.parse(url); }
        catch (Exception e) { toast("That address is not valid."); return; }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            toast("Only normal web addresses can be opened.");
            return;
        }
        if (isBlocked(uri)) { showBlockedPage(); return; }
        webView.loadUrl(enforceSafeSearch(url));
    }

    private boolean isBlocked(Uri uri) {
        String host = uri == null ? null : uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        if (host.startsWith("www.")) host = host.substring(4);
        if (host.matches("^(?:\\d{1,3}\\.){3}\\d{1,3}$") || host.contains(":")) return true;
        for (String domain : BLOCKED_DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        for (String part : BLOCKED_HOST_PARTS) if (host.contains(part)) return true;
        return false;
    }

    private String enforceSafeSearch(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (host.contains("google.")) return replaceQueryParameter(uri, "safe", "active");
            if (host.endsWith("bing.com")) return replaceQueryParameter(uri, "adlt", "strict");
            if (host.endsWith("duckduckgo.com")) return replaceQueryParameter(uri, "kp", "1");
            if (host.endsWith("search.yahoo.com")) return replaceQueryParameter(uri, "vm", "r");
        } catch (Exception ignored) {}
        return url;
    }

    private String replaceQueryParameter(Uri uri, String key, String value) {
        Uri.Builder builder = uri.buildUpon().clearQuery();
        for (String name : uri.getQueryParameterNames()) {
            if (key.equals(name)) continue;
            for (String item : uri.getQueryParameters(name)) builder.appendQueryParameter(name, item);
        }
        builder.appendQueryParameter(key, value);
        return builder.build().toString();
    }

    private void showHome() {
        address.setText("");
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#f8fbf6;color:#111827;padding:32px 22px;text-align:center}"
                + ".leaf{font-size:44px}.card{background:white;border:1px solid #dce9dc;border-radius:22px;padding:24px 18px;margin-top:22px}"
                + "h1{font-size:25px;margin:10px 0 6px}p{color:#6b7280;line-height:1.5;font-size:14px}"
                + "input{box-sizing:border-box;width:100%;padding:14px;border:1px solid #dce9dc;border-radius:16px;font-size:15px}"
                + "button{width:100%;margin-top:10px;padding:14px;border:0;border-radius:16px;background:#2d824e;color:white;font-weight:bold}</style></head>"
                + "<body><div class='leaf'>🌿</div><h1>A calmer way to browse</h1><p>Strict search filtering and adult-domain blocking are already on.</p>"
                + "<div class='card'><form action='https://www.google.com/search' method='get'><input type='hidden' name='safe' value='active'>"
                + "<input name='q' placeholder='Search safely'><button type='submit'>Search the web</button></form></div>"
                + "<p>Protection applies inside FocusLock Browser only.</p></body></html>";
        webView.loadDataWithBaseURL("https://focuslock.local/home", html, "text/html", "UTF-8", null);
    }

    private void showBlockedPage() {
        webView.stopLoading();
        address.setText("");
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#f8fbf6;color:#111827;text-align:center;padding:60px 26px}"
                + ".circle{width:94px;height:94px;line-height:94px;margin:auto;border-radius:50%;background:#f0f8ef;font-size:42px}"
                + "h1{font-size:25px;margin:24px 0 8px}p{color:#6b7280;line-height:1.5}button{padding:13px 22px;border:0;border-radius:18px;background:#2d824e;color:white;font-weight:bold}</style></head>"
                + "<body><div class='circle'>🍃</div><h1>This page is blocked</h1><p>FocusLock protected this browsing session. Take a breath and choose something better.</p>"
                + "<button onclick=\"location.href='https://focuslock.local/home'\">Return home</button></body></html>";
        webView.loadDataWithBaseURL("https://focuslock.local/blocked", html, "text/html", "UTF-8", null);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearFormData();
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }

    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }
    private Button button(String label, int background, int foreground) {
        Button button = new Button(this); button.setText(label); button.setTextSize(11); button.setTextColor(foreground); button.setAllCaps(false);
        button.setPadding(dp(8), dp(8), dp(8), dp(8)); button.setBackground(shape(background, background, 18));
        return button;
    }
    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), stroke); return drawable;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}

package com.focuslock.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Full-screen, local-only account and analytics surfaces. They never participate in locking. */
public final class SectionActivity extends Activity {
    public static final String EXTRA_SECTION = "section";
    private WebView view;
    private boolean account;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        account = "account".equals(getIntent().getStringExtra(EXTRA_SECTION));
        view = new WebView(this);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView web, String url) { return handle(url); }
            @Override public boolean shouldOverrideUrlLoading(WebView web, WebResourceRequest request) { return handle(request.getUrl().toString()); }
            @Override public void onPageFinished(WebView web, String url) { web.postDelayed(SectionActivity.this::bind, 70L); }
        });
        setContentView(view);
        view.loadDataWithBaseURL("https://focuslock.local/", pageHtml(), "text/html", "UTF-8", null);
    }

    private boolean handle(String url) {
        if (url == null || !url.startsWith("focuslock://")) return false;
        if (url.startsWith("focuslock://home")) { startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); }
        else if (url.startsWith("focuslock://insights")) { if (account) { account=false; reload(); } }
        else if (url.startsWith("focuslock://account")) { if (!account) { account=true; reload(); } }
        return true;
    }

    private void reload() { view.loadDataWithBaseURL("https://focuslock.local/", pageHtml(), "text/html", "UTF-8", null); }
    private String pageHtml() {
        String name = account ? "account" : "analytics";
        try (InputStream in = getAssets().open("sections/" + name + ".html"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int count;
            while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
            return out.toString("UTF-8").replace("</head>", bridge() + "</head>");
        } catch (Exception ignored) { return "<html><head>" + bridge() + "</head><body></body></html>"; }
    }

    private static String bridge() { return "<style>html,body{width:100%;height:100%;overflow:hidden!important}body{padding:0!important;display:block!important;background:#F7F5EF!important}.phone{width:100vw!important;height:100vh!important;max-width:none!important;border-radius:0!important;box-shadow:none!important}.status{display:none!important}</style><script>window.focusLockSection=function(email,saved,pauses,today,previous){var p=document.querySelector('.profile');if(p){var strong=p.querySelector('strong'),span=p.querySelector('span');if(strong)strong.textContent=email?email.split('@')[0]:'FocusLock user';if(span)span.textContent=email||'Signed in securely';}var nums=document.querySelectorAll('.hero-stats .num');if(nums.length){nums[0].textContent=saved;nums[1].textContent=pauses;nums[2].textContent=today;}var sub=document.querySelector('.sub');if(sub)sub.textContent=saved==='0m'?'Your focus story starts today.':'You kept '+saved+' for yourself.';var nav=document.querySelectorAll('.navitem,.nav>div');for(var i=0;i<nav.length;i++){(function(n,index){n.addEventListener('click',function(){location.href=index===0?'focuslock://home':index===1?'focuslock://insights':'focuslock://account';});})(nav[i],i);}};</script>"; }
    private void bind() {
        if (view == null) return;
        FocusInsights.Snapshot s = FocusInsights.snapshot(this);
        String email = AccountStore.email(this);
        String today = friendly(s.todayScreenMs);
        view.evaluateJavascript("if(window.focusLockSection){window.focusLockSection(" + js(email) + "," + js(friendly(s.focusSavedMs)) + "," + js(String.valueOf(s.pauses)) + "," + js(today) + "," + js(friendly(s.previousScreenMs)) + ");}", null);
    }
    private static String friendly(long ms) { long minutes=Math.max(0L,ms)/60000L; return minutes>=60?(minutes/60)+"h "+(minutes%60)+"m":minutes+"m"; }
    private static String js(String s) { return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'"; }
    @Override public void onBackPressed() { startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); }
    @Override protected void onDestroy() { if (view != null) view.destroy(); super.onDestroy(); }
}

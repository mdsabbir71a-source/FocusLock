package com.focuslock.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class BlockActivity extends Activity {
    private static final String[] CARDS={"dunes","grove","horizon","nightfall","rainfall","ridge","seedling","tide"};
    private static final String[] REMINDERS={"Take a breath. This urge will pass.","A quiet minute can protect your afternoon.","Choose where your attention grows.","Slow down. Let the moment pass.","Your time is worth protecting.","Small pauses build strong habits.","Let your earlier decision support you.","Look away. Relax your shoulders.","Inhale, exhale, begin again.","Spend your attention with intention.","One protected moment can reset your day."};
    private static volatile boolean visible;
    private String blockedPackage; private WebView card; private CountDownTimer timer; private long duration; private boolean ready;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if(!AccessStore.isAllowed(this)||!RemoteConfigStore.appBlockingEnabled(this)){finish();return;}
        blockedPackage=getIntent().getStringExtra("blocked_package");
        if(blockedPackage==null||!LockStore.isLocked(this,blockedPackage)){finish();return;}
        setContentView(buildCard());
        startTimer();
    }
    public static boolean isVisible(){return visible;}
    @Override protected void onResume(){super.onResume();visible=true;GardenLockOverlay.hide();}
    @Override protected void onPause(){visible=false;super.onPause();}
    private WebView buildCard(){
        card=new WebView(this); WebSettings s=card.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(false); s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setBlockNetworkLoads(true);
        card.setBackgroundColor(android.graphics.Color.rgb(247,245,239));
        card.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){if(url!=null&&url.startsWith("focuslock://home")){goHome();return true;}return false;}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return shouldOverrideUrlLoading(v,r.getUrl().toString());}
            @Override public void onPageFinished(WebView v,String url){ready=true;update(remaining());}
        });
        card.loadDataWithBaseURL("https://focuslock.local/",pageHtml(CARDS[chooseTheme()]),"text/html","UTF-8",null);
        return card;
    }
    private int chooseTheme(){
        long session=LockStore.lockedUntil(this,blockedPackage); android.content.SharedPreferences p=getSharedPreferences("focuslock_lock_card_themes",MODE_PRIVATE); String key="card:"+blockedPackage;
        if(p.getLong(key+":session",Long.MIN_VALUE)==session)return p.getInt(key+":theme",0);
        int next=(p.getInt("last_theme",-1)+1)%CARDS.length; p.edit().putLong(key+":session",session).putInt(key+":theme",next).putInt("last_theme",next).apply(); return next;
    }
    private String pageHtml(String name){
        try(InputStream in=getAssets().open("lockcards/"+name+".html");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8")+bridge();}
        catch(Exception e){return "<html><body style='background:#F7F5EF'></body></html>"+bridge();}
    }
    private String bridge(){return "<style>html,body{width:100%;height:100%;overflow:hidden!important}body{padding:0!important;display:block!important;background:#F7F5EF!important}.phone{width:100vw!important;height:100vh!important;max-width:none!important;border-radius:0!important;box-shadow:none!important}.status{display:none!important}</style><script>window.focusLockSet=function(a,t,q){var x=document.querySelector('.appline');if(x){Array.prototype.forEach.call(x.childNodes,function(n){if(n.nodeType===3)n.nodeValue=' '+a+' is paused';});}var c=document.querySelector('.timer-clock');if(c)c.textContent=t;var z=document.querySelector('.quote');if(z)z.textContent=q;var b=document.querySelector('.btn');if(b){b.href='focuslock://home';b.onclick=function(e){e.preventDefault();location.href='focuslock://home';};}};</script>";}
    private void startTimer(){duration=Math.max(1L,remaining());update(duration);timer=new CountDownTimer(duration,1000){@Override public void onTick(long l){update(l);}@Override public void onFinish(){goHome();}}.start();}
    private long remaining(){return Math.max(0L,LockStore.lockedUntil(this,blockedPackage)-System.currentTimeMillis());}
    private void update(long ms){if(!ready||card==null)return;String[] r=RemoteConfigStore.reminders(this,REMINDERS);String quote=r[LockStore.nextReminderIndex(this,r.length)];card.evaluateJavascript("window.focusLockSet("+js(appName())+","+js(format(ms))+","+js(quote)+");",null);}
    private String appName(){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(blockedPackage,0)).toString();}catch(PackageManager.NameNotFoundException e){return "This app";}}
    private static String js(String s){return "'"+s.replace("\\","\\\\").replace("'","\\'").replace("\n"," ")+"'";}
    private static String format(long ms){long s=Math.max(0,(ms+999)/1000);return String.format(java.util.Locale.US,"%02d:%02d",s/60,s%60);}
    private void goHome(){startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
    @Override public void onBackPressed(){goHome();}
    @Override protected void onDestroy(){visible=false;if(timer!=null)timer.cancel();if(card!=null)card.destroy();super.onDestroy();}
}
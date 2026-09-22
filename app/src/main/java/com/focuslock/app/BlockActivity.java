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
    private static final String CARD="grove";
    private static final String[] REMINDERS={"Take a breath. This urge will pass.","A quiet minute can protect your afternoon.","Choose where your attention grows.","Slow down. Let the moment pass.","Your time is worth protecting.","Small pauses build strong habits.","Let your earlier decision support you.","Look away. Relax your shoulders.","Inhale, exhale, begin again.","Spend your attention with intention.","One protected moment can reset your day."};
    private static volatile boolean visible;
    private String blockedPackage, reminder; private WebView card; private CountDownTimer timer; private boolean ready;

    @Override protected void onCreate(Bundle state){
        super.onCreate();
        if(!AccessStore.isAllowed(this)||!RemoteConfigStore.appBlockingEnabled(this)){finish();return;}
        blockedPackage=getIntent().getStringExtra("blocked_package");
        if(blockedPackage==null||!LockStore.isLocked(this,blockedPackage)){finish();return;}
        reminder=chooseReminder(); setContentView(createCard()); startTimer();
    }
    public static boolean isVisible(){return visible;}
    @Override protected void onResume(){super.onResume();visible=true;GardenLockOverlay.hide();}
    @Override protected void onPause(){visible=false;super.onPause();}

    private WebView createCard(){
        card=new WebView(this);WebSettings settings=card.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        card.setBackgroundColor(android.graphics.Color.rgb(247,245,239));
        card.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){if(url!=null&&url.startsWith("focuslock://home")){goHome();return true;}return false;}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return shouldOverrideUrlLoading(view,request.getUrl().toString());}
            @Override public void onPageFinished(WebView view,String url){view.postDelayed(()->{ready=true;bindLiveCard();},80L);}
        });
        card.loadDataWithBaseURL("https://focuslock.local/",pageHtml(),"text/html","UTF-8",null);return card;
    }
    private String pageHtml(){
        try(InputStream in=getAssets().open("lockcards/"+CARD+".html");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
            return out.toString("UTF-8").replace("</head>",bridge()+"</head>");
        }catch(Exception ignored){return "<html><head>"+bridge()+"</head><body></body></html>";}
    }
    private static String bridge(){return "<style>html,body{width:100%;height:100%;overflow:hidden!important}body{padding:0!important;display:block!important;background:#F7F5EF!important}.phone{width:100vw!important;height:100vh!important;max-width:none!important;border-radius:0!important;box-shadow:none!important}.status{display:none!important}</style><script>window.focusLockStart=function(end,app,quote){var x=document.querySelector('.appline');if(x){Array.prototype.forEach.call(x.childNodes,function(n){if(n.nodeType===3)n.nodeValue=' '+app+' is paused';});}var q=document.querySelector('.quote');if(q)q.textContent=quote;var b=document.querySelector('.btn');if(b){b.href='focuslock://home';b.onclick=function(e){e.preventDefault();location.href='focuslock://home';};}function draw(){var left=Math.max(0,end-Date.now()),s=Math.ceil(left/1000),clock=document.querySelector('.t-clock');if(clock)clock.textContent=String(Math.floor(s/60)).padStart(2,'0')+':'+String(s%60).padStart(2,'0');}draw();if(window.focusLockTicker)clearInterval(window.focusLockTicker);window.focusLockTicker=setInterval(draw,250);};</script>";}
    private void bindLiveCard(){if(!ready||card==null)return;card.evaluateJavascript("if(window.focusLockStart){window.focusLockStart("+LockStore.lockedUntil(this,blockedPackage)+","+js(appName())+","+js(reminder)+");}",null);}
    private void startTimer(){long remaining=Math.max(1L,LockStore.lockedUntil(this,blockedPackage)-System.currentTimeMillis());timer=new CountDownTimer(remaining,1000){@Override public void onTick(long ignored){}@Override public void onFinish(){goHome();}}.start();}
    private String chooseReminder(){long session=LockStore.lockedUntil(this,blockedPackage);android.content.SharedPreferences p=getSharedPreferences("focuslock_lock_card_reminders",MODE_PRIVATE);String key="reminder:"+blockedPackage;String[] list=RemoteConfigStore.reminders(this,REMINDERS);if(p.getLong(key+":session",Long.MIN_VALUE)==session)return list[Math.max(0,Math.min(list.length-1,p.getInt(key+":index",0)))];int next=(p.getInt("last_reminder",-1)+1)%list.length;p.edit().putLong(key+":session",session).putInt(key+":index",next).putInt("last_reminder",next).apply();return list[next];}
    private String appName(){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(blockedPackage,0)).toString();}catch(PackageManager.NameNotFoundException e){return "This app";}}
    private static String js(String s){return "'"+s.replace("\\","\\\\").replace("'","\\'").replace("\n"," ")+"'";}
    private void goHome(){startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
    @Override public void onBackPressed(){goHome();}
    @Override protected void onDestroy(){visible=false;if(timer!=null)timer.cancel();if(card!=null)card.destroy();super.onDestroy();}
}
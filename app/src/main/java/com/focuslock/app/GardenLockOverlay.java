package com.focuslock.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class GardenLockOverlay {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static WindowManager manager;private static WebView card;private static Context appContext;private static String blockedPackage,reminder;private static boolean ready;
    private GardenLockOverlay(){}

    public static void show(Context context,String packageName){if(context==null||packageName==null||!android.provider.Settings.canDrawOverlays(context))return;MAIN.post(()->showMain(context.getApplicationContext(),packageName));}
    public static void hide(){MAIN.post(GardenLockOverlay::hideMain);}
    public static boolean isShowing(){return card!=null;}
    private static void showMain(Context context,String packageName){
        appContext=context;blockedPackage=packageName;reminder=chooseReminder(context,packageName);if(card!=null){refresh();return;}
        try{
            manager=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);if(manager==null)return;
            card=new WebView(context);WebSettings s=card.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);card.setBackgroundColor(Color.rgb(247,245,239));
            card.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView view,String url){if(url!=null&&url.startsWith("focuslock://home")){goHome();return true;}return false;}
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return shouldOverrideUrlLoading(view,request.getUrl().toString());}
                @Override public void onPageFinished(WebView view,String url){view.postDelayed(()->{ready=true;bindLiveCard();},80L);}
            });
            int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,type,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT);params.gravity=Gravity.CENTER;manager.addView(card,params);
            card.loadDataWithBaseURL("https://focuslock.local/",pageHtml(context),"text/html","UTF-8",null);refresh();
        }catch(RuntimeException e){hideMain();DiagnosticStore.record(context,"lock_card_overlay_show_failed",e.getClass().getSimpleName());}
    }
    private static String pageHtml(Context context){try(InputStream in=context.getAssets().open("lockcards/"+LockStore.lockCardName(context,blockedPackage)+".html");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[4096];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);return out.toString("UTF-8").replace("</head>",bridge()+"</head>");}catch(Exception ignored){return "<html><head>"+bridge()+"</head><body></body></html>";}}
    private static String bridge(){return "<style>html,body{width:100%;height:100%;overflow:hidden!important}body{padding:0!important;display:block!important;background:#F7F5EF!important}.phone{width:100vw!important;height:100vh!important;max-width:none!important;border-radius:0!important;box-shadow:none!important}.status{display:none!important}@media (prefers-color-scheme: dark){:root,:root[data-theme=dark]{--screen:#101A13;--ink:#F4FBF4;--ink-soft:#AFC2B2;--green:#43C66B;--green-deep:#6BEA8C;--line:#43C66B;--page-bg:#0B120D;--page-ink:#F4FBF4}body,.phone{background:#101A13!important}.quote{background:rgba(255,255,255,.07)!important;border-color:rgba(103,231,139,.28)!important;color:#F4FBF4!important}.btn{background:rgba(67,198,107,.15)!important;border-color:rgba(103,231,139,.5)!important;color:#76ED97!important}.footer{color:#AFC2B2!important}.homebar{background:rgba(225,248,228,.28)!important}.stroke{stroke:#43C66B!important}.t-clock{color:#F4FBF4!important}}</style><script>window.focusLockStart=function(end,total,app,quote){var line=document.querySelector('.appline');if(line){var state=line.querySelector('.focuslock-app-state');if(!state){Array.prototype.slice.call(line.childNodes).forEach(function(n){if(n.nodeType===3)line.removeChild(n);});state=document.createElement('span');state.className='focuslock-app-state';line.appendChild(state);}state.textContent=app+' is paused';}var q=document.querySelector('.quote');if(q)q.textContent=quote;var b=document.querySelector('.btn');if(b){b.href='focuslock://home';b.onclick=function(e){e.preventDefault();location.href='focuslock://home';};}function draw(){var left=Math.max(0,end-Date.now()),s=Math.ceil(left/1000),clock=document.querySelector('.t-clock');if(clock)clock.textContent=String(Math.floor(s/60)).padStart(2,'0')+':'+String(s%60).padStart(2,'0');var ring=document.getElementById('focuslock-progress')||document.querySelector('circle[stroke-dasharray]');if(ring){var length=ring.getTotalLength?ring.getTotalLength():653.5,ratio=Math.max(0,Math.min(1,left/Math.max(1,total)));ring.style.strokeDasharray=(length*ratio)+' '+length;ring.style.strokeDashoffset=0;}}draw();if(window.focusLockTicker)clearInterval(window.focusLockTicker);window.focusLockTicker=setInterval(draw,250);};</script>";}
    private static void bindLiveCard(){if(!ready||card==null||appContext==null||blockedPackage==null)return;card.evaluateJavascript("if(window.focusLockStart){window.focusLockStart("+LockStore.lockedUntil(appContext,blockedPackage)+","+LockStore.lockDuration(appContext)+","+js(appName(appContext,blockedPackage))+","+js(reminder)+");}",null);}
    private static void refresh(){if(card==null||appContext==null||blockedPackage==null)return;if(!LockStore.isLocked(appContext,blockedPackage)){hideMain();return;}MAIN.postDelayed(GardenLockOverlay::refresh,1000L);}
    private static void goHome(){Context c=appContext;hideMain();if(c!=null)c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}
    private static void hideMain(){MAIN.removeCallbacksAndMessages(null);if(manager!=null&&card!=null)try{manager.removeViewImmediate(card);}catch(RuntimeException ignored){}if(card!=null)card.destroy();manager=null;card=null;appContext=null;blockedPackage=null;reminder=null;ready=false;}
    private static final String[] REMINDERS={"Take a breath. This urge will pass.","A quiet minute can protect your afternoon.","Choose where your attention grows.","Slow down. Let the moment pass.","Your time is worth protecting.","Small pauses build strong habits.","Let your earlier decision support you.","Look away. Relax your shoulders.","Inhale, exhale, begin again.","Spend your attention with intention.","One protected moment can reset your day."};
    private static String chooseReminder(Context context,String packageName){long session=LockStore.lockedUntil(context,packageName);android.content.SharedPreferences p=context.getSharedPreferences("focuslock_lock_card_reminders",Context.MODE_PRIVATE);String key="reminder:"+packageName;String[] list=RemoteConfigStore.reminders(context,REMINDERS);if(p.getLong(key+":session",Long.MIN_VALUE)==session)return list[Math.max(0,Math.min(list.length-1,p.getInt(key+":index",0)))];int next=(p.getInt("last_reminder",-1)+1)%list.length;p.edit().putLong(key+":session",session).putInt(key+":index",next).putInt("last_reminder",next).apply();return list[next];}
    private static String appName(Context context,String packageName){try{return context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(packageName,0)).toString();}catch(Exception ignored){return "This app";}}
    private static String js(String s){return "'"+s.replace("\\","\\\\").replace("'","\\'").replace("\n"," ")+"'";}
}

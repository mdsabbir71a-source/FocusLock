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
    private static final String[] CARDS={"dunes","grove","horizon","nightfall","rainfall","ridge","seedling","tide"};
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static WindowManager manager; private static WebView card; private static String blockedPackage; private static Context appContext; private static boolean ready;
    private GardenLockOverlay(){}

    public static void show(Context context,String packageName){if(context==null||packageName==null||!android.provider.Settings.canDrawOverlays(context))return;MAIN.post(()->showMain(context.getApplicationContext(),packageName));}
    public static void hide(){MAIN.post(GardenLockOverlay::hideMain);}
    public static boolean isShowing(){return card!=null;}
    private static void showMain(Context context,String packageName){
        appContext=context;blockedPackage=packageName;if(card!=null){refresh();return;}
        try{
            manager=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);if(manager==null)return;
            card=new WebView(context);WebSettings s=card.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setBlockNetworkLoads(true);card.setBackgroundColor(Color.rgb(247,245,239));
            card.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,String url){if(url!=null&&url.startsWith("focuslock://home")){goHome();return true;}return false;}
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return shouldOverrideUrlLoading(v,r.getUrl().toString());}
                @Override public void onPageFinished(WebView v,String url){v.postDelayed(()->{ready=true;refresh();},80L);}
            });
            int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,type,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT);p.gravity=Gravity.CENTER;manager.addView(card,p);
            card.loadDataWithBaseURL("https://focuslock.local/",pageHtml(context,CARDS[chooseTheme(context,packageName)]),"text/html","UTF-8",null);
        }catch(RuntimeException e){hideMain();DiagnosticStore.record(context,"lock_card_overlay_show_failed",e.getClass().getSimpleName());}
    }
    private static int chooseTheme(Context c,String pkg){long session=LockStore.lockedUntil(c,pkg);android.content.SharedPreferences p=c.getSharedPreferences("focuslock_lock_card_themes",Context.MODE_PRIVATE);String key="card:"+pkg;if(p.getLong(key+":session",Long.MIN_VALUE)==session)return p.getInt(key+":theme",0);int next=(p.getInt("last_theme",-1)+1)%CARDS.length;p.edit().putLong(key+":session",session).putInt(key+":theme",next).putInt("last_theme",next).apply();return next;}
    private static String pageHtml(Context c,String name){try(InputStream in=c.getAssets().open("lockcards/"+name+".html");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);String html=out.toString("UTF-8");return html.replace("</head>",bridge()+"</head>");}catch(Exception e){return "<html><head>"+bridge()+"</head><body style='background:#F7F5EF'></body></html>";}}
    private static String bridge(){return "<style>html,body{width:100%;height:100%;overflow:hidden!important}body{padding:0!important;display:block!important;background:#F7F5EF!important}.phone{width:100vw!important;height:100vh!important;max-width:none!important;border-radius:0!important;box-shadow:none!important}.status{display:none!important}</style><script>window.focusLockSet=function(a,t){var x=document.querySelector('.appline');if(x){Array.prototype.forEach.call(x.childNodes,function(n){if(n.nodeType===3)n.nodeValue=' '+a+' is paused';});}var z=document.querySelector('.timer-clock');if(z)z.textContent=t;var b=document.querySelector('.btn');if(b){b.href='focuslock://home';b.onclick=function(e){e.preventDefault();location.href='focuslock://home';};}};</script>";}
    private static void refresh(){if(card==null||appContext==null||blockedPackage==null)return;if(!LockStore.isLocked(appContext,blockedPackage)){hideMain();return;}long left=Math.max(0L,LockStore.lockedUntil(appContext,blockedPackage)-System.currentTimeMillis());if(ready)card.evaluateJavascript("if(window.focusLockSet){window.focusLockSet("+js(appName(appContext,blockedPackage))+","+js(format(left))+");}",null);MAIN.postDelayed(GardenLockOverlay::refresh,1000L);}
    private static void goHome(){Context c=appContext;hideMain();if(c!=null)c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}
    private static void hideMain(){MAIN.removeCallbacksAndMessages(null);if(manager!=null&&card!=null)try{manager.removeViewImmediate(card);}catch(RuntimeException ignored){}if(card!=null)card.destroy();card=null;manager=null;blockedPackage=null;appContext=null;ready=false;}
    private static String appName(Context c,String pkg){try{return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return "This app";}}
    private static String js(String s){return "'"+s.replace("\\","\\\\").replace("'","\\'").replace("\n"," ")+"'";}
    private static String format(long ms){long s=Math.max(0,(ms+999)/1000);return String.format(java.util.Locale.US,"%02d:%02d",s/60,s%60);}
}
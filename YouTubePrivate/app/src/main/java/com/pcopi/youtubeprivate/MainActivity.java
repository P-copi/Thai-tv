package com.pcopi.youtubeprivate;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    WebView web; EditText search; View customView; WebChromeClient.CustomViewCallback customCallback;
    final String HOME="https://m.youtube.com/"; int red=Color.rgb(255,0,51);
    final Set<String> blockedHosts=new HashSet<>(Arrays.asList("doubleclick.net","googlesyndication.com","googleadservices.com","adservice.google.com","adnxs.com","adsrvr.org","taboola.com","outbrain.com","scorecardresearch.com"));
    @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();load(HOME);}
    TextView label(String s,int z){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(z);t.setGravity(Gravity.CENTER);t.setPadding(8,4,8,4);return t;}
    GradientDrawable bg(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(r);return g;}
    boolean isBlocked(String u){try{String h=Uri.parse(u).getHost();if(h==null)return false;h=h.toLowerCase(Locale.US);for(String x:blockedHosts)if(h.equals(x)||h.endsWith("."+x))return true;}catch(Exception e){}return false;}
    WebResourceResponse blockedResponse(){return new WebResourceResponse("text/plain","utf-8",null);}
    void hideBars(){getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    void showBars(){getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    void enterFull(View v,WebChromeClient.CustomViewCallback cb){if(customView!=null){cb.onCustomViewHidden();return;}customView=v;customCallback=cb;((ViewGroup)web.getParent()).setVisibility(View.GONE);addContentView(v,new ViewGroup.LayoutParams(-1,-1));setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);hideBars();v.setBackgroundColor(Color.BLACK);}
    void exitFull(){if(customView==null)return;((ViewGroup)customView.getParent()).removeView(customView);customView=null;if(customCallback!=null){customCallback.onCustomViewHidden();customCallback=null;}((ViewGroup)web.getParent()).setVisibility(View.VISIBLE);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);showBars();}
    void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(15,15,15));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(12,8,10,8);bar.setBackgroundColor(Color.rgb(20,20,20));
        TextView logo=label("▶",24);logo.setTextColor(red);bar.addView(logo,new LinearLayout.LayoutParams(40,48));TextView title=label("YouTube ส่วนตัว",19);title.setGravity(Gravity.CENTER_VERTICAL);bar.addView(title,new LinearLayout.LayoutParams(0,48,1));TextView open=label("↗",25);open.setOnClickListener(v->openYouTubeApp());bar.addView(open,new LinearLayout.LayoutParams(48,48));root.addView(bar);
        LinearLayout sr=new LinearLayout(this);sr.setPadding(12,8,12,8);sr.setGravity(Gravity.CENTER_VERTICAL);search=new EditText(this);search.setSingleLine(true);search.setHint("ค้นหา YouTube...");search.setHintTextColor(Color.LTGRAY);search.setTextColor(Color.WHITE);search.setTextSize(16);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setBackground(bg(Color.rgb(38,38,38),60));sr.addView(search,new LinearLayout.LayoutParams(0,50,1));TextView go=label("ค้นหา",15);go.setBackground(bg(red,50));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(82,50);gp.setMargins(8,0,0,0);sr.addView(go,gp);View.OnClickListener sl=v->{String q=search.getText().toString().trim();if(!q.isEmpty())load("https://m.youtube.com/results?search_query="+Uri.encode(q));};go.setOnClickListener(sl);search.setOnEditorActionListener((v,a,e)->{sl.onClick(v);return true;});root.addView(sr);
        web=new WebView(this);web.setBackgroundColor(Color.BLACK);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(true);s.setBuiltInZoomControls(false);s.setSupportZoom(false);s.setUserAgentString("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
        web.setWebChromeClient(new WebChromeClient(){@Override public void onShowCustomView(View v,CustomViewCallback c){enterFull(v,c);}@Override public void onHideCustomView(){exitFull();}});
        web.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){String u=r.getUrl().toString();return isBlocked(u)?blockedResponse():super.shouldInterceptRequest(v,r);}@Override public WebResourceResponse shouldInterceptRequest(WebView v,String u){return isBlocked(u)?blockedResponse():super.shouldInterceptRequest(v,u);}@Override public boolean shouldOverrideUrlLoading(WebView v,String u){if(u.startsWith("https://www.youtube.com")||u.startsWith("https://m.youtube.com")||u.startsWith("https://youtube.com"))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){}return true;}});
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(4,5,4,5);nav.setBackgroundColor(Color.rgb(20,20,20));
        TextView home=label("⌂\nหน้าแรก",12),back=label("‹\nย้อนกลับ",12),forward=label("›\nถัดไป",12),yt=label("▶\nYouTube",12);yt.setTextColor(red);
        home.setOnClickListener(v->load(HOME));back.setOnClickListener(v->{if(web.canGoBack())web.goBack();});forward.setOnClickListener(v->{if(web.canGoForward())web.goForward();});yt.setOnClickListener(v->openYouTubeApp());
        for(TextView t:new TextView[]{home,back,forward,yt})nav.addView(t,new LinearLayout.LayoutParams(0,58,1));root.addView(nav);setContentView(root);
    }
    void load(String u){web.loadUrl(u);}void openYouTubeApp(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/")));}catch(Exception e){}}
    @Override public void onBackPressed(){if(customView!=null){exitFull();return;}if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
}

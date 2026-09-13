package com.pcopi.youtubeprivate;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    WebView web; View customView; View rootView; FullscreenFrameLayout fullFrame; WebChromeClient.CustomViewCallback customCallback;
    boolean pageFullscreen=false, restoring=false; LinearLayout root; LinearLayout.LayoutParams webParams;
    final String HOME="https://m.youtube.com/"; int red=Color.rgb(255,0,51);
    final Set<String> blockedHosts=new HashSet<>(Arrays.asList("doubleclick.net","googlesyndication.com","googleadservices.com","adservice.google.com","adnxs.com","adsrvr.org","taboola.com","outbrain.com","scorecardresearch.com"));
    @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();load(HOME);}
    TextView label(String s,int z){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(z);t.setGravity(Gravity.CENTER);t.setPadding(8,4,8,4);return t;}
    GradientDrawable bg(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(r);return g;}
    boolean isBlocked(String u){try{String h=Uri.parse(u).getHost();if(h==null)return false;h=h.toLowerCase(Locale.US);for(String x:blockedHosts)if(h.equals(x)||h.endsWith("."+x))return true;}catch(Exception e){}return false;}
    WebResourceResponse blockedResponse(){return new WebResourceResponse("text/plain","utf-8",null);}
    void hideBars(){getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    void showBars(){getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}

    class SwipeOverlay extends View {
        Paint p=new Paint(3); float value=0; boolean left;
        SwipeOverlay(Context c,boolean l){super(c);left=l;setVisibility(GONE);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setValue(float v){value=Math.max(0,Math.min(1,v));invalidate();}
        void show(){setVisibility(VISIBLE);invalidate();}
        void hide(){setVisibility(GONE);}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),barW=Math.max(7,Math.min(12,w*.07f));float top=h*.22f,bottom=h*.78f,x=left?w*.06f:w*.94f-barW;p.setColor(Color.argb(130,255,255,255));c.drawRoundRect(x,top,x+barW,bottom,barW,barW,p);p.setColor(red);float fill=top+(bottom-top)*(1-value);c.drawRoundRect(x,fill,x+barW,bottom,barW,barW,p);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Math.max(22,w*.042f));p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.WHITE);c.drawText(Math.round(value*100)+"%",left?w*.15f:w*.85f,h*.86f,p);}
    }

    class FullscreenFrameLayout extends FrameLayout {
        float downX,downY; boolean adjusting; float startVolume,startBrightness; final AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE); SwipeOverlay leftOverlay,rightOverlay;
        FullscreenFrameLayout(Context c){super(c);setBackgroundColor(Color.BLACK);setFocusable(true);leftOverlay=new SwipeOverlay(c,true);rightOverlay=new SwipeOverlay(c,false);FrameLayout.LayoutParams lp1=new FrameLayout.LayoutParams(72,-1,Gravity.LEFT);FrameLayout.LayoutParams lp2=new FrameLayout.LayoutParams(72,-1,Gravity.RIGHT);addView(leftOverlay,lp1);addView(rightOverlay,lp2);}
        void prepareValues(){int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);rightOverlay.setValue(max==0?0:audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)max);WindowManager.LayoutParams p=getWindow().getAttributes();float b=p.screenBrightness;if(b<0)b=.5f;leftOverlay.setValue(b);}
        void showControls(){prepareValues();leftOverlay.show();rightOverlay.show();new Handler().postDelayed(()->{leftOverlay.hide();rightOverlay.hide();},1400);}
        @Override public boolean onInterceptTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:downX=e.getX();downY=e.getY();startVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);WindowManager.LayoutParams p=getWindow().getAttributes();startBrightness=p.screenBrightness<0?.5f:p.screenBrightness;adjusting=false;break;case MotionEvent.ACTION_MOVE:float dx=e.getX()-downX,dy=e.getY()-downY;if(Math.abs(dy)>24&&Math.abs(dy)>Math.abs(dx)*1.12f){adjusting=true;return true;}break;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:adjusting=false;break;}return super.onInterceptTouchEvent(e);}
        @Override public boolean onTouchEvent(MotionEvent e){if(!adjusting)return true;if(e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP){boolean right=e.getX()>getWidth()/2f;float delta=(downY-e.getY())/Math.max(1f,getHeight());showControls();if(right){int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);int target=Math.round(startVolume+delta*0.45f*max);target=Math.max(0,Math.min(max,target));audio.setStreamVolume(AudioManager.STREAM_MUSIC,target,0);rightOverlay.setValue(max==0?0:target/(float)max);}else{float target=Math.max(.05f,Math.min(1f,startBrightness+delta*0.80f));WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=target;getWindow().setAttributes(p);leftOverlay.setValue(target);}return true;}return true;}
    }

    void bringOverlaysToFront(){if(fullFrame!=null){fullFrame.bringChildToFront(fullFrame.leftOverlay);fullFrame.bringChildToFront(fullFrame.rightOverlay);}}
    void enterFull(View v,WebChromeClient.CustomViewCallback cb){if(pageFullscreen){cb.onCustomViewHidden();return;}if(customView!=null){cb.onCustomViewHidden();return;}customView=v;customCallback=cb;v.setBackgroundColor(Color.BLACK);fullFrame=new FullscreenFrameLayout(this);fullFrame.addView(v,new FrameLayout.LayoutParams(-1,-1));setContentView(fullFrame);bringOverlaysToFront();fullFrame.prepareValues();setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);hideBars();fullFrame.setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    void enterPageFull(){if(pageFullscreen||customView!=null||web==null)return;ViewParent p=web.getParent();if(p instanceof ViewGroup)((ViewGroup)p).removeView(web);fullFrame=new FullscreenFrameLayout(this);fullFrame.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(fullFrame);pageFullscreen=true;bringOverlaysToFront();fullFrame.prepareValues();setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);hideBars();fullFrame.setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    void exitPageFull(){if(!pageFullscreen)return;pageFullscreen=false;if(fullFrame!=null){fullFrame.removeView(web);fullFrame=null;}root.addView(web,2,webParams);setContentView(root);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);showBars();loadHomeOnly();}
    void exitFull(){if(customView==null)return;View old=customView;customView=null;if(customCallback!=null){customCallback.onCustomViewHidden();customCallback=null;}fullFrame=null;setContentView(rootView);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);showBars();old.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);loadHomeOnly();}

    void buildUi(){
        root=new LinearLayout(this);rootView=root;root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(15,15,15));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(12,8,10,8);bar.setBackgroundColor(Color.rgb(20,20,20));TextView logo=label("▶",24);logo.setTextColor(red);bar.addView(logo,new LinearLayout.LayoutParams(40,48));TextView title=label("YouTube ส่วนตัว",19);title.setGravity(Gravity.CENTER_VERTICAL);bar.addView(title,new LinearLayout.LayoutParams(0,48,1));TextView open=label("↗",25);open.setOnClickListener(v->openYouTubeApp());bar.addView(open,new LinearLayout.LayoutParams(48,48));root.addView(bar);
        LinearLayout sr=new LinearLayout(this);sr.setPadding(12,8,12,8);sr.setGravity(Gravity.CENTER_VERTICAL);EditText search=new EditText(this);search.setSingleLine(true);search.setHint("ค้นหา YouTube...");search.setHintTextColor(Color.LTGRAY);search.setTextColor(Color.WHITE);search.setTextSize(16);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setBackground(bg(Color.rgb(38,38,38),60));sr.addView(search,new LinearLayout.LayoutParams(0,50,1));TextView go=label("ค้นหา",15);go.setBackground(bg(red,50));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(82,50);gp.setMargins(8,0,0,0);sr.addView(go,gp);View.OnClickListener sl=v->{String q=search.getText().toString().trim();if(!q.isEmpty())load("https://m.youtube.com/results?search_query="+Uri.encode(q));};go.setOnClickListener(sl);search.setOnEditorActionListener((v,a,e)->{sl.onClick(v);return true;});root.addView(sr);
        web=new WebView(this);web.setBackgroundColor(Color.BLACK);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setMediaPlaybackRequiresUserGesture(true);s.setBuiltInZoomControls(false);s.setSupportZoom(false);s.setUserAgentString("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
        web.setWebChromeClient(new WebChromeClient(){@Override public void onShowCustomView(View v,CustomViewCallback c){if(pageFullscreen){c.onCustomViewHidden();return;}enterFull(v,c);}@Override public void onHideCustomView(){exitFull();}});
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap b){super.onPageStarted(v,u,b);if(!restoring&&!pageFullscreen&&customView==null&&isWatchUrl(u)){v.post(()->{if(!pageFullscreen&&customView==null)enterPageFull();});}}
            @Override public void onPageFinished(WebView v,String u){super.onPageFinished(v,u);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){String u=r.getUrl().toString();return isBlocked(u)?blockedResponse():super.shouldInterceptRequest(v,r);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,String u){return isBlocked(u)?blockedResponse():super.shouldInterceptRequest(v,u);}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String u){if(u.startsWith("https://www.youtube.com")||u.startsWith("https://m.youtube.com")||u.startsWith("https://youtube.com"))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){}return true;}
        });
        webParams=new LinearLayout.LayoutParams(-1,0,1);root.addView(web,webParams);
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(4,5,4,5);nav.setBackgroundColor(Color.rgb(20,20,20));TextView home=label("⌂\nหน้าแรก",12),back=label("‹\nย้อนกลับ",12),forward=label("›\nถัดไป",12),yt=label("▶\nYouTube",12),expand=label("⛶\nเต็มจอ",12);yt.setTextColor(red);expand.setTextColor(red);home.setOnClickListener(v->load(HOME));back.setOnClickListener(v->{if(web.canGoBack())web.goBack();});forward.setOnClickListener(v->{if(web.canGoForward())web.goForward();});yt.setOnClickListener(v->openYouTubeApp());expand.setOnClickListener(v->enterPageFull());for(TextView t:new TextView[]{home,back,forward,yt,expand})nav.addView(t,new LinearLayout.LayoutParams(0,58,1));root.addView(nav);setContentView(root);
    }
    boolean isWatchUrl(String u){try{Uri x=Uri.parse(u);String h=x.getHost();String p=x.getPath();return h!=null&&(h.equals("m.youtube.com")||h.equals("www.youtube.com")||h.equals("youtube.com"))&&p!=null&&(p.equals("/watch")||p.startsWith("/shorts/"));}catch(Exception e){return false;}}
    void load(String u){if(pageFullscreen)exitPageFull();restoring=u.equals(HOME);web.loadUrl(u);if(restoring)new Handler().postDelayed(()->restoring=false,1000);}
    void loadHomeOnly(){restoring=true;web.loadUrl(HOME);new Handler().postDelayed(()->restoring=false,1000);}
    void openYouTubeApp(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/")));}catch(Exception e){}}
    @Override public void onBackPressed(){if(customView!=null){exitFull();return;}if(pageFullscreen){exitPageFull();return;}if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
}

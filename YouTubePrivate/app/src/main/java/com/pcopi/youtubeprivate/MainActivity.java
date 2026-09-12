package com.pcopi.youtubeprivate;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    WebView web;
    EditText search;
    final String HOME="https://m.youtube.com/";
    int red=Color.rgb(255,0,51);

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi(); load(HOME); }

    TextView label(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(size); t.setGravity(Gravity.CENTER); t.setPadding(8,4,8,4); return t; }
    GradientDrawable bg(int color,float r){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(r); return g; }

    void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(15,15,15));
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(12,8,10,8); bar.setBackgroundColor(Color.rgb(20,20,20));
        TextView logo=label("▶",24); logo.setTextColor(red); bar.addView(logo,new LinearLayout.LayoutParams(40,48));
        TextView title=label("YouTube ส่วนตัว",19); title.setGravity(Gravity.CENTER_VERTICAL); bar.addView(title,new LinearLayout.LayoutParams(0,48,1));
        TextView open=label("↗",25); open.setOnClickListener(v->openYouTubeApp()); bar.addView(open,new LinearLayout.LayoutParams(48,48));
        root.addView(bar);

        LinearLayout searchRow=new LinearLayout(this); searchRow.setPadding(12,8,12,8); searchRow.setGravity(Gravity.CENTER_VERTICAL);
        search=new EditText(this); search.setSingleLine(true); search.setHint("ค้นหา YouTube..."); search.setHintTextColor(Color.LTGRAY); search.setTextColor(Color.WHITE); search.setTextSize(16); search.setPadding(18,0,12,0); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH); search.setBackground(bg(Color.rgb(38,38,38),60));
        searchRow.addView(search,new LinearLayout.LayoutParams(0,50,1));
        TextView go=label("ค้นหา",15); go.setTextColor(Color.WHITE); go.setBackground(bg(red,50)); LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(82,50); gp.setMargins(8,0,0,0); searchRow.addView(go,gp);
        View.OnClickListener sl=v->{String q=search.getText().toString().trim(); if(!q.isEmpty()) load("https://m.youtube.com/results?search_query="+Uri.encode(q));}; go.setOnClickListener(sl); search.setOnEditorActionListener((v,a,e)->{sl.onClick(v); return true;});
        root.addView(searchRow);

        web=new WebView(this); web.setBackgroundColor(Color.BLACK); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true); web.getSettings().setMediaPlaybackRequiresUserGesture(true); web.getSettings().setBuiltInZoomControls(false); web.getSettings().setSupportZoom(false); web.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
        web.setWebViewClient(new WebViewClient(){ @Override public boolean shouldOverrideUrlLoading(WebView v,String u){ if(u.startsWith("https://www.youtube.com")||u.startsWith("https://m.youtube.com")||u.startsWith("https://youtube.com")) return false; try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception ignored){} return true; }});
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(4,5,4,5); nav.setBackgroundColor(Color.rgb(20,20,20));
        TextView home=label("⌂\nหน้าแรก",12); TextView back=label("‹\nย้อนกลับ",12); TextView forward=label("›\nถัดไป",12); TextView yt=label("▶\nYouTube",12); home.setTextColor(Color.WHITE); yt.setTextColor(red);
        home.setOnClickListener(v->load(HOME)); back.setOnClickListener(v->{if(web.canGoBack())web.goBack();}); forward.setOnClickListener(v->{if(web.canGoForward())web.goForward();}); yt.setOnClickListener(v->openYouTubeApp());
        for(TextView t:new TextView[]{home,back,forward,yt}) nav.addView(t,new LinearLayout.LayoutParams(0,58,1)); root.addView(nav);
        setContentView(root);
    }

    void load(String url){ web.loadUrl(url); }
    void openYouTubeApp(){ try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/")));}catch(Exception ignored){} }
    @Override public void onBackPressed(){ if(web!=null&&web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}

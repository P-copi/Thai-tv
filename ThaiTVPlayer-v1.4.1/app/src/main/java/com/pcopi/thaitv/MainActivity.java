package com.pcopi.thaitv;

import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    static class Channel { String name, url, logo; Channel(String n, String u, String l){name=n;url=u;logo=l;} }
    final ArrayList<Channel> channels = new ArrayList<>();
    LinearLayout root, listBox;
    FrameLayout playerBox;
    PlayerView playerView;
    ExoPlayer player;
    TextView status;
    Button closePlayer;
    int PICK_M3U = 1001;
    final android.os.Handler main = new android.os.Handler(Looper.getMainLooper());

    int dp(int n){ return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    TextView text(String s, float z){ TextView t=new TextView(this); t.setText(s); t.setTextSize(z); t.setTextColor(Color.DKGRAY); t.setPadding(dp(12),dp(8),dp(12),dp(8)); return t; }
    Button button(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi(); }

    void buildUi(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        LinearLayout bar=new LinearLayout(this); bar.setPadding(dp(4),dp(3),dp(4),0);
        TextView title=text("Thai TV Player",19); title.setTextColor(Color.BLACK);
        bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button file=button("นำเข้า M3U"), url=button("จาก URL");
        bar.addView(file,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(url,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));

        status=text("นำเข้าไฟล์ M3U หรือ URL เพื่อเริ่มใช้งาน",14);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        playerBox=new FrameLayout(this); playerBox.setBackgroundColor(Color.BLACK); playerBox.setVisibility(View.GONE);
        playerView=new PlayerView(this); playerView.setUseController(true);
        playerBox.addView(playerView,new FrameLayout.LayoutParams(-1,dp(220)));
        closePlayer=button("✕ ปิดจอ"); closePlayer.setTextColor(Color.WHITE); closePlayer.setBackgroundColor(Color.argb(190,0,0,0));
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(100),dp(48),Gravity.TOP|Gravity.END); cp.setMargins(0,dp(4),dp(4),0);
        playerBox.addView(closePlayer,cp); root.addView(playerBox,new LinearLayout.LayoutParams(-1,dp(230)));

        listBox=new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this); scroll.addView(listBox); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        file.setOnClickListener(v->pickFile()); url.setOnClickListener(v->askUrl()); closePlayer.setOnClickListener(v->hidePlayer());
    }

    void pickFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","application/octet-stream","application/vnd.apple.mpegurl","audio/x-mpegurl"});
        startActivityForResult(i,PICK_M3U);
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==PICK_M3U && c==RESULT_OK && d!=null && d.getData()!=null){
            try{
                getContentResolver().takePersistableUriPermission(d.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }catch(Exception ignored){}
            try{ loadText(read(d.getData())); }
            catch(Exception e){ status.setText("เปิดไฟล์ไม่สำเร็จ: "+e.getMessage()); }
        }
    }

    String read(Uri uri)throws Exception{ try(InputStream in=getContentResolver().openInputStream(uri)){ return read(in); } }
    String read(InputStream in)throws Exception{
        ByteArrayOutputStream o=new ByteArrayOutputStream(); byte[] b=new byte[16384]; int n;
        while(in!=null && (n=in.read(b))>0)o.write(b,0,n);
        byte[] data=o.toByteArray();
        String s=new String(data,StandardCharsets.UTF_8);
        if(s.indexOf('\uFFFD')>=0) try{s=new String(data,"UTF-16");}catch(Exception ignored){}
        return s.replace("\uFEFF","");
    }

    void askUrl(){
        EditText e=new EditText(this); e.setSingleLine(true); e.setHint("https://.../playlist.m3u"); e.setInputType(33);
        new AlertDialog.Builder(this).setTitle("นำเข้า Playlist จาก URL").setView(e)
            .setNegativeButton("ยกเลิก",null).setPositiveButton("โหลด",(d,w)->fetchUrl(e.getText().toString().trim())).show();
    }

    void fetchUrl(String u){
        if(u.isEmpty()) return; status.setText("กำลังโหลด Playlist...");
        Executors.newSingleThreadExecutor().execute(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent","ThaiTVPlayer/1.4.4");
                String s=read(c.getInputStream()); c.disconnect(); main.post(()->loadText(s));
            }catch(Exception e){ main.post(()->status.setText("โหลด URL ไม่สำเร็จ: "+e.getMessage())); }
        });
    }

    String attr(String line,String key){
        String q=key+"="; int p=line.toLowerCase(Locale.ROOT).indexOf(q.toLowerCase(Locale.ROOT));
        if(p<0)return ""; p+=q.length(); if(p>=line.length())return "";
        char quote=line.charAt(p); if(quote=='\"' || quote=='\''){ int e=line.indexOf(quote,p+1); return e>p?line.substring(p+1,e):""; }
        int e=line.indexOf(',',p); return (e>p?line.substring(p,e):line.substring(p)).trim();
    }

    void loadText(String s){
        channels.clear(); String name=null,logo="";
        String[] lines=s.replace("\r\n","\n").replace('\r','\n').split("\n");
        for(String raw:lines){
            String line=raw.trim(); if(line.isEmpty())continue;
            if(line.startsWith("#EXTINF")){
                int p=line.indexOf(','); name=p>=0?line.substring(p+1).trim():"ช่องทีวี"; logo=attr(line,"tvg-logo");
                if(name.contains(","))name=name.substring(name.lastIndexOf(',')+1).trim();
            }else if(!line.startsWith("#") && (line.startsWith("http://")||line.startsWith("https://"))){
                if(name==null)name="ช่องทีวี"; channels.add(new Channel(name,line,logo)); name=null; logo="";
            }
        }
        renderList(); status.setText(channels.isEmpty()?"ไม่พบช่องในไฟล์ M3U":("พบ "+channels.size()+" ช่อง"));
    }

    void renderList(){
        listBox.removeAllViews();
        for(int i=0;i<channels.size();i++){
            final Channel ch=channels.get(i);
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(8),dp(3),dp(8),dp(3));
            ImageView icon=new ImageView(this); icon.setImageResource(android.R.drawable.ic_media_play); icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(icon,new LinearLayout.LayoutParams(dp(48),dp(52)));
            TextView label=text((i+1)+"  "+ch.name,16); label.setTextColor(Color.DKGRAY); row.addView(label,new LinearLayout.LayoutParams(0,dp(56),1));
            row.setOnClickListener(v->play(ch)); listBox.addView(row,new LinearLayout.LayoutParams(-1,dp(62)));
            if(!ch.logo.isEmpty()) loadLogo(ch.logo,icon);
        }
    }

    void loadLogo(String u,ImageView target){
        Executors.newSingleThreadExecutor().execute(()->{
            try{ HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(5000); c.setReadTimeout(8000); c.setRequestProperty("User-Agent","Mozilla/5.0"); Bitmap b=BitmapFactory.decodeStream(c.getInputStream()); c.disconnect(); if(b!=null)main.post(()->target.setImageBitmap(b)); }catch(Exception ignored){}
        });
    }

    void play(Channel ch){
        if(player!=null)player.release();
        player=new ExoPlayer.Builder(this).build(); playerView.setPlayer(player); playerBox.setVisibility(View.VISIBLE);
        MediaItem.Builder mb=new MediaItem.Builder().setUri(Uri.parse(ch.url));
        if(ch.url.toLowerCase(Locale.ROOT).contains(".m3u8"))mb.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(mb.build()); player.prepare(); player.play(); status.setText("กำลังเล่น: "+ch.name);
    }

    void hidePlayer(){ if(player!=null){player.stop();player.release();player=null;} playerView.setPlayer(null); playerBox.setVisibility(View.GONE); }
    @Override public void onBackPressed(){ if(player!=null){hidePlayer();return;} super.onBackPressed(); }
    @Override protected void onDestroy(){ if(player!=null)player.release(); super.onDestroy(); }
    @Override public void onConfigurationChanged(android.content.res.Configuration c){
        super.onConfigurationChanged(c); if(playerBox==null)return; boolean land=c.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        playerBox.getLayoutParams().height=land?-1:dp(230); playerBox.requestLayout();
        getWindow().getDecorView().setSystemUiVisibility(land?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION):0);
    }
}

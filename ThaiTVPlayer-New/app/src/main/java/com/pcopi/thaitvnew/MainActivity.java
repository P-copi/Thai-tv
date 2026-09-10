package com.pcopi.thaitvnew;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_M3U = 7001;
    private static final String DEFAULT_PLAYLIST = "https://raw.githubusercontent.com/P-copi/Thai-tv/main/thai-tv-new.m3u";
    private DrawerLayout drawer;
    private LinearLayout normalPanel, toolbar, controls;
    private RecyclerView recycler;
    private TextView status, title;
    private PlayerView playerView;
    private FrameLayout playerFrame;
    private Button closePlayer;
    private ExoPlayer player;
    private ChannelAdapter adapter;
    private final ArrayList<Channel> channels = new ArrayList<>();
    private final ArrayList<Channel> favorites = new ArrayList<>();
    private final ArrayList<Channel> history = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        try { buildUi(); } catch (Throwable t) { setContentView(makeFatal(t)); return; }
        applyOrientation(getResources().getConfiguration().orientation);
        loadUrl(DEFAULT_PLAYLIST);
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientation(newConfig.orientation);
    }

    private void applyOrientation(int orientation) {
        boolean landscape = orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (normalPanel != null) normalPanel.setVisibility(landscape ? View.GONE : View.VISIBLE);
        if (toolbar != null) toolbar.setVisibility(landscape ? View.GONE : View.VISIBLE);
        if (controls != null) controls.setVisibility(landscape ? View.GONE : View.VISIBLE);
        if (status != null) status.setVisibility(landscape ? View.GONE : View.VISIBLE);
        if (recycler != null) recycler.setVisibility(landscape ? View.GONE : View.VISIBLE);
        if (drawer != null) drawer.setDrawerLockMode(landscape ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
        if (playerFrame != null) {
            playerFrame.setBackgroundColor(Color.BLACK);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, landscape ? 0 : dp(220));
            if (landscape) lp.weight = 1;
            playerFrame.setLayoutParams(lp);
        }
        if (closePlayer != null) closePlayer.setVisibility(landscape ? View.VISIBLE : View.GONE);
        if (landscape) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView tv(String s, float size) { TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(0xffeeeeee); t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(16),0,dp(16),0); return t; }
    private Button btn(String s) { Button b=new Button(this); b.setText(s); b.setTextSize(14); return b; }

    private void buildUi() {
        drawer = new DrawerLayout(this);
        drawer.setBackgroundColor(0xff101010);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        drawer.addView(root, new DrawerLayout.LayoutParams(-1,-1));

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(0xff212121);
        TextView menu=tv("☰",28); menu.setGravity(Gravity.CENTER);
        toolbar.addView(menu,new LinearLayout.LayoutParams(dp(54),dp(56)));
        menu.setOnClickListener(v->drawer.openDrawer(Gravity.START));
        title=tv("Thai TV Player",20); title.setTypeface(null,1);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));
        TextView search=tv("⌕",28); search.setGravity(Gravity.CENTER);
        toolbar.addView(search,new LinearLayout.LayoutParams(dp(54),dp(56)));
        search.setOnClickListener(v->searchDialog());
        root.addView(toolbar);

        playerFrame=new FrameLayout(this);
        playerFrame.setBackgroundColor(Color.BLACK);
        playerView=new PlayerView(this);
        playerView.setUseController(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setBackgroundColor(Color.BLACK);
        playerFrame.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        closePlayer=btn("✕");
        closePlayer.setTextSize(22);
        closePlayer.setTextColor(Color.WHITE);
        closePlayer.setBackgroundColor(0xaa222222);
        FrameLayout.LayoutParams closeLp=new FrameLayout.LayoutParams(dp(58),dp(52),Gravity.TOP|Gravity.END);
        closeLp.setMargins(0,dp(10),dp(10),0);
        playerFrame.addView(closePlayer,closeLp);
        closePlayer.setVisibility(View.GONE);
        closePlayer.setOnClickListener(v->stopPlayer());
        root.addView(playerFrame,new LinearLayout.LayoutParams(-1,dp(220)));

        controls=new LinearLayout(this);
        controls.setPadding(dp(6),dp(3),dp(6),dp(3));
        Button file=btn("ไฟล์ M3U"), url=btn("URL Playlist"), refresh=btn("↻ รีเฟรช");
        controls.addView(file,new LinearLayout.LayoutParams(0,dp(48),1));
        controls.addView(url,new LinearLayout.LayoutParams(0,dp(48),1));
        controls.addView(refresh,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(controls);
        file.setOnClickListener(v->pickFile()); url.setOnClickListener(v->urlDialog()); refresh.setOnClickListener(v->loadUrl(DEFAULT_PLAYLIST));

        status=tv("กำลังเตรียมรายการช่อง…",14); status.setTextColor(0xffffb74d);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(40)));
        recycler=new RecyclerView(this); recycler.setLayoutManager(new LinearLayoutManager(this)); adapter=new ChannelAdapter(); recycler.setAdapter(adapter);
        root.addView(recycler,new LinearLayout.LayoutParams(-1,0,1));
        normalPanel=root;
        buildDrawer();
        setContentView(drawer);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Android) ThaiTVPlayer/3.1")
                .setConnectTimeoutMs(15000).setReadTimeoutMs(25000).setAllowCrossProtocolRedirects(true);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();
        playerView.setPlayer(player);
        player.addListener(new androidx.media3.common.Player.Listener(){
            @Override public void onPlayerError(PlaybackException e){ error("เปิดช่องไม่ได้: "+(e.getErrorCodeName()==null?"ไม่ทราบสาเหตุ":e.getErrorCodeName())); }
        });
    }

    private void buildDrawer(){
        LinearLayout nav=new LinearLayout(this); nav.setOrientation(LinearLayout.VERTICAL); nav.setBackgroundColor(0xff181818); nav.setPadding(0,dp(20),0,dp(12));
        TextView head=tv("  Thai TV Player",22); head.setTypeface(null,1); head.setTextColor(0xffff9800); nav.addView(head,new LinearLayout.LayoutParams(-1,dp(72)));
        addNav(nav,"▣   รายการช่อง",()->showChannels(channels,"รายการช่อง"));
        addNav(nav,"★   รายการโปรด",()->showChannels(favorites,"รายการโปรด"));
        addNav(nav,"◷   ประวัติการรับชม",()->showChannels(history,"ประวัติการรับชม"));
        TextView sep=tv("  เพลย์ลิสต์",12); sep.setTextColor(0xff888888); nav.addView(sep,new LinearLayout.LayoutParams(-1,dp(40)));
        addNav(nav,"＋   เปิดไฟล์ M3U",this::pickFile); addNav(nav,"↗   เปิด URL Playlist",this::urlDialog); addNav(nav,"▶   เปิด URL สตรีม",this::streamDialog);
        TextView sep2=tv("  อื่นๆ",12); sep2.setTextColor(0xff888888); nav.addView(sep2,new LinearLayout.LayoutParams(-1,dp(40)));
        addNav(nav,"⚙   ตั้งค่า",this::settingsDialog); addNav(nav,"ⓘ   เกี่ยวกับ",this::aboutDialog);
        DrawerLayout.LayoutParams lp=new DrawerLayout.LayoutParams(dp(310),-1); lp.gravity=Gravity.START; drawer.addView(nav,lp);
    }
    private void addNav(LinearLayout p,String s,Runnable c){TextView t=tv(s,16);t.setPadding(dp(22),0,dp(8),0);p.addView(t,new LinearLayout.LayoutParams(-1,dp(52)));t.setOnClickListener(v->{drawer.closeDrawers();c.run();});}
    private void showChannels(List<Channel> d,String n){title.setText(n);adapter.setData(d);status.setText(d.size()+" ช่อง");status.setTextColor(0xffffb74d);}

    private void pickFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/x-mpegURL","application/vnd.apple.mpegurl","audio/mpegurl","audio/x-mpegurl","application/octet-stream","text/plain","*/*"});
        startActivityForResult(i,PICK_M3U);
    }
    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==PICK_M3U&&res==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri u=data.getData();
            executor.execute(()->{try(InputStream in=getContentResolver().openInputStream(u)){
                if(in==null)throw new IOException("ไม่สามารถอ่านไฟล์");
                String text=read(in);
                if(!looksLikeM3u(text)) throw new IOException("ไฟล์นี้ไม่ใช่ M3U/M3U8 ที่อ่านได้");
                main.post(()->parseAndShow(text,"ไฟล์ M3U"));
            }catch(Exception e){main.post(()->error("อ่านไฟล์ไม่ได้: "+e.getMessage()));}});
        }
    }
    private boolean looksLikeM3u(String s){if(s==null)return false;String x=s.replace("\uFEFF","").trim();return x.startsWith("#EXTM3U")||x.contains("#EXTINF")||(x.contains(".m3u8")&&x.contains("http"));}

    private void urlDialog(){EditText e=new EditText(this);e.setSingleLine();e.setHint("https://.../playlist.m3u");new AlertDialog.Builder(this).setTitle("URL Playlist").setView(e).setNegativeButton("ยกเลิก",null).setPositiveButton("เปิด",(d,w)->loadUrl(e.getText().toString().trim())).show();}
    private void streamDialog(){EditText e=new EditText(this);e.setSingleLine();e.setHint("https://.../stream.m3u8");new AlertDialog.Builder(this).setTitle("เปิด URL สตรีม").setView(e).setNegativeButton("ยกเลิก",null).setPositiveButton("เล่น",(d,w)->{String u=e.getText().toString().trim();if(!u.isEmpty())play(new Channel("Network Stream","",u));}).show();}
    private void searchDialog(){EditText e=new EditText(this);e.setSingleLine();e.setHint("ค้นหาช่อง");new AlertDialog.Builder(this).setTitle("ค้นหาช่อง").setView(e).setNegativeButton("ปิด",null).setPositiveButton("ค้นหา",(d,w)->{String q=e.getText().toString().toLowerCase(Locale.ROOT);ArrayList<Channel> r=new ArrayList<>();for(Channel c:channels)if(c.name.toLowerCase(Locale.ROOT).contains(q))r.add(c);showChannels(r,"ผลการค้นหา");}).show();}
    private void settingsDialog(){new AlertDialog.Builder(this).setTitle("ตั้งค่า").setMultiChoiceItems(new String[]{"เล่นช่องอัตโนมัติเมื่อเลือก","จำรายการโปรด","ใช้ตัวควบคุมแบบเต็มหน้าจอ"},new boolean[]{true,true,true},null).setPositiveButton("ตกลง",null).show();}
    private void aboutDialog(){new AlertDialog.Builder(this).setTitle("Thai TV Player").setMessage("เครื่องเล่น IPTV สำหรับ M3U / M3U8\n\nรองรับไฟล์ M3U ในเครื่อง, URL Playlist และการเล่น HLS/M3U8").setPositiveButton("ตกลง",null).show();}

    private void loadUrl(String url){if(url==null||url.isEmpty()){error("URL ว่าง");return;}status.setText("กำลังโหลด playlist…");status.setTextColor(0xffffb74d);executor.execute(()->{try{String text=http(url);main.post(()->parseAndShow(text,"รายการช่อง"));}catch(Exception e){main.post(()->error("โหลดไม่ได้: "+e.getMessage()));}});}
    private String http(String s)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(s).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Mozilla/5.0 (Android) ThaiTVPlayer/3.1");int code=c.getResponseCode();if(code<200||code>=400)throw new IOException("HTTP "+code);try(InputStream in=c.getInputStream()){return read(in);}finally{c.disconnect();}}
    private String read(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))!=-1)b.write(x,0,n);return b.toString(StandardCharsets.UTF_8.name());}
    private void parseAndShow(String text,String name){ArrayList<Channel> r=parse(text);if(r.isEmpty()){error("ไม่พบช่องใน M3U");return;}channels.clear();channels.addAll(r);showChannels(channels,name);status.setText("พบ "+r.size()+" ช่อง");}
    private ArrayList<Channel> parse(String text){ArrayList<Channel> r=new ArrayList<>();String[] lines=text.replace("\uFEFF","").replace("\r","").split("\n");String n="ช่องไม่ระบุ",logo="";for(String raw:lines){String line=raw.trim();if(line.startsWith("#EXTINF")){int comma=line.indexOf(',');n=comma>=0?line.substring(comma+1).trim():"ช่อง";logo=attr(line,"tvg-logo");}else if((line.startsWith("http://")||line.startsWith("https://"))&&!line.startsWith("#")){r.add(new Channel(n,logo,line));n="ช่องไม่ระบุ";logo="";}}return r;}
    private String attr(String s,String key){String q=key+"=\"";int a=s.indexOf(q);if(a<0)return "";a+=q.length();int b=s.indexOf('"',a);return b>a?s.substring(a,b):"";}
    private void play(Channel c){if(c==null||c.url==null||c.url.isEmpty()||player==null)return;try{String u=c.url.trim();MediaItem.Builder mb=new MediaItem.Builder().setUri(u);String low=u.toLowerCase(Locale.ROOT);if(low.contains(".m3u8")||low.contains("m3u8?"))mb.setMimeType(MimeTypes.APPLICATION_M3U8);player.stop();player.setMediaItem(mb.build());player.prepare();player.play();addHistory(c);title.setText(c.name);status.setText("กำลังเปิด: "+c.name);status.setTextColor(0xffffb74d);}catch(Exception e){error("เปิดช่องไม่ได้: "+e.getMessage());}}
    private void stopPlayer(){if(player!=null){player.stop();player.clearMediaItems();}title.setText("Thai TV Player");status.setText("หยุดเล่นแล้ว");status.setTextColor(0xffffb74d);}
    private void addHistory(Channel c){for(int i=history.size()-1;i>=0;i--)if(history.get(i).url.equals(c.url))history.remove(i);history.add(0,c);if(history.size()>30)history.remove(history.size()-1);}
    private void toggleFav(Channel c){for(int i=0;i<favorites.size();i++){if(favorites.get(i).url.equals(c.url)){favorites.remove(i);adapter.notifyDataSetChanged();return;}}favorites.add(c);adapter.notifyDataSetChanged();}
    private boolean isFav(Channel c){for(Channel x:favorites)if(x.url.equals(c.url))return true;return false;}
    private void error(String s){status.setText(s);status.setTextColor(0xffff5252);}
    private View makeFatal(Throwable t){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(24),dp(50),dp(24),dp(24));l.setBackgroundColor(0xff101010);TextView a=tv("Thai TV Player",24);a.setTextColor(0xffff9800);l.addView(a);TextView e=tv("แอปเริ่มทำงานไม่สำเร็จ\n"+t.getClass().getSimpleName()+"\n"+(t.getMessage()==null?"":t.getMessage()),14);e.setTextColor(0xffff7777);l.addView(e);Button r=btn("ลองเปิดใหม่");r.setOnClickListener(v->recreate());l.addView(r);return l;}
    @Override protected void onDestroy(){if(player!=null)player.release();executor.shutdownNow();super.onDestroy();}

    static class Channel{String name,logo,url;Channel(String n,String l,String u){name=n;logo=l;url=u;}}
    class ChannelAdapter extends RecyclerView.Adapter<ChannelVH>{
        ArrayList<Channel> data=new ArrayList<>();
        void setData(List<Channel>d){data=new ArrayList<>(d);notifyDataSetChanged();}
        @Override public ChannelVH onCreateViewHolder(android.view.ViewGroup p,int v){LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(5),dp(8),dp(5));ImageView im=new ImageView(MainActivity.this);im.setScaleType(ImageView.ScaleType.CENTER_INSIDE);row.addView(im,new LinearLayout.LayoutParams(dp(58),dp(58)));LinearLayout mid=new LinearLayout(MainActivity.this);mid.setOrientation(LinearLayout.VERTICAL);TextView name=tv("",16),sub=tv("",12);sub.setTextColor(0xff888888);mid.addView(name,new LinearLayout.LayoutParams(-1,dp(34)));mid.addView(sub,new LinearLayout.LayoutParams(-1,dp(24)));row.addView(mid,new LinearLayout.LayoutParams(0,dp(68),1));TextView star=tv("☆",28);star.setGravity(Gravity.CENTER);row.addView(star,new LinearLayout.LayoutParams(dp(52),dp(68)));return new ChannelVH(row,im,name,sub,star);}
        @Override public void onBindViewHolder(ChannelVH h,int pos){Channel c=data.get(pos);h.name.setText(c.name);h.sub.setText(c.url);h.star.setText(isFav(c)?"★":"☆");if(c.logo!=null&&!c.logo.isEmpty())Glide.with(MainActivity.this).load(c.logo).placeholder(android.R.drawable.ic_menu_gallery).error(android.R.drawable.ic_menu_gallery).into(h.img);else h.img.setImageResource(android.R.drawable.ic_media_play);h.itemView.setOnClickListener(v->play(c));h.star.setOnClickListener(v->toggleFav(c));}
        @Override public int getItemCount(){return data.size();}
    }
    static class ChannelVH extends RecyclerView.ViewHolder{ImageView img;TextView name,sub,star;ChannelVH(View v,ImageView i,TextView n,TextView s,TextView f){super(v);img=i;name=n;sub=s;star=f;}}
}

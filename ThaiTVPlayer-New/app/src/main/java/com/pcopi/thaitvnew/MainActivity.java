package com.pcopi.thaitvnew;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
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
        if (normalPanel != null) normalPanel.setVisibility(View.VISIBLE);
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
                .setUserAgent("Mozilla/5.0 (Android) ThaiTVPlayer/4.0")
                .setConnectTimeoutMs(15000).setReadTimeoutMs(25000).setAllowCrossProtocolRedirects(true);
        DefaultMediaSourceFactory mediaFactory = new DefaultMediaSourceFactory(http)
                .setDrmSessionManagerProvider(new ClearKeyDrmProvider());
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(mediaFactory).build();
        playerView.setPlayer(player);
        player.addListener(new androidx.media3.common.Player.Listener(){
            @Override public void onPlayerError(PlaybackException e){ error("เปิดช่องไม่ได้: "+(e.getErrorCodeName()==null?"ไม่ทราบสาเหตุ":e.getErrorCodeName())); }
        });
    }

    private class ClearKeyDrmProvider implements DrmSessionManagerProvider {
        @Override public DrmSessionManager get(MediaItem mediaItem) {
            Object tag = mediaItem.localConfiguration == null ? null : mediaItem.localConfiguration.tag;
            if (!(tag instanceof Channel)) return DrmSessionManager.DRM_UNSUPPORTED;
            Channel c = (Channel) tag;
            if (c.clearKey == null || c.clearKey.isEmpty()) return DrmSessionManager.DRM_UNSUPPORTED;
            try {
                byte[] json = buildClearKeyJson(c.clearKey).getBytes(StandardCharsets.UTF_8);
                return new DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(new LocalMediaDrmCallback(json));
            } catch (Exception e) {
                return DrmSessionManager.DRM_UNSUPPORTED;
            }
        }
    }

    private String buildClearKeyJson(String pair) throws Exception {
        String[] p = pair.trim().split(":",2);
        if (p.length != 2) throw new IllegalArgumentException("ClearKey ต้องเป็น KID:KEY");
        byte[] kid = hexToBytes(p[0]);
        byte[] key = hexToBytes(p[1]);
        String kid64 = Base64.encodeToString(kid, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        String key64 = Base64.encodeToString(key, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return "{\"keys\":[{\"kty\":\"oct\",\"kid\":\""+kid64+"\",\"k\":\""+key64+"\"}],\"type\":\"temporary\"}";
    }

    private byte[] hexToBytes(String s) {
        String x=s.replace("-","").trim();
        if ((x.length()&1)!=0) throw new IllegalArgumentException("hex ไม่สมบูรณ์");
        byte[] out=new byte[x.length()/2];
        for(int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(x.substring(i*2,i*2+2),16);
        return out;
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
    private void aboutDialog(){new AlertDialog.Builder(this).setTitle("เกี่ยวกับ").setMessage("Thai TV Player\nM3U / M3U8 / HLS / DASH ClearKey\nเวอร์ชันใหม่").setPositiveButton("ตกลง",null).show();}

    private void loadUrl(String u){if(u==null||u.isEmpty()){error("URL Playlist ว่าง");return;}status.setText("กำลังโหลด Playlist…");executor.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("User-Agent","Mozilla/5.0");c.connect();if(c.getResponseCode()<200||c.getResponseCode()>=400)throw new IOException("HTTP "+c.getResponseCode());String text=read(c.getInputStream());main.post(()->parseAndShow(text,"รายการช่อง"));c.disconnect();}catch(Exception e){main.post(()->error("โหลด Playlist ไม่ได้: "+e.getMessage()));}});}
    private String read(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);return new String(b.toByteArray(),StandardCharsets.UTF_8);}
    private void parseAndShow(String text,String name){try{ArrayList<Channel> out=parse(text);channels.clear();channels.addAll(out);showChannels(channels,name);if(channels.isEmpty())error("ไม่พบช่องใน Playlist");}catch(Exception e){error("อ่าน Playlist ไม่ได้: "+e.getMessage());}}
    private ArrayList<Channel> parse(String text){
        ArrayList<Channel> out=new ArrayList<>();String[] lines=text.replace("\r","").split("\n");Channel pending=null;
        for(String raw:lines){String line=raw.trim();if(line.isEmpty())continue;
            if(line.startsWith("#EXTINF")){String name=line.substring(line.lastIndexOf(",")+1).trim();String logo=attr(line,"tvg-logo");String id=attr(line,"tvg-id");String clear=attr(line,"clearkey");if(clear.isEmpty())clear=attr(line,"drm-clearkey");pending=new Channel(name,id,"",logo,clear);}
            else if(!line.startsWith("#")&&pending!=null){pending.url=line;out.add(pending);pending=null;}
        }return out;
    }
    private String attr(String line,String key){String[] keys={key,"\""+key+"\""};for(String k:keys){int p=line.indexOf(k+"=");if(p<0)continue;int s=p+k.length()+1;if(s>=line.length())continue;char q=line.charAt(s);if(q=='\"'||q=='\''){int e=line.indexOf(q,s+1);if(e>s)return line.substring(s+1,e);}int e=line.indexOf(' ',s);if(e<0)e=line.length();return line.substring(s,e).replace("\"","");}return "";}

    private void play(Channel c){if(c==null||c.url==null||c.url.isEmpty()){error("ไม่มี URL ของช่อง");return;}try{MediaItem.Builder b=new MediaItem.Builder().setUri(c.url).setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder().setTitle(c.name).build()).setTag(c);if(c.url.toLowerCase(Locale.ROOT).contains(".m3u8"))b.setMimeType(MimeTypes.APPLICATION_M3U8);player.setMediaItem(b.build());player.prepare();player.play();if(!history.contains(c)){history.add(0,c);if(history.size()>50)history.remove(history.size()-1);}}catch(Exception e){error("เปิดช่องไม่ได้: "+e.getMessage());}}
    private void stopPlayer(){if(player!=null){player.stop();player.clearMediaItems();}if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);}
    private void error(String s){if(status!=null){status.setText(s);status.setTextColor(0xffff5252);}}
    private View makeFatal(Throwable t){TextView v=tv("เปิดแอปไม่ได้\n"+(t.getMessage()==null?t.getClass().getSimpleName():t.getMessage()),16);v.setGravity(Gravity.CENTER);return v;}

    private class ChannelAdapter extends RecyclerView.Adapter<ChannelHolder>{private final ArrayList<Channel> data=new ArrayList<>();void setData(List<Channel> d){data.clear();data.addAll(d);notifyDataSetChanged();}public ChannelHolder onCreateViewHolder(android.view.ViewGroup p,int v){LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(5),dp(8),dp(5));ImageView im=new ImageView(MainActivity.this);im.setScaleType(ImageView.ScaleType.CENTER_INSIDE);TextView tx=tv("",16);row.addView(im,new LinearLayout.LayoutParams(dp(54),dp(54)));row.addView(tx,new LinearLayout.LayoutParams(0,dp(64),1));return new ChannelHolder(row,im,tx);}public void onBindViewHolder(ChannelHolder h,int pos){Channel c=data.get(pos);h.tx.setText((pos+1)+"  "+c.name);if(c.logo!=null&&!c.logo.isEmpty())Glide.with(MainActivity.this).load(c.logo).placeholder(android.R.drawable.ic_media_play).into(h.im);else h.im.setImageResource(android.R.drawable.ic_media_play);h.itemView.setOnClickListener(v->{play(c);if(getResources().getConfiguration().orientation!=Configuration.ORIENTATION_LANDSCAPE)setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);});}public int getItemCount(){return data.size();}}
    private static class ChannelHolder extends RecyclerView.ViewHolder{ImageView im;TextView tx;ChannelHolder(View v,ImageView i,TextView t){super(v);im=i;tx=t;}}
    private static class Channel{String name,id,url,logo,clearKey;Channel(String n,String i,String u){this(n,i,u,"","");}Channel(String n,String i,String u,String l,String k){name=n;id=i;url=u;logo=l;clearKey=k;}}
}

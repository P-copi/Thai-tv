package com.pcopi.thaitvnew;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int PICK_M3U = 7001;
    private static final String DEFAULT_PLAYLIST = "https://raw.githubusercontent.com/P-copi/Thai-tv/main/thai-tv-new.m3u";
    private DrawerLayout drawer; private RecyclerView recycler; private TextView status,title; private PlayerView playerView; private ExoPlayer player; private ChannelAdapter adapter;
    private final ArrayList<Channel> channels=new ArrayList<>(),favorites=new ArrayList<>(),history=new ArrayList<>();
    private final ExecutorService executor=Executors.newSingleThreadExecutor(); private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();loadUrl(DEFAULT_PLAYLIST);}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView tv(String s,float z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(0xffeeeeee);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(16),0,dp(16),0);return t;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextSize(14);return b;}
    private void buildUi(){
        drawer=new DrawerLayout(this);drawer.setBackgroundColor(0xff101010);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);drawer.addView(root,new DrawerLayout.LayoutParams(-1,-1));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(4),0,dp(4),0);bar.setBackgroundColor(0xff212121);
        TextView menu=tv("☰",28);menu.setGravity(Gravity.CENTER);bar.addView(menu,new LinearLayout.LayoutParams(dp(52),dp(56)));menu.setOnClickListener(v->drawer.openDrawer(Gravity.START));
        title=tv("Thai TV Player",20);title.setTypeface(null,1);bar.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));
        TextView search=tv("⌕",28);search.setGravity(Gravity.CENTER);bar.addView(search,new LinearLayout.LayoutParams(dp(52),dp(56)));search.setOnClickListener(v->searchDialog());root.addView(bar);
        playerView=new PlayerView(this);playerView.setUseController(true);playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);playerView.setBackgroundColor(Color.BLACK);root.addView(playerView,new LinearLayout.LayoutParams(-1,dp(220)));
        LinearLayout controls=new LinearLayout(this);controls.setPadding(dp(8),dp(4),dp(8),dp(4));Button file=btn("ไฟล์ M3U"),net=btn("URL Playlist"),refresh=btn("↻ รีเฟรช");controls.addView(file,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(net,new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(refresh,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(controls);
        file.setOnClickListener(v->pickFile());net.setOnClickListener(v->urlDialog());refresh.setOnClickListener(v->loadUrl(DEFAULT_PLAYLIST));
        status=tv("กำลังโหลดเพลย์ลิสต์…",14);status.setTextColor(0xffffb74d);root.addView(status,new LinearLayout.LayoutParams(-1,dp(42)));
        recycler=new RecyclerView(this);recycler.setLayoutManager(new LinearLayoutManager(this));adapter=new ChannelAdapter();recycler.setAdapter(adapter);root.addView(recycler,new LinearLayout.LayoutParams(-1,0,1));
        buildDrawer();setContentView(drawer);player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);
    }
    private void buildDrawer(){LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.VERTICAL);nav.setBackgroundColor(0xff181818);nav.setPadding(0,dp(30),0,dp(12));TextView head=tv("  Thai TV Player",22);head.setTypeface(null,1);head.setTextColor(0xffff9800);nav.addView(head,new LinearLayout.LayoutParams(-1,dp(65)));
        addNav(nav,"▣   รายการช่อง",()->showChannels(channels,"รายการช่อง"));addNav(nav,"★   รายการโปรด",()->showChannels(favorites,"รายการโปรด"));addNav(nav,"◷   ประวัติการรับชม",()->showChannels(history,"ประวัติการรับชม"));addNav(nav,"＋   เปิดไฟล์ M3U",this::pickFile);addNav(nav,"↗   เปิด URL Playlist",this::urlDialog);addNav(nav,"▶   เปิด URL สตรีม",this::streamDialog);addNav(nav,"⚙   ตั้งค่า",this::settingsDialog);addNav(nav,"ⓘ   เกี่ยวกับ",this::aboutDialog);DrawerLayout.LayoutParams lp=new DrawerLayout.LayoutParams(dp(300),-1);lp.gravity=Gravity.START;drawer.addView(nav,lp);}
    private void addNav(LinearLayout p,String s,View.OnClickListener c){TextView t=tv(s,16);t.setPadding(dp(22),0,dp(8),0);p.addView(t,new LinearLayout.LayoutParams(-1,dp(54)));t.setOnClickListener(v->{drawer.closeDrawers();c.onClick(v);});}
    private void showChannels(List<Channel> d,String n){title.setText(n);adapter.setData(d);status.setText(d.size()+" ช่อง");status.setTextColor(0xffffb74d);}
    private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_M3U);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==PICK_M3U&&res==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();executor.execute(()->{try(InputStream in=getContentResolver().openInputStream(u)){String text=read(in);main.post(()->parseAndShow(text,"ไฟล์ M3U"));}catch(Exception e){main.post(()->error("อ่านไฟล์ไม่ได้: "+e.getMessage()));}});}}
    private void urlDialog(){EditText e=new EditText(this);e.setSingleLine();e.setHint("https://.../playlist.m3u");new AlertDialog.Builder(this).setTitle("URL Playlist").setView(e).setNegativeButton("ยกเลิก",null).setPositiveButton("เปิด",(d,w)->loadUrl(e.getText().toString().trim())).show();}
    private void streamDialog(){EditText e=new EditText(this);e.setSingleLine();e.setHint("https://.../stream.m3u8");new AlertDialog.Builder(this).setTitle("เปิด URL สตรีม").setView(e).setNegativeButton("ยกเลิก",null).setPositiveButton("เล่น",(d,w)->play(new Channel("Network Stream","",e.getText().toString().trim()))).show();}
    private void searchDialog(){EditText e=new EditText(this);e.setSingleLine();e.setHint("ค้นหาช่อง");new AlertDialog.Builder(this).setTitle("ค้นหาช่อง").setView(e).setNegativeButton("ปิด",null).setPositiveButton("ค้นหา",(d,w)->{String q=e.getText().toString().toLowerCase(Locale.ROOT);ArrayList<Channel> r=new ArrayList<>();for(Channel c:channels)if(c.name.toLowerCase(Locale.ROOT).contains(q))r.add(c);showChannels(r,"ผลการค้นหา");}).show();}
    private void settingsDialog(){new AlertDialog.Builder(this).setTitle("ตั้งค่า").setMultiChoiceItems(new String[]{"เล่นช่องอัตโนมัติเมื่อเลือก","จำรายการโปรด","ใช้ตัวควบคุมแบบเต็มหน้าจอ"},new boolean[]{true,true,true},null).setPositiveButton("ตกลง",null).show();}
    private void aboutDialog(){new AlertDialog.Builder(this).setTitle("Thai TV Player").setMessage("เครื่องเล่น IPTV สำหรับไฟล์ M3U และ URL M3U8\n\nรองรับรายการช่อง, รายการโปรด, ประวัติ และการเปิดสตรีมโดยตรง").setPositiveButton("ตกลง",null).show();}
    private void loadUrl(String url){if(url==null||url.isEmpty()){error("URL ว่าง");return;}status.setText("กำลังโหลด playlist…");executor.execute(()->{try{String text=http(url);main.post(()->parseAndShow(text,"รายการช่อง"));}catch(Exception e){main.post(()->error("โหลดไม่ได้: "+e.getMessage()));}});}
    private String http(String s)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(s).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","ThaiTVPlayer/1.0");try(InputStream in=c.getInputStream()){return read(in);}finally{c.disconnect();}}
    private String read(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))!=-1)b.write(x,0,n);return b.toString(StandardCharsets.UTF_8.name());}
    private void parseAndShow(String text,String name){ArrayList<Channel> r=parse(text);if(r.isEmpty()){error("ไม่พบช่องใน M3U");return;}channels.clear();channels.addAll(r);showChannels(channels,name);status.setText("พบ "+r.size()+" ช่อง");}
    private ArrayList<Channel> parse(String text){ArrayList<Channel> r=new ArrayList<>();String[] lines=text.replace("\r","").split("\n");String n="ช่องไม่ระบุ",logo="";for(String raw:lines){String line=raw.trim();if(line.startsWith("#EXTINF")){int comma=line.indexOf(',');n=comma>=0?line.substring(comma+1).trim():"ช่อง";logo=attr(line,"tvg-logo");}else if((line.startsWith("http://")||line.startsWith("https://"))&&!line.startsWith("#")){r.add(new Channel(n,logo,line));n="ช่องไม่ระบุ";logo="";}}return r;}
    private String attr(String s,String key){String q=key+"=\"";int a=s.indexOf(q);if(a<0)return "";a+=q.length();int b=s.indexOf('"',a);return b>0?s.substring(a,b):"";}
    private void play(Channel c){if(c.url==null||c.url.isEmpty())return;MediaItem.Builder mb=new MediaItem.Builder().setUri(c.url);if(c.url.toLowerCase(Locale.ROOT).contains(".m3u8")||c.url.toLowerCase(Locale.ROOT).contains("m3u8?"))mb.setMimeType(MimeTypes.APPLICATION_M3U8);player.setMediaItem(mb.build());player.prepare();player.play();addHistory(c);title.setText(c.name);}
    private void addHistory(Channel c){for(int i=history.size()-1;i>=0;i--)if(history.get(i).url.equals(c.url))history.remove(i);history.add(0,c);if(history.size()>30)history.remove(history.size()-1);}
    private void toggleFav(Channel c){for(Channel x:favorites)if(x.url.equals(c.url)){favorites.remove(x);adapter.notifyDataSetChanged();return;}favorites.add(c);adapter.notifyDataSetChanged();}
    private boolean isFav(Channel c){for(Channel x:favorites)if(x.url.equals(c.url))return true;return false;}
    private void error(String s){status.setText(s);status.setTextColor(0xffff5252);}
    @Override protected void onDestroy(){super.onDestroy();if(player!=null)player.release();executor.shutdownNow();}
    static class Channel{String name,logo,url;Channel(String n,String l,String u){name=n;logo=l;url=u;}}
    class ChannelAdapter extends RecyclerView.Adapter<ChannelVH>{ArrayList<Channel> data=new ArrayList<>();void setData(List<Channel>d){data=new ArrayList<>(d);notifyDataSetChanged();}
        @Override public ChannelVH onCreateViewHolder(android.view.ViewGroup p,int v){LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(4),dp(8),dp(4));ImageView im=new ImageView(MainActivity.this);im.setScaleType(ImageView.ScaleType.CENTER_INSIDE);row.addView(im,new LinearLayout.LayoutParams(dp(58),dp(58)));LinearLayout mid=new LinearLayout(MainActivity.this);mid.setOrientation(LinearLayout.VERTICAL);TextView name=tv("",16),sub=tv("",12);sub.setTextColor(0xff9e9e9e);mid.addView(name,new LinearLayout.LayoutParams(-1,dp(34)));mid.addView(sub,new LinearLayout.LayoutParams(-1,dp(24)));row.addView(mid,new LinearLayout.LayoutParams(0,dp(66),1));TextView star=tv("☆",28);star.setGravity(Gravity.CENTER);row.addView(star,new LinearLayout.LayoutParams(dp(52),dp(66)));return new ChannelVH(row,im,name,sub,star);}
        @Override public void onBindViewHolder(ChannelVH h,int pos){Channel c=data.get(pos);h.name.setText(c.name);h.sub.setText(c.url);h.star.setText(isFav(c)?"★":"☆");if(c.logo!=null&&!c.logo.isEmpty())Glide.with(MainActivity.this).load(c.logo).placeholder(android.R.drawable.ic_menu_gallery).into(h.img);else h.img.setImageResource(android.R.drawable.ic_media_play);h.itemView.setOnClickListener(v->play(c));h.star.setOnClickListener(v->toggleFav(c));}
        @Override public int getItemCount(){return data.size();}}
    static class ChannelVH extends RecyclerView.ViewHolder{ImageView img;TextView name,sub,star;ChannelVH(View v,ImageView i,TextView n,TextView s,TextView f){super(v);img=i;name=n;sub=s;star=f;}}
}
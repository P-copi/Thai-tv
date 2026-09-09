package com.pcopi.thaitv;

import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    static class Channel { String name,url,logo; Channel(String n,String u,String l){name=n;url=u;logo=l;} }
    private ExoPlayer player; private PlayerView playerView; private LinearLayout list; private TextView status;
    private final ArrayList<Channel> channels=new ArrayList<>(); private final ExecutorService io=Executors.newSingleThreadExecutor();
    private static final int PICK_M3U=1001;
    @Override public void onCreate(@Nullable Bundle b){super.onCreate(b);buildUi();}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private Button btn(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);b.setOnClickListener(l);return b;}
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff101010);
        Toolbar bar=new Toolbar(this);bar.setTitle("Thai TV Player");bar.setTitleTextColor(Color.WHITE);bar.setBackgroundColor(0xff171717);root.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));setSupportActionBar(bar);
        playerView=new PlayerView(this);playerView.setUseController(true);root.addView(playerView,new LinearLayout.LayoutParams(-1,dp(230)));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);actions.addView(btn("ไฟล์ M3U",v->pickFile()));actions.addView(btn("URL Playlist",v->urlDialog()));actions.addView(btn("ล้างรายการ",v->{channels.clear();refreshList();}));root.addView(actions,new LinearLayout.LayoutParams(-1,dp(58)));
        status=new TextView(this);status.setText("เลือกไฟล์ M3U หรือใส่ URL Playlist เพื่อเริ่มใช้งาน");status.setTextColor(0xffdddddd);status.setPadding(dp(12),dp(4),dp(12),dp(4));root.addView(status);
        ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(8),dp(4),dp(8),dp(20));sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);
    }
    private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,PICK_M3U);}
    @Override protected void onActivityResult(int r,int c,@Nullable Intent d){super.onActivityResult(r,c,d);if(r==PICK_M3U&&c==RESULT_OK&&d!=null&&d.getData()!=null){Uri u=d.getData();io.execute(()->{try(InputStream in=getContentResolver().openInputStream(u)){ArrayList<Channel>x=parse(read(in));runOnUiThread(()->setChannels(x,"นำเข้าไฟล์สำเร็จ "+x.size()+" ช่อง"));}catch(Exception e){runOnUiThread(()->toast("อ่านไฟล์ไม่ได้: "+e.getMessage()));}});}}
    private void urlDialog(){EditText e=new EditText(this);e.setHint("https://.../playlist.m3u");e.setSingleLine(true);new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("URL Playlist").setView(e).setNegativeButton("ยกเลิก",null).setPositiveButton("เปิด",(d,w)->loadUrl(e.getText().toString().trim())).show();}
    private void loadUrl(String u){if(u.isEmpty())return;status.setText("กำลังโหลด Playlist...");io.execute(()->{try{HttpURLConnection h=(HttpURLConnection)new URL(u).openConnection();h.setConnectTimeout(12000);h.setReadTimeout(20000);h.setRequestProperty("User-Agent","ThaiTVPlayer/2.0");ArrayList<Channel>x=parse(read(h.getInputStream()));runOnUiThread(()->setChannels(x,"โหลดสำเร็จ "+x.size()+" ช่อง"));}catch(Exception e){runOnUiThread(()->toast("โหลด Playlist ไม่สำเร็จ: "+e.getMessage()));}});}
    private String read(InputStream in)throws IOException{BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String l;while((l=r.readLine())!=null)s.append(l).append('\n');return s.toString();}
    private ArrayList<Channel> parse(String s){ArrayList<Channel>out=new ArrayList<>();String name=null,logo="";for(String raw:s.replace("\r","").split("\n")){String line=raw.trim();if(line.isEmpty())continue;if(line.startsWith("#EXTINF")){int k=line.indexOf(',');name=k>=0?line.substring(k+1).trim():"ช่อง";logo=attr(line,"tvg-logo");}else if(!line.startsWith("#")&&name!=null){out.add(new Channel(name,line,logo));name=null;logo="";}}return out;}
    private String attr(String s,String key){String q=key+"=\"";int i=s.indexOf(q);if(i>=0){int j=s.indexOf('"',i+q.length());if(j>i)return s.substring(i+q.length(),j);}return "";}
    private void setChannels(ArrayList<Channel>x,String msg){channels.clear();channels.addAll(x);refreshList();status.setText(msg);if(!channels.isEmpty())play(channels.get(0));}
    private void refreshList(){list.removeAllViews();for(int i=0;i<channels.size();i++){Channel c=channels.get(i);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(6),dp(4),dp(6),dp(4));ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_INSIDE);row.addView(im,new LinearLayout.LayoutParams(dp(58),dp(52)));if(!c.logo.isEmpty())Glide.with(this).load(c.logo).placeholder(android.R.drawable.ic_media_play).into(im);else im.setImageResource(android.R.drawable.ic_media_play);TextView t=new TextView(this);t.setText((i+1)+". "+c.name);t.setTextColor(Color.WHITE);t.setTextSize(16);row.addView(t,new LinearLayout.LayoutParams(0,dp(60),1));row.setOnClickListener(v->play(c));list.addView(row,new LinearLayout.LayoutParams(-1,dp(64)));}}
    private void play(Channel c){try{player.setMediaItem(MediaItem.fromUri(c.url));player.prepare();player.play();status.setText("กำลังเล่น: "+c.name);}catch(Exception e){toast("เปิดช่องไม่ได้: "+e.getMessage());}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();status.setText(s);}
    @Override protected void onDestroy(){if(player!=null)player.release();io.shutdownNow();super.onDestroy();}
    @Override public boolean onCreateOptionsMenu(Menu m){m.add("โหลด Playlist URL");m.add("ข้อมูลแอป");return true;}
    @Override public boolean onOptionsItemSelected(MenuItem i){if(i.getTitle().toString().startsWith("โหลด"))urlDialog();else new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Thai TV Player 2.0").setMessage("เล่น M3U / M3U8 พร้อมรายการช่องและโลโก้").setPositiveButton("ตกลง",null).show();return true;}
}

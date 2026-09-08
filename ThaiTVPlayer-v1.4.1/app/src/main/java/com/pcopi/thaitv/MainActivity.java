package com.pcopi.thaitv;

import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
 static class Channel { String name,url; Channel(String n,String u){name=n;url=u;} }
 ArrayList<Channel> channels=new ArrayList<>(); LinearLayout root,listBox; FrameLayout playerBox; PlayerView playerView; ExoPlayer player; TextView status; Button closePlayer;
 int PICK_M3U=1001;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 int wrap(){return LinearLayout.LayoutParams.WRAP_CONTENT;}
 TextView text(String s,int z){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(Color.DKGRAY);t.setPadding(18,12,18,12);return t;}
 Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
 @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();}
 void buildUi(){
  root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
  LinearLayout bar=new LinearLayout(this);bar.setPadding(6,6,6,2);
  TextView title=text("Thai TV Player 1.4.1",19);title.setTextColor(Color.BLACK);bar.addView(title,new LinearLayout.LayoutParams(0,wrap(),1));
  Button file=button("นำเข้า M3U"), url=button("นำเข้า URL");bar.addView(file,new LinearLayout.LayoutParams(0,wrap(),1));bar.addView(url,new LinearLayout.LayoutParams(0,wrap(),1));root.addView(bar,new LinearLayout.LayoutParams(-1,wrap()));
  status=text("เลือก M3U หรือ URL เพื่อเริ่มใช้งาน",14);root.addView(status,new LinearLayout.LayoutParams(-1,wrap()));
  playerBox=new FrameLayout(this);playerBox.setBackgroundColor(Color.BLACK);playerView=new PlayerView(this);playerView.setUseController(true);playerBox.addView(playerView,new FrameLayout.LayoutParams(-1,dp(220)));
  closePlayer=button("✕ ปิดจอ");closePlayer.setTextColor(Color.WHITE);closePlayer.setBackgroundColor(Color.argb(190,0,0,0));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(dp(105),dp(48),Gravity.TOP|Gravity.END);cp.setMargins(0,dp(5),dp(5),0);playerBox.addView(closePlayer,cp);root.addView(playerBox,new LinearLayout.LayoutParams(-1,dp(230)));
  listBox=new LinearLayout(this);listBox.setOrientation(LinearLayout.VERTICAL);ScrollView scroll=new ScrollView(this);scroll.addView(listBox);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  file.setOnClickListener(v->pickFile());url.setOnClickListener(v->askUrl());closePlayer.setOnClickListener(v->hidePlayer());
 }
 void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/*");startActivityForResult(i,PICK_M3U);}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==PICK_M3U&&c==RESULT_OK&&d!=null){try{loadText(read(getContentResolver().openInputStream(d.getData())));}catch(Exception e){status.setText("เปิดไฟล์ไม่สำเร็จ: "+e.getMessage());}}}
 String read(InputStream in)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);return new String(o.toByteArray(),StandardCharsets.UTF_8);}
 void askUrl(){EditText e=new EditText(this);e.setHint("https://.../playlist.m3u");new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("นำเข้า Playlist จาก URL").setView(e).setNegativeButton("ยกเลิก",null).setPositiveButton("โหลด",(d,w)->fetchUrl(e.getText().toString().trim())).show();}
 void fetchUrl(String u){if(u.isEmpty())return;status.setText("กำลังโหลด URL...");Executors.newSingleThreadExecutor().execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","ThaiTVPlayer/1.4.1");String s=read(c.getInputStream());runOnUiThread(()->loadText(s));}catch(Exception e){runOnUiThread(()->status.setText("โหลด URL ไม่สำเร็จ: "+e.getMessage()));}});}
 void loadText(String s){channels.clear();String name=null;for(String raw:s.split("\\r?\\n")){String line=raw.trim();if(line.startsWith("#EXTINF")){int p=line.indexOf(',');name=p>=0?line.substring(p+1).trim():"ช่องทีวี";}else if(!line.isEmpty()&&!line.startsWith("#")&&(line.startsWith("http://")||line.startsWith("https://"))){if(name==null)name="ช่องทีวี";channels.add(new Channel(name,line));name=null;}}renderList();status.setText(channels.size()+" ช่อง");}
 void renderList(){listBox.removeAllViews();for(int i=0;i<channels.size();i++){Channel ch=channels.get(i);Button b=button((i+1)+"  "+ch.name);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);listBox.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));b.setOnClickListener(v->play(ch));}}
 void play(Channel ch){if(player!=null)player.release();player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);player.setMediaItem(MediaItem.fromUri(Uri.parse(ch.url)));player.prepare();player.play();status.setText("กำลังเล่น: "+ch.name);}
 void hidePlayer(){if(player!=null){player.stop();player.release();player=null;}playerView.setPlayer(null);}
 @Override public void onBackPressed(){if(player!=null){hidePlayer();return;}super.onBackPressed();}
 @Override protected void onDestroy(){if(player!=null)player.release();super.onDestroy();}
 @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(playerBox==null)return;boolean land=c.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;playerBox.getLayoutParams().height=land?-1:dp(230);playerBox.requestLayout();getWindow().getDecorView().setSystemUiVisibility(land?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION):0);}
}

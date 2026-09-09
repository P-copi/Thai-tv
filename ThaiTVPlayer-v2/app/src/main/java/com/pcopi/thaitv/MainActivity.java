package com.pcopi.thaitv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    static final int BG = Color.rgb(18,18,18), PANEL = Color.rgb(30,30,30), TEXT = Color.WHITE, MUTED = Color.rgb(180,180,180), GREEN = Color.rgb(0,200,83);
    DrawerLayout drawer; LinearLayout root, listPanel, menuPanel; PlayerView playerView; ExoPlayer player;
    EditText search; Spinner groupSpinner; ChannelAdapter adapter; ArrayList<Channel> all = new ArrayList<>(), shown = new ArrayList<>();
    ArrayList<String> groups = new ArrayList<>(); SharedPreferences prefs; ExecutorService executor = Executors.newSingleThreadExecutor();
    TextView title; boolean fullscreen=false;
    ActivityResultLauncher<String[]> picker;

    @Override public void onCreate(Bundle b) { super.onCreate(b); requestWindowFeature(Window.FEATURE_NO_TITLE); getWindow().setStatusBarColor(BG); prefs=getSharedPreferences("thai_tv",MODE_PRIVATE); buildUi();
        picker=registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> { if(uri!=null) readLocal(uri); });
    }

    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    TextView tv(String s,float size,int color){ TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(dp(14),0,dp(14),0); return v; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setTextColor(TEXT); b.setTextSize(14); b.setAllCaps(false); b.setBackgroundColor(PANEL); return b; }

    void buildUi(){
        drawer=new DrawerLayout(this); drawer.setBackgroundColor(BG);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(6),0,dp(6),0); bar.setBackgroundColor(Color.rgb(24,24,24));
        Button menu=btn("☰"); menu.setTextSize(24); bar.addView(menu,new LinearLayout.LayoutParams(dp(52),dp(56))); menu.setOnClickListener(v->drawer.openDrawer(menuPanel));
        title=tv("Thai TV Player",20,TEXT); title.setTypeface(null,1); bar.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));
        Button fs=btn("⛶"); fs.setTextSize(23); bar.addView(fs,new LinearLayout.LayoutParams(dp(52),dp(56))); fs.setOnClickListener(v->toggleFullscreen());
        root.addView(bar);
        playerView=new PlayerView(this); playerView.setUseController(true); playerView.setBackgroundColor(Color.BLACK); root.addView(playerView,new LinearLayout.LayoutParams(-1,dp(230)));
        LinearLayout controls=new LinearLayout(this); controls.setPadding(dp(6),dp(5),dp(6),dp(5)); controls.setBackgroundColor(PANEL);
        search=new EditText(this); search.setHint("ค้นหาช่อง..."); search.setHintTextColor(MUTED); search.setTextColor(TEXT); search.setSingleLine(true); search.setBackgroundColor(Color.TRANSPARENT); controls.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        Button clear=btn("✕"); controls.addView(clear,new LinearLayout.LayoutParams(dp(48),dp(48))); clear.setOnClickListener(v->search.setText("")); root.addView(controls);
        groupSpinner=new Spinner(this); root.addView(groupSpinner,new LinearLayout.LayoutParams(-1,dp(48))); groupSpinner.setBackgroundColor(PANEL); groupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?>p){} public void onItemSelected(AdapterView<?>p,View v,int pos,long id){filter();}});
        listPanel=new LinearLayout(this); listPanel.setOrientation(LinearLayout.VERTICAL); root.addView(listPanel,new LinearLayout.LayoutParams(-1,0,1));
        ListView lv=new ListView(this); lv.setDivider(null); adapter=new ChannelAdapter(this,shown); lv.setAdapter(adapter); lv.setOnItemClickListener((p,v,pos,id)->play(shown.get(pos))); listPanel.addView(lv,new LinearLayout.LayoutParams(-1,0,1));
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){} public void onTextChanged(CharSequence s,int a,int b,int c){filter();} public void afterTextChanged(android.text.Editable e){}});
        buildMenu(); drawer.addView(root,new DrawerLayout.LayoutParams(-1,-1)); drawer.addView(menuPanel,new DrawerLayout.LayoutParams(dp(300),-1,Gravity.LEFT)); setContentView(drawer);
        player=new ExoPlayer.Builder(this).build(); playerView.setPlayer(player);
        loadSavedPlaylist();
    }

    void buildMenu(){
        menuPanel=new LinearLayout(this); menuPanel.setOrientation(LinearLayout.VERTICAL); menuPanel.setPadding(dp(10),dp(25),dp(10),dp(10)); menuPanel.setBackgroundColor(Color.rgb(25,25,25));
        TextView h=tv("Thai TV Player",22,TEXT); h.setTypeface(null,1); h.setPadding(dp(12),dp(8),dp(12),dp(20)); menuPanel.addView(h);
        TextView sub=tv("เมนูหลัก",13,MUTED); menuPanel.addView(sub,new LinearLayout.LayoutParams(-1,dp(34)));
        addMenu("📂  เปิดไฟล์ M3U / M3U8",v->{drawer.closeDrawers(); picker.launch(new String[]{"*/*"});});
        addMenu("🌐  เพิ่ม URL Playlist",v->{drawer.closeDrawers(); urlDialog(false);});
        addMenu("▶  เปิด URL M3U8 โดยตรง",v->{drawer.closeDrawers(); urlDialog(true);});
        addMenu("↻  รีเฟรช Playlist",v->{loadSavedPlaylist();});
        addMenu("★  รายการโปรด",v->{drawer.closeDrawers(); showFavorites();});
        addMenu("◷  ประวัติการรับชม",v->{drawer.closeDrawers(); showHistory();});
        addMenu("⌕  ค้นหาช่อง",v->{drawer.closeDrawers(); search.requestFocus();});
        addMenu("⚙  ตั้งค่า",v->{drawer.closeDrawers(); settingsDialog();});
        Space sp=new Space(this); menuPanel.addView(sp,new LinearLayout.LayoutParams(1,0,1));
        TextView note=tv("รองรับ M3U / M3U8 • HLS\nใช้เฉพาะสตรีมที่คุณมีสิทธิ์เข้าถึง",12,MUTED); note.setPadding(dp(12),dp(12),dp(12),dp(20)); menuPanel.addView(note);
    }
    void addMenu(String s,View.OnClickListener l){Button b=btn(s); b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); b.setPadding(dp(14),0,dp(8),0); menuPanel.addView(b,new LinearLayout.LayoutParams(-1,dp(52))); b.setOnClickListener(l);}

    void urlDialog(boolean direct){
        LinearLayout box=new LinearLayout(this); box.setPadding(dp(20),dp(5),dp(20),0); box.setOrientation(LinearLayout.VERTICAL);
        EditText e=new EditText(this); e.setSingleLine(true); e.setHint(direct?"https://example.com/live.m3u8":"https://example.com/playlist.m3u"); box.addView(e);
        new AlertDialog.Builder(this).setTitle(direct?"เปิด M3U8 โดยตรง":"เพิ่ม URL Playlist").setView(box).setNegativeButton("ยกเลิก",null).setPositiveButton("เปิด",(d,w)->{String u=e.getText().toString().trim(); if(!u.isEmpty()){if(direct) playUrl(u); else loadUrl(u);}}).show();
    }
    void settingsDialog(){ new AlertDialog.Builder(this).setTitle("ตั้งค่า").setItems(new String[]{"ล้างรายการโปรด","ล้างประวัติ","ล้าง Playlist ที่บันทึกไว้"},(d,w)->{if(w==0)prefs.edit().remove("fav").apply(); if(w==1)prefs.edit().remove("history").apply(); if(w==2){prefs.edit().remove("playlist_url").apply(); all.clear();filter();}}).setNegativeButton("ปิด",null).show(); }

    void readLocal(Uri uri){ executor.execute(()->{try{InputStream in=getContentResolver().openInputStream(uri); String text=readAll(in); runOnUiThread(()->setPlaylist(parse(text),"ไฟล์ M3U"));}catch(Exception e){toast("เปิดไฟล์ไม่ได้: "+e.getMessage());}}); }
    void loadUrl(String u){ prefs.edit().putString("playlist_url",u).apply(); executor.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(12000); c.setReadTimeout(20000); c.setRequestProperty("User-Agent","ThaiTVPlayer/2.0"); String text=readAll(c.getInputStream()); runOnUiThread(()->setPlaylist(parse(text),"URL Playlist"));}catch(Exception e){toast("โหลด Playlist ไม่สำเร็จ: "+e.getMessage());}}); }
    void loadSavedPlaylist(){String u=prefs.getString("playlist_url",""); if(!u.isEmpty()) loadUrl(u); else {all.clear(); filter();}}
    String readAll(InputStream in)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8")); StringBuilder s=new StringBuilder(); String x; while((x=r.readLine())!=null)s.append(x).append('\n'); r.close(); return s.toString();}

    ArrayList<Channel> parse(String text){
        ArrayList<Channel> out=new ArrayList<>(); String name=null, logo="", group="ทั่วไป"; Matcher m;
        String[] lines=text.replace("\r","").split("\n");
        for(int i=0;i<lines.length;i++){String line=lines[i].trim(); if(line.startsWith("#EXTINF")){int comma=line.indexOf(','); name=comma>=0?line.substring(comma+1).trim():"ช่อง"; logo=attr(line,"tvg-logo"); group=attr(line,"group-title"); if(group.isEmpty())group="ทั่วไป";} else if(!line.isEmpty()&&!line.startsWith("#")&&name!=null){out.add(new Channel(name,line,logo,group)); name=null;}}
        return out;
    }
    String attr(String s,String key){Matcher m=Pattern.compile(key+"=\\\"([^\\\"]*)\\\"",Pattern.CASE_INSENSITIVE).matcher(s); return m.find()?m.group(1):"";}
    void setPlaylist(ArrayList<Channel> c,String source){all.clear();all.addAll(c); title.setText("Thai TV Player  •  "+c.size()+" ช่อง"); rebuildGroups(); filter(); toast("โหลด Playlist สำเร็จ "+c.size()+" ช่อง");}
    void rebuildGroups(){groups.clear();groups.add("ทั้งหมด");for(Channel c:all)if(!groups.contains(c.group))groups.add(c.group); ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,groups){public View getView(int p,View v,android.view.ViewGroup parent){TextView t=(TextView)super.getView(p,v,parent);t.setTextColor(TEXT);t.setPadding(dp(14),0,dp(14),0);return t;}};groupSpinner.setAdapter(a);}
    void filter(){String q=search==null?"":search.getText().toString().toLowerCase(Locale.ROOT);String g=(groupSpinner!=null&&groupSpinner.getSelectedItem()!=null)?groupSpinner.getSelectedItem().toString():"ทั้งหมด";shown.clear();for(Channel c:all)if((g.equals("ทั้งหมด")||c.group.equals(g))&&(q.isEmpty()||c.name.toLowerCase(Locale.ROOT).contains(q)))shown.add(c);if(adapter!=null)adapter.notifyDataSetChanged();}

    void play(Channel c){playUrl(c.url); prefs.edit().putString("last_name",c.name).apply(); addHistory(c);}
    void playUrl(String u){if(u==null||u.isEmpty())return; try{MediaItem.Builder b=new MediaItem.Builder().setUri(Uri.parse(u)); if(u.toLowerCase(Locale.ROOT).contains("m3u8"))b.setMimeType(MimeTypes.APPLICATION_M3U8); player.setMediaItem(b.build());player.prepare();player.play();}catch(Exception e){toast("เปิดช่องไม่ได้: "+e.getMessage());}}
    void addHistory(Channel c){String old=prefs.getString("history","");LinkedHashSet<String>s=new LinkedHashSet<>(Arrays.asList(old.split("\\n")));s.remove(c.name+"|"+c.url);s.add(c.name+"|"+c.url);StringBuilder b=new StringBuilder();for(String x:s)if(!x.isEmpty())b.append(x).append('\n');prefs.edit().putString("history",b.toString()).apply();}
    boolean isFav(Channel c){return prefs.getString("fav","").contains("\n"+c.url+"\n")||prefs.getString("fav","").equals(c.url);}
    void toggleFav(Channel c){String f=prefs.getString("fav","");HashSet<String>s=new HashSet<>(Arrays.asList(f.split("\\n")));if(s.contains(c.url))s.remove(c.url);else s.add(c.url);StringBuilder b=new StringBuilder();for(String x:s)if(!x.isEmpty())b.append(x).append('\n');prefs.edit().putString("fav",b.toString()).apply();}
    void showFavorites(){String f=prefs.getString("fav","");shown.clear();for(Channel c:all)if(f.contains(c.url))shown.add(c);adapter.notifyDataSetChanged();toast("รายการโปรด "+shown.size()+" ช่อง");}
    void showHistory(){String h=prefs.getString("history","");shown.clear();for(String x:h.split("\\n")){int p=x.indexOf('|');if(p>0)shown.add(new Channel(x.substring(0,p),x.substring(p+1),"","ประวัติ"));}adapter.notifyDataSetChanged();toast("ประวัติ "+shown.size()+" ช่อง");}

    void toggleFullscreen(){fullscreen=!fullscreen;getWindow().setFlags(fullscreen?WindowManager.LayoutParams.FLAG_FULLSCREEN:0,WindowManager.LayoutParams.FLAG_FULLSCREEN);setRequestedOrientation(fullscreen?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}
    void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
    @Override protected void onDestroy(){super.onDestroy();if(player!=null)player.release();executor.shutdownNow();}

    static class Channel{String name,url,logo,group;Channel(String n,String u,String l,String g){name=n;url=u;logo=l;group=g;}}
    class ChannelAdapter extends BaseAdapter{
        Context ctx;ArrayList<Channel> data;ChannelAdapter(Context c,ArrayList<Channel>d){ctx=c;data=d;}
        public int getCount(){return data.size();} public Object getItem(int p){return data.get(p);} public long getItemId(int p){return p;}
        public View getView(int p,View v,android.view.ViewGroup parent){LinearLayout row=new LinearLayout(ctx);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(5),dp(8),dp(5));row.setBackgroundColor(Color.rgb(23,23,23));
            ImageView im=new ImageView(ctx);im.setImageResource(android.R.drawable.ic_media_play);im.setBackgroundColor(Color.rgb(40,40,40));row.addView(im,new LinearLayout.LayoutParams(dp(54),dp(54)));
            LinearLayout tx=new LinearLayout(ctx);tx.setOrientation(LinearLayout.VERTICAL);TextView n=tv(data.get(p).name,16,TEXT);n.setTypeface(null,1);TextView g=tv(data.get(p).group,12,MUTED);tx.addView(n,new LinearLayout.LayoutParams(-1,dp(32)));tx.addView(g,new LinearLayout.LayoutParams(-1,dp(24)));row.addView(tx,new LinearLayout.LayoutParams(0,dp(64),1));
            Button fav=btn(isFav(data.get(p))?"★":"☆");fav.setTextSize(25);fav.setTextColor(isFav(data.get(p))?Color.YELLOW:MUTED);row.addView(fav,new LinearLayout.LayoutParams(dp(55),dp(64)));fav.setOnClickListener(x->{toggleFav(data.get(p));notifyDataSetChanged();});
            String logo=data.get(p).logo; if(logo!=null&&!logo.isEmpty()){final Channel cc=data.get(p); executor.execute(()->{try{Bitmap bm=BitmapFactory.decodeStream(new URL(logo).openStream());runOnUiThread(()->{if(bm!=null)im.setImageBitmap(bm);});}catch(Exception ignored){}});}
            return row;}
    }
}

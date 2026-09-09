package com.pcopi.thaitv;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivityV21 extends AppCompatActivity {
  final int BG=Color.rgb(15,15,15),PANEL=Color.rgb(28,28,28),CARD=Color.rgb(36,36,36),TEXT=Color.WHITE,MUTED=Color.rgb(175,175,175),ACCENT=Color.rgb(0,200,83);
  DrawerLayout drawer; LinearLayout root,content,side; PlayerView playerView; ExoPlayer player; TextView title,status; EditText search; Spinner groups;
  ArrayList<Channel> channels=new ArrayList<>(),shown=new ArrayList<>(); ArrayList<MediaFile> media=new ArrayList<>(); ArrayList<String> groupNames=new ArrayList<>();
  BaseAdapter channelAdapter; SharedPreferences prefs; ExecutorService io=Executors.newSingleThreadExecutor(); int tab=1; boolean full;
  ActivityResultLauncher<String[]> m3uPicker,mediaPicker; static final int PERM=91;

  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  TextView text(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setPadding(dp(12),0,dp(12),0);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
  Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(TEXT);b.setTextSize(13);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setBackgroundColor(PANEL);return b;}

  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);prefs=getSharedPreferences("thai_tv",0);makeUi();
    m3uPicker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),u->{if(u!=null){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);}catch(Exception e){}readLocal(u);}});
    mediaPicker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),u->{if(u!=null)playUri(u);}); requestPermission(); loadSaved(); scanMedia(); }

  void makeUi(){drawer=new DrawerLayout(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setBackgroundColor(Color.rgb(22,22,22));Button menu=button("☰");menu.setTextSize(24);top.addView(menu,new LinearLayout.LayoutParams(dp(54),dp(56)));menu.setOnClickListener(v->drawer.openDrawer(side));title=text("Thai TV Player",19,TEXT);title.setTypeface(null,1);top.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));Button fs=button("⛶");fs.setTextSize(21);top.addView(fs,new LinearLayout.LayoutParams(dp(54),dp(56)));fs.setOnClickListener(v->full());root.addView(top);
    playerView=new PlayerView(this);playerView.setUseController(true);playerView.setBackgroundColor(Color.BLACK);root.addView(playerView,new LinearLayout.LayoutParams(-1,dp(220)));status=text("พร้อมใช้งาน",12,MUTED);root.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));
    content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));nav();makeSide();drawer.addView(root,new DrawerLayout.LayoutParams(-1,-1));drawer.addView(side,new DrawerLayout.LayoutParams(dp(310),-1,Gravity.LEFT));setContentView(drawer);player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);player.addListener(new Player.Listener(){public void onPlayerError(PlaybackException e){toast("เปิดสตรีมไม่ได้: "+e.getErrorCodeName());}});showTab(1); }

  void nav(){LinearLayout n=new LinearLayout(this);n.setBackgroundColor(Color.rgb(25,25,25));String[] a={"▣\nวิดีโอ","▤\nPlaylist","⌕\nเรียกดู","♫\nเสียง","⋮\nอื่นๆ"};for(int i=0;i<5;i++){final int x=i;Button b=button(a[i]);b.setTextSize(11);n.addView(b,new LinearLayout.LayoutParams(0,dp(66),1));b.setOnClickListener(v->showTab(x));}root.addView(n,new LinearLayout.LayoutParams(-1,dp(66)));}
  void makeSide(){side=new LinearLayout(this);side.setOrientation(LinearLayout.VERTICAL);side.setPadding(dp(10),dp(22),dp(10),dp(10));side.setBackgroundColor(Color.rgb(24,24,24));TextView h=text("Thai TV Player",23,TEXT);h.setTypeface(null,1);side.addView(h,new LinearLayout.LayoutParams(-1,dp(58)));side.addView(text("เมนูหลัก",13,MUTED),new LinearLayout.LayoutParams(-1,dp(35)));sideAdd("▣  วิดีโอ",0);sideAdd("▤  Playlist",1);sideAdd("⌕  เรียกดู",2);sideAdd("♫  เสียง",3);sideAdd("⋮  อื่นๆ",4);sideAdd("📂  เปิดไฟล์ M3U / M3U8",9);sideAdd("🌐  เพิ่ม URL Playlist",8);}
  void sideAdd(String s,int x){Button b=button(s);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);side.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));b.setOnClickListener(v->{drawer.closeDrawers();if(x==8)urlDialog(false);else if(x==9)m3uPicker.launch(new String[]{"*/*"});else showTab(x);});}

  void showTab(int t){tab=t;content.removeAllViews();if(t==1)playlist();else if(t==2)browse();else if(t==0||t==3)mediaTab(t==3);else other();}
  void playlist(){LinearLayout tools=new LinearLayout(this);Button f=button("📂 M3U");Button u=button("🌐 URL");Button d=button("▶ M3U8");tools.addView(f,new LinearLayout.LayoutParams(0,dp(46),1));tools.addView(u,new LinearLayout.LayoutParams(0,dp(46),1));tools.addView(d,new LinearLayout.LayoutParams(0,dp(46),1));content.addView(tools);f.setOnClickListener(v->m3uPicker.launch(new String[]{"*/*"}));u.setOnClickListener(v->urlDialog(false));d.setOnClickListener(v->urlDialog(true));
    search=new EditText(this);search.setSingleLine(true);search.setHint("ค้นหาช่อง…");search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setBackgroundColor(PANEL);content.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){filter();}public void afterTextChanged(Editable e){}});
    groups=new Spinner(this);groups.setBackgroundColor(PANEL);content.addView(groups,new LinearLayout.LayoutParams(-1,dp(44)));groups.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?>p){}public void onItemSelected(AdapterView<?>p,View v,int x,long id){filter();}});
    ListView list=new ListView(this);list.setDivider(null);channelAdapter=new ChannelAdapter(this,shown);list.setAdapter(channelAdapter);list.setOnItemClickListener((p,v,pos,id)->play(shown.get(pos)));content.addView(list,new LinearLayout.LayoutParams(-1,0,1));rebuildGroups();filter();}
  void browse(){content.addView(text("เรียกดูไฟล์ในเครื่อง",18,TEXT),new LinearLayout.LayoutParams(-1,dp(50)));action("📄  เปิด M3U / M3U8",v->m3uPicker.launch(new String[]{"*/*"}));action("🎬  เปิดวิดีโอ",v->mediaPicker.launch(new String[]{"video/*"}));action("♫  เปิดเสียง",v->mediaPicker.launch(new String[]{"audio/*"}));action("↻  สแกนอุปกรณ์ใหม่",v->scanMedia());status.setText("สแกนสื่ออัตโนมัติทุกครั้งที่เปิดแอป");}
  void other(){content.addView(text("อื่นๆ",18,TEXT),new LinearLayout.LayoutParams(-1,dp(50)));action("▶  เปิด URL M3U8 โดยตรง",v->urlDialog(true));action("↻  รีเฟรช Playlist",v->loadSaved());action("★  รายการโปรด",v->favorites());action("◷  ประวัติการรับชม",v->history());action("⚙  ตั้งค่า / ล้างข้อมูล",v->settings());action("ℹ  Thai TV Player 2.1",v->new AlertDialog.Builder(this).setTitle("Thai TV Player").setMessage("M3U • M3U8 • HLS\nสแกน Video/Audio อัตโนมัติ\nVersion 2.1.0").setPositiveButton("ตกลง",null).show());}
  void action(String s,View.OnClickListener l){Button b=button(s);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);content.addView(b,new LinearLayout.LayoutParams(-1,dp(55)));b.setOnClickListener(l);}

  void urlDialog(boolean direct){EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(direct?"https://example.com/live.m3u8":"https://example.com/playlist.m3u");new AlertDialog.Builder(this).setTitle(direct?"เปิด M3U8 โดยตรง":"เพิ่ม URL Playlist").setView(e).setNegativeButton("ยกเลิก",null).setPositiveButton("เปิด",(x,w)->{String s=e.getText().toString().trim();if(!s.isEmpty()){if(direct)playUrl(s);else loadUrl(s);}}).show();}
  void readLocal(Uri u){io.execute(()->{try{String s=read(getContentResolver().openInputStream(u));ArrayList<Channel> c=parse(s);prefs.edit().putString("playlist_uri",u.toString()).remove("playlist_url").apply();runOnUiThread(()->{setChannels(c,"ไฟล์");});}catch(Exception e){toast("อ่าน M3U ไม่สำเร็จ: "+e.getMessage());}});}
  void loadUrl(String u){prefs.edit().putString("playlist_url",u).remove("playlist_uri").apply();io.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setInstanceFollowRedirects(true);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","Mozilla/5.0 ThaiTVPlayer/2.1");c.setRequestProperty("Accept","*/*");String s=read(c.getInputStream());ArrayList<Channel> ch=parse(s);runOnUiThread(()->setChannels(ch,"URL"));}catch(Exception e){toast("โหลด Playlist ไม่สำเร็จ: "+e.getMessage());}});}
  void loadSaved(){String p=prefs.getString("playlist_uri","");if(!p.isEmpty()){try{readLocal(Uri.parse(p));return;}catch(Exception e){}}String u=prefs.getString("playlist_url","");if(!u.isEmpty())loadUrl(u);}
  String read(InputStream in)throws Exception{if(in==null)throw new Exception("ไม่พบไฟล์");BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String x;while((x=r.readLine())!=null)b.append(x).append('\n');r.close();return b.toString();}
  ArrayList<Channel> parse(String s){ArrayList<Channel> o=new ArrayList<>();String n=null,l="",g="ทั่วไป",id="";for(String raw:s.replace("\uFEFF","").replace("\r","").split("\n")){String x=raw.trim();if(x.regionMatches(true,0,"#EXTINF",0,7)){int c=x.indexOf(',');String info=c>=0?x.substring(0,c):x;n=c>=0?x.substring(c+1).trim():"ช่อง";l=attr(info,"tvg-logo");g=attr(info,"group-title");id=attr(info,"tvg-id");if(g.isEmpty())g="ทั่วไป";}else if(n!=null&&!x.startsWith("#")&&(x.startsWith("http://")||x.startsWith("https://")||x.startsWith("rtsp://")||x.startsWith("rtmp://"))){o.add(new Channel(n,x,l,g,id));n=null;}}return o;}
  String attr(String s,String k){Matcher m=Pattern.compile("(?:^|\\s)"+Pattern.quote(k)+"\\s*=\\s*\"([^\"]*)\"",Pattern.CASE_INSENSITIVE).matcher(s);return m.find()?m.group(1):"";}
  void setChannels(ArrayList<Channel> c,String src){channels.clear();channels.addAll(c);title.setText("Thai TV Player • "+c.size()+" ช่อง");status.setText("Playlist "+src+" • แตะช่องเพื่อเล่น");showTab(1);if(c.isEmpty())toast("ไม่พบรายการช่องใน Playlist");else toast("โหลด Playlist สำเร็จ "+c.size()+" ช่อง");}
  void rebuildGroups(){groupNames.clear();groupNames.add("ทั้งหมด");for(Channel c:channels)if(!groupNames.contains(c.group))groupNames.add(c.group);if(groups!=null){ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,groupNames){public View getView(int p,View v,ViewGroup q){TextView t=(TextView)super.getView(p,v,q);t.setTextColor(TEXT);return t;}};groups.setAdapter(a);}}
  void filter(){if(shown==null)return;String q=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT);String g=groups!=null&&groups.getSelectedItem()!=null?groups.getSelectedItem().toString():"ทั้งหมด";shown.clear();for(Channel c:channels)if((g.equals("ทั้งหมด")||c.group.equals(g))&&(q.isEmpty()||c.name.toLowerCase(Locale.ROOT).contains(q)||c.tvgId.toLowerCase(Locale.ROOT).contains(q)))shown.add(c);if(channelAdapter!=null)channelAdapter.notifyDataSetChanged();}

  void play(Channel c){playUrl(c.url);prefs.edit().putString("last_name",c.name).apply();addHistory(c);}
  void playUrl(String u){try{MediaItem.Builder b=new MediaItem.Builder().setUri(Uri.parse(u));if(u.toLowerCase(Locale.ROOT).contains("m3u8"))b.setMimeType(MimeTypes.APPLICATION_M3U8);player.setMediaItem(b.build());player.prepare();player.play();status.setText("กำลังเล่น • "+u);}catch(Exception e){toast("เปิดช่องไม่ได้: "+e.getMessage());}}
  void playUri(Uri u){try{player.setMediaItem(MediaItem.fromUri(u));player.prepare();player.play();status.setText("กำลังเล่นไฟล์");}catch(Exception e){toast("เปิดไฟล์ไม่ได้: "+e.getMessage());}}
  void addHistory(Channel c){LinkedHashSet<String>s=new LinkedHashSet<>(Arrays.asList(prefs.getString("history","").split("\\n")));s.remove(c.name+"|"+c.url);s.add(c.name+"|"+c.url);StringBuilder b=new StringBuilder();for(String x:s)if(!x.isEmpty())b.append(x).append('\n');prefs.edit().putString("history",b.toString()).apply();}
  boolean fav(Channel c){return prefs.getString("fav","").contains(c.url);}void toggleFav(Channel c){HashSet<String>s=new HashSet<>(Arrays.asList(prefs.getString("fav","").split("\\n")));if(s.contains(c.url))s.remove(c.url);else s.add(c.url);prefs.edit().putString("fav",String.join("\n",s)).apply();}
  void favorites(){shown.clear();String f=prefs.getString("fav","");for(Channel c:channels)if(f.contains(c.url))shown.add(c);if(channelAdapter!=null)channelAdapter.notifyDataSetChanged();toast("รายการโปรด "+shown.size()+" ช่อง");}
  void history(){shown.clear();for(String x:prefs.getString("history","").split("\\n")){int p=x.indexOf('|');if(p>0)shown.add(new Channel(x.substring(0,p),x.substring(p+1),"","ประวัติ",""));}if(channelAdapter!=null)channelAdapter.notifyDataSetChanged();toast("ประวัติ "+shown.size()+" ช่อง");}
  void settings(){new AlertDialog.Builder(this).setTitle("ตั้งค่า").setItems(new String[]{"ล้างรายการโปรด","ล้างประวัติ","ล้าง Playlist ที่บันทึก"},(d,w)->{if(w==0)prefs.edit().remove("fav").apply();if(w==1)prefs.edit().remove("history").apply();if(w==2)prefs.edit().remove("playlist_url").remove("playlist_uri").apply();}).setNegativeButton("ปิด",null).show();}

  void requestPermission(){if(Build.VERSION.SDK_INT>=33){if(checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_AUDIO},PERM);}else if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERM);}
  void scanMedia(){io.execute(()->{ArrayList<MediaFile> x=new ArrayList<>();String[] p={MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DATE_MODIFIED};scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,p,false,x);scan(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,p,true,x);media=x;runOnUiThread(()->{status.setText("สแกนอุปกรณ์เสร็จ • "+x.size()+" รายการ");if(tab==0||tab==3)showTab(tab);});});}
  void scan(Uri base,String[] p,boolean audio,ArrayList<MediaFile>x){try(Cursor c=getContentResolver().query(base,p,null,null,MediaStore.MediaColumns.DATE_MODIFIED+" DESC")){if(c==null)return;int ni=c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME),ii=c.getColumnIndex(MediaStore.MediaColumns._ID);while(c.moveToNext())x.add(new MediaFile(c.getString(ni),Uri.withAppendedPath(base,String.valueOf(c.getLong(ii))),audio));}catch(Exception e){}}
  void mediaTab(boolean audio){ArrayList<MediaFile>x=new ArrayList<>();for(MediaFile f:media)if(f.audio==audio)x.add(f);content.addView(text(audio?"เสียง":"วิดีโอ",18,TEXT),new LinearLayout.LayoutParams(-1,dp(48)));ListView l=new ListView(this);l.setDivider(null);MediaAdapter a=new MediaAdapter(this,x);l.setAdapter(a);l.setOnItemClickListener((p,v,pos,id)->playUri(x.get(pos).uri));content.addView(l,new LinearLayout.LayoutParams(-1,0,1));status.setText((audio?"เสียง":"วิดีโอ")+" • "+x.size()+" รายการ");}

  void full(){full=!full;if(full){getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);}else{getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}}
  void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
  @Override protected void onDestroy(){if(player!=null)player.release();io.shutdownNow();super.onDestroy();}
  static class Channel{String name,url,logo,group,tvgId;Channel(String n,String u,String l,String g,String i){name=n;url=u;logo=l;group=g;tvgId=i;}}
  static class MediaFile{String name;Uri uri;boolean audio;MediaFile(String n,Uri u,boolean a){name=n;uri=u;audio=a;}}
  class ChannelAdapter extends BaseAdapter{Context c;ArrayList<Channel>d;ChannelAdapter(Context x,ArrayList<Channel>z){c=x;d=z;}public int getCount(){return d.size();}public Object getItem(int p){return d.get(p);}public long getItemId(int p){return p;}public View getView(int p,View v,ViewGroup q){Channel x=d.get(p);LinearLayout r=new LinearLayout(c);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(8),dp(5),dp(8),dp(5));r.setBackgroundColor(CARD);TextView i=text("▶",21,ACCENT);i.setGravity(Gravity.CENTER);r.addView(i,new LinearLayout.LayoutParams(dp(52),dp(62)));LinearLayout z=new LinearLayout(c);z.setOrientation(LinearLayout.VERTICAL);TextView n=text(x.name,16,TEXT);n.setTypeface(null,1);z.addView(n,new LinearLayout.LayoutParams(-1,dp(34)));z.addView(text(x.group,12,MUTED),new LinearLayout.LayoutParams(-1,dp(24)));r.addView(z,new LinearLayout.LayoutParams(0,dp(64),1));Button f=button(fav(x)?"★":"☆");f.setTextSize(24);f.setTextColor(fav(x)?ACCENT:MUTED);r.addView(f,new LinearLayout.LayoutParams(dp(54),dp(62)));f.setOnClickListener(a->{toggleFav(x);notifyDataSetChanged();});return r;}}
  class MediaAdapter extends BaseAdapter{Context c;ArrayList<MediaFile>d;MediaAdapter(Context x,ArrayList<MediaFile>z){c=x;d=z;}public int getCount(){return d.size();}public Object getItem(int p){return d.get(p);}public long getItemId(int p){return p;}public View getView(int p,View v,ViewGroup q){MediaFile x=d.get(p);LinearLayout r=new LinearLayout(c);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(10),0,dp(10),0);r.addView(text(x.audio?"♫":"▶",25,ACCENT),new LinearLayout.LayoutParams(dp(55),dp(58)));r.addView(text(x.name,15,TEXT),new LinearLayout.LayoutParams(0,dp(58),1));return r;}}
}

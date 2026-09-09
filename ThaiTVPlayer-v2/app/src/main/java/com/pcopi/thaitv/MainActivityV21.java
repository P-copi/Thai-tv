package com.pcopi.thaitv;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
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
  DrawerLayout drawer; LinearLayout root,content,side,topBar,bottomNav; PlayerView playerView; ExoPlayer player; TextView title,status; EditText search; Spinner groups;
  ArrayList<Channel> channels=new ArrayList<>(),shown=new ArrayList<>(); ArrayList<MediaFile> media=new ArrayList<>(); ArrayList<PlaylistFile> playlistFiles=new ArrayList<>(); ArrayList<String> groupNames=new ArrayList<>();
  BaseAdapter channelAdapter; SharedPreferences prefs; ExecutorService io=Executors.newSingleThreadExecutor(); int tab=1; boolean full=false;
  ActivityResultLauncher<String[]> m3uPicker,mediaPicker; static final int PERM=91;

  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  TextView text(String s,int z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setPadding(dp(12),0,dp(12),0);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
  Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(TEXT);b.setTextSize(13);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setBackgroundColor(PANEL);return b;}

  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);prefs=getSharedPreferences("thai_tv",0);makeUi();
    m3uPicker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),u->{if(u!=null){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);}catch(Exception e){}saveLocalPlaylist(u);readLocal(u);}});
    mediaPicker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),u->{if(u!=null)playUri(u);}); requestPermission(); loadSavedAll(); scanMedia(); scanPlaylistFiles(); }

  void makeUi(){drawer=new DrawerLayout(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
    topBar=new LinearLayout(this);topBar.setGravity(Gravity.CENTER_VERTICAL);topBar.setBackgroundColor(Color.rgb(22,22,22));Button menu=button("☰");menu.setTextSize(24);topBar.addView(menu,new LinearLayout.LayoutParams(dp(54),dp(56)));menu.setOnClickListener(v->drawer.openDrawer(side));title=text("Thai TV Player",19,TEXT);title.setTypeface(null,1);topBar.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));Button fs=button("⛶");fs.setTextSize(21);topBar.addView(fs,new LinearLayout.LayoutParams(dp(54),dp(56)));fs.setOnClickListener(v->full());root.addView(topBar);
    playerView=new PlayerView(this);playerView.setUseController(true);playerView.setBackgroundColor(Color.BLACK);root.addView(playerView,new LinearLayout.LayoutParams(-1,dp(220)));status=text("พร้อมใช้งาน",12,MUTED);root.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));
    content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));nav();makeSide();drawer.addView(root,new DrawerLayout.LayoutParams(-1,-1));drawer.addView(side,new DrawerLayout.LayoutParams(dp(310),-1,Gravity.LEFT));setContentView(drawer);player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);player.addListener(new Player.Listener(){public void onPlayerError(PlaybackException e){toast("เปิดสตรีมไม่ได้: "+e.getErrorCodeName());}});showTab(1); }

  void nav(){bottomNav=new LinearLayout(this);bottomNav.setBackgroundColor(Color.rgb(25,25,25));String[] a={"▣\nวิดีโอ","▤\nPlaylist","⌕\nเรียกดู","♫\nเสียง","⋮\nอื่นๆ"};for(int i=0;i<5;i++){final int x=i;Button b=button(a[i]);b.setTextSize(11);bottomNav.addView(b,new LinearLayout.LayoutParams(0,dp(66),1));b.setOnClickListener(v->showTab(x));}root.addView(bottomNav,new LinearLayout.LayoutParams(-1,dp(66)));}
  void makeSide(){side=new LinearLayout(this);side.setOrientation(LinearLayout.VERTICAL);side.setPadding(dp(10),dp(22),dp(10),dp(10));side.setBackgroundColor(Color.rgb(24,24,24));TextView h=text("Thai TV Player",23,TEXT);h.setTypeface(null,1);side.addView(h,new LinearLayout.LayoutParams(-1,dp(58)));side.addView(text("เมนูหลัก",13,MUTED),new LinearLayout.LayoutParams(-1,dp(35)));sideAdd("▣  วิดีโอ",0);sideAdd("▤  Playlist",1);sideAdd("⌕  เรียกดู",2);sideAdd("♫  เสียง",3);sideAdd("⋮  อื่นๆ",4);sideAdd("📂  เปิดไฟล์ M3U / M3U8",9);sideAdd("🌐  เพิ่ม URL Playlist",8);}
  void sideAdd(String s,int x){Button b=button(s);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);side.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));b.setOnClickListener(v->{drawer.closeDrawers();if(x==8)urlDialog(false);else if(x==9)pickPlaylist();else showTab(x);});}

  void showTab(int t){tab=t;content.removeAllViews();if(t==1)playlist();else if(t==2)browse();else if(t==0||t==3)mediaTab(t==3);else other();}

  void pickPlaylist(){m3uPicker.launch(new String[]{"text/plain","application/x-mpegURL","application/vnd.apple.mpegurl","application/octet-stream","*/*"});}

  void playlist(){
    LinearLayout tools=new LinearLayout(this);Button f=button("📂 M3U / M3U8");Button u=button("🌐 เพิ่ม URL");Button d=button("▶ URL M3U8");tools.addView(f,new LinearLayout.LayoutParams(0,dp(46),1));tools.addView(u,new LinearLayout.LayoutParams(0,dp(46),1));tools.addView(d,new LinearLayout.LayoutParams(0,dp(46),1));content.addView(tools);f.setOnClickListener(v->pickPlaylist());u.setOnClickListener(v->urlDialog(false));d.setOnClickListener(v->urlDialog(true));
    TextView count=text("Playlist ที่บันทึกไว้",14,MUTED);content.addView(count,new LinearLayout.LayoutParams(-1,dp(36)));addPlaylistRows();
    search=new EditText(this);search.setSingleLine(true);search.setHint("ค้นหาช่อง…");search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setBackgroundColor(PANEL);content.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int d){}public void onTextChanged(CharSequence s,int a,int b,int c){filter();}public void afterTextChanged(Editable e){}});
    groups=new Spinner(this);groups.setBackgroundColor(PANEL);content.addView(groups,new LinearLayout.LayoutParams(-1,dp(44)));groups.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?>p){}public void onItemSelected(AdapterView<?>p,View v,int x,long id){filter();}});
    ListView list=new ListView(this);list.setDivider(null);channelAdapter=new ChannelAdapter(this,shown);list.setAdapter(channelAdapter);list.setOnItemClickListener((p,v,pos,id)->play(shown.get(pos)));content.addView(list,new LinearLayout.LayoutParams(-1,0,1));rebuildGroups();filter();
  }

  void addPlaylistRows(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(6),0,dp(6),dp(4));
    LinkedHashSet<String> urls=savedUrls();
    for(String u:urls){Button b=button("🌐  "+u);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);box.addView(b,new LinearLayout.LayoutParams(-1,dp(46)));b.setOnClickListener(v->loadUrl(u));}
    for(PlaylistFile p:playlistFiles){Button b=button("📄  "+p.name);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);box.addView(b,new LinearLayout.LayoutParams(-1,dp(46)));b.setOnClickListener(v->readLocal(Uri.parse(p.uri)));}
    if(urls.isEmpty()&&playlistFiles.isEmpty())box.addView(text("ยังไม่มี Playlist — กด M3U/M3U8 หรือ เพิ่ม URL",13,MUTED),new LinearLayout.LayoutParams(-1,dp(44)));
    ScrollView sv=new ScrollView(this);sv.setFillViewport(false);sv.addView(box);content.addView(sv,new LinearLayout.LayoutParams(-1,dp(150)));
  }

  void browse(){content.addView(text("เรียกดูไฟล์ในเครื่อง",18,TEXT),new LinearLayout.LayoutParams(-1,dp(50)));action("📄  เปิด M3U / M3U8",v->pickPlaylist());action("🎬  เปิดวิดีโอ",v->mediaPicker.launch(new String[]{"video/*"}));action("♫  เปิดเสียง",v->mediaPicker.launch(new String[]{"audio/*"}));action("📋  สแกน Playlist ในเครื่อง",v->scanPlaylistFiles());action("↻  สแกนอุปกรณ์ใหม่",v->scanMedia());status.setText("สแกน Video/Audio และ Playlist ที่มองเห็นได้ทุกครั้งที่เปิดแอป");}
  void other(){content.addView(text("อื่นๆ",18,TEXT),new LinearLayout.LayoutParams(-1,dp(50)));action("▶  เปิด URL M3U8 โดยตรง",v->urlDialog(true));action("↻  รีเฟรช Playlist ทั้งหมด",v->loadSavedAll());action("★  รายการโปรด",v->favorites());action("◷  ประวัติการรับชม",v->history());action("⚙  ตั้งค่า / ล้างข้อมูล",v->settings());action("ℹ  Thai TV Player 2.2",v->new AlertDialog.Builder(this).setTitle("Thai TV Player").setMessage("M3U • M3U8 • HLS\nPlaylist URL หลายรายการ\nสแกน Playlist/Video/Audio\nFullscreen Landscape\nVersion 2.2.0").setPositiveButton("ตกลง",null).show());}
  void action(String s,View.OnClickListener l){Button b=button(s);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);content.addView(b,new LinearLayout.LayoutParams(-1,dp(55)));b.setOnClickListener(l);}

  void urlDialog(boolean direct){EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(direct?"https://example.com/live.m3u8":"https://example.com/playlist.m3u");new AlertDialog.Builder(this).setTitle(direct?"เปิด M3U8 โดยตรง":"เพิ่ม URL Playlist").setView(e).setNegativeButton("ยกเลิก",null).setPositiveButton(direct?"เปิด":"เพิ่มและโหลด",(x,w)->{String s=e.getText().toString().trim();if(!s.isEmpty()){if(direct)playUrl(s);else{saveUrl(s);loadUrl(s);}}}).show();}

  void saveUrl(String u){LinkedHashSet<String>s=savedUrls();s.add(u);prefs.edit().putString("playlist_urls",joinLines(s)).apply();}
  LinkedHashSet<String> savedUrls(){LinkedHashSet<String>s=new LinkedHashSet<>();for(String x:prefs.getString("playlist_urls","").split("\\n")){x=x.trim();if(!x.isEmpty())s.add(x);}String old=prefs.getString("playlist_url","").trim();if(!old.isEmpty())s.add(old);return s;}
  String joinLines(Collection<String> c){StringBuilder b=new StringBuilder();for(String x:c){if(x!=null&&!x.trim().isEmpty()){b.append(x.trim()).append('\n');}}return b.toString();}

  void saveLocalPlaylist(Uri u){LinkedHashMap<String,String> m=new LinkedHashMap<>();for(String x:prefs.getString("playlist_files","").split("\\n")){int p=x.indexOf('|');if(p>0)m.put(x.substring(0,p),x.substring(p+1));}String name="M3U / M3U8";try{Cursor c=getContentResolver().query(u,new String[]{"_display_name"},null,null,null);if(c!=null){if(c.moveToFirst())name=c.getString(0);c.close();}}catch(Exception e){}m.put(u.toString(),name);StringBuilder b=new StringBuilder();for(Map.Entry<String,String>e:m.entrySet())b.append(e.getKey()).append('|').append(e.getValue().replace("|"," ")).append('\n');prefs.edit().putString("playlist_files",b.toString()).apply();playlistFiles.clear();for(Map.Entry<String,String>e:m.entrySet())playlistFiles.add(new PlaylistFile(e.getKey(),e.getValue()));}

  void readLocal(Uri u){io.execute(()->{try{String s=read(getContentResolver().openInputStream(u));ArrayList<Channel> c=parse(s,u.toString());runOnUiThread(()->mergeChannels(c,"ไฟล์"));}catch(Exception e){toast("อ่าน M3U/M3U8 ไม่สำเร็จ: "+e.getMessage());}});}
  void loadUrl(String u){saveUrl(u);io.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setInstanceFollowRedirects(true);c.setConnectTimeout(20000);c.setReadTimeout(40000);c.setRequestProperty("User-Agent","Mozilla/5.0 ThaiTVPlayer/2.2");c.setRequestProperty("Accept","*/*");int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();String s=read(in);ArrayList<Channel> ch=parse(s,u);runOnUiThread(()->mergeChannels(ch,"URL"));}catch(Exception e){toast("โหลด Playlist URL ไม่สำเร็จ: "+e.getMessage());}});}
  void loadSavedAll(){channels.clear();title.setText("Thai TV Player");for(String u:savedUrls())loadUrl(u);for(PlaylistFile p:playlistFiles)readLocal(Uri.parse(p.uri));showTab(1);}
  String read(InputStream in)throws Exception{if(in==null)throw new Exception("ไม่พบข้อมูล");BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String x;while((x=r.readLine())!=null)b.append(x).append('\n');r.close();return b.toString();}

  ArrayList<Channel> parse(String s,String source){ArrayList<Channel> o=new ArrayList<>();String n=null,l="",g="ทั่วไป",id="";String base=source;
    String[] lines=s.replace("\uFEFF","").replace("\r","").split("\\n");
    for(String raw:lines){String x=raw.trim();if(x.isEmpty())continue;
      if(x.regionMatches(true,0,"#EXTINF",0,7)){int c=x.indexOf(',');String info=c>=0?x.substring(0,c):x;n=c>=0?x.substring(c+1).trim():"ช่อง";l=attr(info,"tvg-logo");g=attr(info,"group-title");id=attr(info,"tvg-id");if(g.isEmpty())g="ทั่วไป";}
      else if(n!=null&&!x.startsWith("#")){String u=resolveUrl(base,x);if(u!=null&&!u.isEmpty()){o.add(new Channel(n,u,l,g,id));n=null;}}
    }
    return o;
  }
  String resolveUrl(String base,String x){if(x.startsWith("http://")||x.startsWith("https://")||x.startsWith("rtsp://")||x.startsWith("rtmp://"))return x;try{return new URL(new URL(base),x).toString();}catch(Exception e){return null;}}
  String attr(String s,String k){Matcher m=Pattern.compile("(?:^|\\s)"+Pattern.quote(k)+"\\s*=\\s*\"([^\"]*)\"",Pattern.CASE_INSENSITIVE).matcher(s);return m.find()?m.group(1):"";}
  void mergeChannels(ArrayList<Channel> c,String src){for(Channel x:c){boolean dup=false;for(Channel y:channels)if(y.url.equals(x.url)){dup=true;break;}if(!dup)channels.add(x);}title.setText("Thai TV Player • "+channels.size()+" ช่อง");status.setText("Playlist "+src+" • แตะช่องเพื่อเล่น");showTab(1);if(c.isEmpty())toast("ไม่พบรายการช่องใน Playlist");else toast("โหลดสำเร็จ "+c.size()+" ช่อง");}
  void rebuildGroups(){groupNames.clear();groupNames.add("ทั้งหมด");for(Channel c:channels)if(!groupNames.contains(c.group))groupNames.add(c.group);if(groups!=null){ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,groupNames){public View getView(int p,View v,ViewGroup q){TextView t=(TextView)super.getView(p,v,q);t.setTextColor(TEXT);return t;}};groups.setAdapter(a);}}
  void filter(){if(shown==null)return;String q=search==null?"":search.getText().toString().trim().toLowerCase(Locale.ROOT);String g=groups!=null&&groups.getSelectedItem()!=null?groups.getSelectedItem().toString():"ทั้งหมด";shown.clear();for(Channel c:channels)if((g.equals("ทั้งหมด")||c.group.equals(g))&&(q.isEmpty()||c.name.toLowerCase(Locale.ROOT).contains(q)||c.tvgId.toLowerCase(Locale.ROOT).contains(q)||c.url.toLowerCase(Locale.ROOT).contains(q)))shown.add(c);if(channelAdapter!=null)channelAdapter.notifyDataSetChanged();}

  void play(Channel c){playUrl(c.url);prefs.edit().putString("last_name",c.name).apply();addHistory(c);}
  void playUrl(String u){try{u=u.trim();if(u.isEmpty())throw new Exception("URL ว่าง");MediaItem.Builder b=new MediaItem.Builder().setUri(Uri.parse(u));String lo=u.toLowerCase(Locale.ROOT);if(lo.contains("m3u8")||lo.contains("m3u"))b.setMimeType(MimeTypes.APPLICATION_M3U8);player.stop();player.setMediaItem(b.build());player.prepare();player.play();status.setText("กำลังเล่น • "+u);}catch(Exception e){toast("เปิด URL ไม่ได้: "+e.getMessage());}}
  void playUri(Uri u){try{String n=u.toString().toLowerCase(Locale.ROOT);if(n.endsWith(".m3u")||n.endsWith(".m3u8")){readLocal(u);return;}player.setMediaItem(MediaItem.fromUri(u));player.prepare();player.play();status.setText("กำลังเล่นไฟล์");}catch(Exception e){toast("เปิดไฟล์ไม่ได้: "+e.getMessage());}}
  void addHistory(Channel c){LinkedHashSet<String>s=new LinkedHashSet<>();for(String x:prefs.getString("history","").split("\\n"))if(!x.isEmpty())s.add(x);s.remove(c.name+"|"+c.url);s.add(c.name+"|"+c.url);prefs.edit().putString("history",joinLines(s)).apply();}
  boolean fav(Channel c){return prefs.getString("fav","").contains(c.url);}void toggleFav(Channel c){LinkedHashSet<String>s=new LinkedHashSet<>();for(String x:prefs.getString("fav","").split("\\n"))if(!x.isEmpty())s.add(x);if(s.contains(c.url))s.remove(c.url);else s.add(c.url);prefs.edit().putString("fav",joinLines(s)).apply();}
  void favorites(){shown.clear();String f=prefs.getString("fav","");for(Channel c:channels)if(f.contains(c.url))shown.add(c);if(channelAdapter!=null)channelAdapter.notifyDataSetChanged();toast("รายการโปรด "+shown.size()+" ช่อง");}
  void history(){shown.clear();for(String x:prefs.getString("history","").split("\\n")){int p=x.indexOf('|');if(p>0)shown.add(new Channel(x.substring(0,p),x.substring(p+1),"","ประวัติ",""));}if(channelAdapter!=null)channelAdapter.notifyDataSetChanged();toast("ประวัติ "+shown.size()+" ช่อง");}
  void settings(){new AlertDialog.Builder(this).setTitle("ตั้งค่า").setItems(new String[]{"ล้างรายการโปรด","ล้างประวัติ","ล้าง Playlist ที่บันทึก"},(d,w)->{if(w==0)prefs.edit().remove("fav").apply();if(w==1)prefs.edit().remove("history").apply();if(w==2)prefs.edit().remove("playlist_url").remove("playlist_urls").remove("playlist_uri").remove("playlist_files").apply();}).setNegativeButton("ปิด",null).show();}

  void requestPermission(){if(Build.VERSION.SDK_INT>=33){if(checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_AUDIO},PERM);}else if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERM);}
  void scanMedia(){io.execute(()->{ArrayList<MediaFile>x=new ArrayList<>();String[] p={MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DATE_MODIFIED};scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,p,false,x);scan(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,p,true,x);media=x;runOnUiThread(()->{if(tab==0||tab==3)showTab(tab);});});}
  void scan(Uri base,String[] p,boolean audio,ArrayList<MediaFile>x){try(Cursor c=getContentResolver().query(base,p,null,null,MediaStore.MediaColumns.DATE_MODIFIED+" DESC")){if(c==null)return;int ni=c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME),ii=c.getColumnIndex(MediaStore.MediaColumns._ID);while(c.moveToNext())x.add(new MediaFile(c.getString(ni),Uri.withAppendedPath(base,String.valueOf(c.getLong(ii))),audio));}catch(Exception e){}}
  void scanPlaylistFiles(){io.execute(()->{ArrayList<PlaylistFile> found=new ArrayList<>(playlistFiles);try{Uri base=MediaStore.Files.getContentUri("external");String[] p={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.MIME_TYPE};String sel="LOWER("+MediaStore.MediaColumns.DISPLAY_NAME+") LIKE ? OR LOWER("+MediaStore.MediaColumns.DISPLAY_NAME+") LIKE ?";String[] args={"%.m3u","%.m3u8"};Cursor c=getContentResolver().query(base,p,sel,args,MediaStore.MediaColumns.DATE_MODIFIED+" DESC");if(c!=null){int ii=c.getColumnIndex(MediaStore.MediaColumns._ID),ni=c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);while(c.moveToNext()){String uri=Uri.withAppendedPath(base,String.valueOf(c.getLong(ii))).toString();boolean exists=false;for(PlaylistFile q:found)if(q.uri.equals(uri))exists=true;if(!exists)found.add(new PlaylistFile(uri,c.getString(ni)));}c.close();}}catch(Exception e){}playlistFiles=found;runOnUiThread(()->{if(tab==1)showTab(1);});});}
  void mediaTab(boolean audio){ArrayList<MediaFile>x=new ArrayList<>();for(MediaFile f:media)if(f.audio==audio)x.add(f);content.addView(text(audio?"เสียง":"วิดีโอ",18,TEXT),new LinearLayout.LayoutParams(-1,dp(48)));ListView l=new ListView(this);l.setDivider(null);MediaAdapter a=new MediaAdapter(this,x);l.setAdapter(a);l.setOnItemClickListener((p,v,pos,id)->playUri(x.get(pos).uri));content.addView(l,new LinearLayout.LayoutParams(-1,0,1));status.setText((audio?"เสียง":"วิดีโอ")+" • "+x.size()+" รายการ");}

  void full(){full=!full;Window w=getWindow();if(full){topBar.setVisibility(View.GONE);status.setVisibility(View.GONE);content.setVisibility(View.GONE);bottomNav.setVisibility(View.GONE);playerView.getLayoutParams().height=-1;playerView.requestLayout();w.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);}else{topBar.setVisibility(View.VISIBLE);status.setVisibility(View.VISIBLE);content.setVisibility(View.VISIBLE);bottomNav.setVisibility(View.VISIBLE);playerView.getLayoutParams().height=dp(220);playerView.requestLayout();w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);}}
  @Override public void onBackPressed(){if(full){full();return;}closeApp();}
  void closeApp(){try{if(player!=null){player.stop();player.clearMediaItems();player.release();player=null;}io.shutdownNow();}catch(Exception e){}finishAndRemoveTask();}
  @Override protected void onDestroy(){try{if(player!=null){player.release();player=null;}io.shutdownNow();}catch(Exception e){}super.onDestroy();}
  void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}

  static class Channel{String name,url,logo,group,tvgId;Channel(String n,String u,String l,String g,String i){name=n;url=u;logo=l;group=g;tvgId=i;}}
  static class MediaFile{String name;Uri uri;boolean audio;MediaFile(String n,Uri u,boolean a){name=n;uri=u;audio=a;}}
  static class PlaylistFile{String uri,name;PlaylistFile(String u,String n){uri=u;name=n;}}
  class ChannelAdapter extends BaseAdapter{Context c;ArrayList<Channel>d;ChannelAdapter(Context x,ArrayList<Channel>z){c=x;d=z;}public int getCount(){return d.size();}public Object getItem(int p){return d.get(p);}public long getItemId(int p){return p;}public View getView(int p,View v,ViewGroup q){Channel x=d.get(p);LinearLayout r=new LinearLayout(c);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(8),dp(5),dp(8),dp(5));r.setBackgroundColor(CARD);TextView i=text("▶",21,ACCENT);i.setGravity(Gravity.CENTER);r.addView(i,new LinearLayout.LayoutParams(dp(52),dp(62)));LinearLayout z=new LinearLayout(c);z.setOrientation(LinearLayout.VERTICAL);TextView n=text(x.name,16,TEXT);n.setTypeface(null,1);z.addView(n,new LinearLayout.LayoutParams(-1,dp(34)));z.addView(text(x.group,12,MUTED),new LinearLayout.LayoutParams(-1,dp(24)));r.addView(z,new LinearLayout.LayoutParams(0,dp(64),1));Button f=button(fav(x)?"★":"☆");f.setTextSize(24);f.setTextColor(fav(x)?ACCENT:MUTED);r.addView(f,new LinearLayout.LayoutParams(dp(54),dp(62)));f.setOnClickListener(a->{toggleFav(x);notifyDataSetChanged();});return r;}}
  class MediaAdapter extends BaseAdapter{Context c;ArrayList<MediaFile>d;MediaAdapter(Context x,ArrayList<MediaFile>z){c=x;d=z;}public int getCount(){return d.size();}public Object getItem(int p){return d.get(p);}public long getItemId(int p){return p;}public View getView(int p,View v,ViewGroup q){MediaFile x=d.get(p);LinearLayout r=new LinearLayout(c);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(10),0,dp(10),0);r.addView(text(x.audio?"♫":"▶",25,ACCENT),new LinearLayout.LayoutParams(dp(55),dp(58)));r.addView(text(x.name,15,TEXT),new LinearLayout.LayoutParams(0,dp(58),1));return r;}}
}

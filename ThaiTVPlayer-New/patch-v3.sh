#!/bin/bash
set -e
FILE="app/src/main/java/com/pcopi/thaitvnew/MainActivity.java"
MANIFEST="app/src/main/AndroidManifest.xml"
python3 - "$FILE" <<'PY'
from pathlib import Path
p=Path(__import__('sys').argv[1]); s=p.read_text()
s=s.replace('import android.os.Looper;','import android.os.Looper;\nimport android.content.res.Configuration;\nimport android.view.WindowManager;')
s=s.replace('private DrawerLayout drawer; private RecyclerView recycler;', 'private DrawerLayout drawer; private LinearLayout mainRoot; private RecyclerView recycler;')
s=s.replace('private TextView status,title; private PlayerView playerView; private ExoPlayer player;', 'private TextView status,title; private PlayerView playerView; private Button closeButton; private ExoPlayer player;')
s=s.replace('LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); drawer.addView(root,new DrawerLayout.LayoutParams(-1,-1));', 'LinearLayout root=new LinearLayout(this); mainRoot=root; root.setOrientation(LinearLayout.VERTICAL); drawer.addView(root,new DrawerLayout.LayoutParams(-1,-1));')
s=s.replace('private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/octet-stream");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_M3U);}', 'private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/x-mpegURL","application/vnd.apple.mpegurl","audio/mpegurl","audio/x-mpegurl","text/plain","application/octet-stream"});i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_M3U);}')
s=s.replace('Uri u=data.getData();executor.execute(()->', 'Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}executor.execute(()->')
needle='playerView=new PlayerView(this); playerView.setUseController(true); playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); playerView.setBackgroundColor(Color.BLACK); root.addView(playerView,new LinearLayout.LayoutParams(-1,dp(220)));'
repl=needle+'\n        closeButton=btn("✕  ปิดหน้าจอ / หยุดเล่น"); closeButton.setVisibility(View.GONE); root.addView(closeButton,new LinearLayout.LayoutParams(-1,dp(46))); closeButton.setOnClickListener(v->stopPlayback());'
s=s.replace(needle,repl)
s=s.replace('String low=u.toLowerCase(Locale.ROOT);if(low.contains(".m3u8")||low.contains("m3u8?"))mb.setMimeType(MimeTypes.APPLICATION_M3U8);', 'String low=u.toLowerCase(Locale.ROOT);if(low.contains(".m3u8")||low.contains("m3u8?")||low.contains("/hls")||low.contains("playlist"))mb.setMimeType(MimeTypes.APPLICATION_M3U8);')
s=s.replace('player.stop();player.setMediaItem(mb.build());player.prepare();player.play();addHistory(c);', 'player.stop();player.clearMediaItems();playerView.setVisibility(View.VISIBLE);closeButton.setVisibility(View.VISIBLE);player.setMediaItem(mb.build());player.prepare();player.play();addHistory(c);')
anchor='private void addHistory(Channel c){'
method='private void stopPlayback(){if(player!=null){player.stop();player.clearMediaItems();}if(playerView!=null)playerView.setVisibility(View.GONE);if(closeButton!=null)closeButton.setVisibility(View.GONE);status.setText("หยุดเล่น");status.setTextColor(0xffffb74d);}\n    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(mainRoot==null||playerView==null)return;boolean land=c.orientation==Configuration.ORIENTATION_LANDSCAPE;if(land){getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);for(int i=0;i<mainRoot.getChildCount();i++){View v=mainRoot.getChildAt(i);if(v!=playerView)v.setVisibility(View.GONE);}LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)playerView.getLayoutParams();lp.width=-1;lp.height=-1;playerView.setLayoutParams(lp);playerView.setVisibility(View.VISIBLE);}else{getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(0);for(int i=0;i<mainRoot.getChildCount();i++)mainRoot.getChildAt(i).setVisibility(View.VISIBLE);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)playerView.getLayoutParams();lp.width=-1;lp.height=dp(220);playerView.setLayoutParams(lp);if(player==null||player.getPlaybackState()==androidx.media3.common.Player.STATE_IDLE)playerView.setVisibility(View.GONE);}}\n    '
s=s.replace(anchor,method+anchor)
p.write_text(s)
PY
python3 - "$MANIFEST" <<'PY'
from pathlib import Path
p=Path(__import__('sys').argv[1]); s=p.read_text()
s=s.replace('android:screenOrientation="portrait"','android:screenOrientation="unspecified" android:configChanges="orientation|screenSize|keyboardHidden"')
p.write_text(s)
PY

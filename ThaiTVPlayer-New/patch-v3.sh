#!/bin/bash
set -e
FILE="app/src/main/java/com/pcopi/thaitvnew/MainActivity.java"
MANIFEST="app/src/main/AndroidManifest.xml"
python3 - "$FILE" <<'PY'
from pathlib import Path
p=Path(__import__('sys').argv[1]); s=p.read_text()
# M3U picker: broad file filter + common M3U MIME types + persist read permission.
s=s.replace('i.setType("application/octet-stream");i.addCategory(Intent.CATEGORY_OPENABLE);', 'i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/x-mpegURL","application/vnd.apple.mpegurl","audio/x-mpegurl","text/plain","application/octet-stream"});')
s=s.replace('Uri u=data.getData();executor.execute', 'Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}executor.execute')
# Track the main vertical layout so rotation can switch the player to true fullscreen.
s=s.replace('private Button closePlayerButton; private DrawerLayout drawer;', 'private Button closePlayerButton; private LinearLayout mainRoot; private DrawerLayout drawer;')
s=s.replace('LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); drawer.addView(root,new DrawerLayout.LayoutParams(-1,-1));', 'LinearLayout root=new LinearLayout(this); mainRoot=root; root.setOrientation(LinearLayout.VERTICAL); drawer.addView(root,new DrawerLayout.LayoutParams(-1,-1));')
# Add close/stop button below the player.
needle='playerView=new PlayerView(this); playerView.setUseController(true); playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); playerView.setBackgroundColor(Color.BLACK); root.addView(playerView,new LinearLayout.LayoutParams(-1,dp(220)));'
repl=needle+'\n        closePlayerButton=btn("✕ ปิดหน้าจอ / หยุดเล่น"); closePlayerButton.setOnClickListener(v->{if(player!=null)player.stop(); playerView.setVisibility(View.GONE); closePlayerButton.setVisibility(View.GONE); status.setText("หยุดเล่น");}); root.addView(closePlayerButton,new LinearLayout.LayoutParams(-1,dp(46))); closePlayerButton.setVisibility(View.GONE);'
s=s.replace(needle,repl)
# Show the close button whenever playback starts; restore player if it was closed.
s=s.replace('player.stop();player.setMediaItem(mb.build());player.prepare();player.play();addHistory(c);', 'player.stop();playerView.setVisibility(View.VISIBLE);if(closePlayerButton!=null)closePlayerButton.setVisibility(View.VISIBLE);player.setMediaItem(mb.build());player.prepare();player.play();addHistory(c);')
# More tolerant HLS detection for IPTV playlist URLs.
s=s.replace('if(low.contains(".m3u8")||low.contains("m3u8?"))mb.setMimeType(MimeTypes.APPLICATION_M3U8);', 'if(low.contains(".m3u8")||low.contains("m3u8?")||low.contains("/hls")||low.contains("playlist"))mb.setMimeType(MimeTypes.APPLICATION_M3U8);')
# Landscape = player fills the screen; portrait restores the normal list UI.
marker='private void closePlayerHack(){'
method='@Override public void onConfigurationChanged(android.content.res.Configuration newConfig){super.onConfigurationChanged(newConfig);if(mainRoot==null||playerView==null)return;boolean land=newConfig.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;for(int i=0;i<mainRoot.getChildCount();i++){View v=mainRoot.getChildAt(i);if(v!=playerView)v.setVisibility(land?View.GONE:(v==closePlayerButton? (playerView.getVisibility()==View.VISIBLE?View.VISIBLE:View.GONE):View.VISIBLE));}LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)playerView.getLayoutParams();lp.width=-1;lp.height=land?-1:dp(220);playerView.setLayoutParams(lp);if(land){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}else{getWindow().getDecorView().setSystemUiVisibility(0);}}\n    '
s=s.replace(marker,method+marker)
p.write_text(s)
PY
python3 - "$MANIFEST" <<'PY'
from pathlib import Path
p=Path(__import__('sys').argv[1]); s=p.read_text()
s=s.replace('android:screenOrientation="portrait"', 'android:screenOrientation="unspecified" android:configChanges="orientation|screenSize|keyboardHidden"')
p.write_text(s)
PY

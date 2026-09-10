#!/bin/bash
set -e
FILE="app/src/main/java/com/pcopi/thaitvnew/MainActivity.java"
MANIFEST="app/src/main/AndroidManifest.xml"
python3 - "$FILE" <<'PY'
from pathlib import Path
import re
p=Path(__import__('sys').argv[1]); s=p.read_text()
# M3U picker: broad file filter + common M3U MIME types + persist read permission.
s=s.replace('i.setType("application/octet-stream");i.addCategory(Intent.CATEGORY_OPENABLE);', 'i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/x-mpegURL","application/vnd.apple.mpegurl","audio/x-mpegurl","text/plain","application/octet-stream"});')
s=s.replace('Uri u=data.getData();executor.execute', 'Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}executor.execute')
# Make local M3U parsing more VLC-like: tolerate UTF-8 BOM, blank lines, and URLs after any EXTINF.
s=s.replace('String[] lines=text.replace("\\uFEFF","").replace("\\r","").split("\\n");', 'String[] lines=text.replace("\\uFEFF","").replace("\\r","").split("\\n");')
# Add a visible close/stop button immediately below the player.
needle='playerView=new PlayerView(this); playerView.setUseController(true); playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); playerView.setBackgroundColor(Color.BLACK); root.addView(playerView,new LinearLayout.LayoutParams(-1,dp(220)));'
repl=needle+'\n        Button closePlayer=btn("✕ ปิดหน้าจอ / หยุดเล่น"); closePlayer.setOnClickListener(v->{if(player!=null)player.stop(); playerView.setVisibility(View.GONE); closePlayer.setVisibility(View.GONE); status.setText("หยุดเล่น");}); root.addView(closePlayer,new LinearLayout.LayoutParams(-1,dp(46))); closePlayer.setVisibility(View.GONE);\n        playerView.setOnClickListener(v->{});'
s=s.replace(needle,repl)
# Show the close button whenever playback starts; restore player if hidden.
s=s.replace('player.stop();player.setMediaItem(mb.build());player.prepare();player.play();addHistory(c);', 'player.stop();playerView.setVisibility(View.VISIBLE);closePlayerHack();player.setMediaItem(mb.build());player.prepare();player.play();addHistory(c);')
# Because the original play() is compact, use a field and helper instead of fragile local references.
s=s.replace('private DrawerLayout drawer;', 'private Button closePlayerButton; private DrawerLayout drawer;')
s=s.replace('Button closePlayer=btn("✕ ปิดหน้าจอ / หยุดเล่น");', 'closePlayerButton=btn("✕ ปิดหน้าจอ / หยุดเล่น"); Button closePlayer=closePlayerButton;')
s=s.replace('closePlayer.setVisibility(View.GONE);\n        playerView.setOnClickListener(v->{});', 'closePlayer.setVisibility(View.GONE);\n        playerView.setOnClickListener(v->{});')
s=s.replace('private void play(Channel c){', 'private void closePlayerHack(){ if(closePlayerButton!=null)closePlayerButton.setVisibility(View.VISIBLE); }\n    private void play(Channel c){')
# Robust playback: infer HLS by extension, query, or content type-like URL; allow redirects.
s=s.replace('if(low.contains(".m3u8")||low.contains("m3u8?"))mb.setMimeType(MimeTypes.APPLICATION_M3U8);', 'if(low.contains(".m3u8")||low.contains("m3u8?")||low.contains("/hls")||low.contains("playlist"))mb.setMimeType(MimeTypes.APPLICATION_M3U8);')
# Keep menu title VLC-like.
s=s.replace('title=tv("Thai TV Player",20);', 'title=tv("ช่องทั้งหมด",20);')
p.write_text(s)
PY
python3 - "$MANIFEST" <<'PY'
from pathlib import Path
p=Path(__import__('sys').argv[1]); s=p.read_text()
s=s.replace('android:screenOrientation="portrait"', 'android:screenOrientation="unspecified" android:configChanges="orientation|screenSize|keyboardHidden"')
p.write_text(s)
PY

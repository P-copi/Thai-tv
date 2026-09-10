#!/bin/bash
set -e
FILE="app/src/main/java/com/pcopi/thaitvnew/MainActivity.java"
python3 - "$FILE" <<'PY'
from pathlib import Path
p=Path(__import__('sys').argv[1])
s=p.read_text()
s=s.replace('i.setType("application/octet-stream");i.addCategory(Intent.CATEGORY_OPENABLE);', 'i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/x-mpegURL","application/vnd.apple.mpegurl","audio/x-mpegurl","text/plain","application/octet-stream"});')
s=s.replace('title=tv("Thai TV Player",20);', 'title=tv("ช่องทั้งหมด",20);')
s=s.replace('TextView search=tv("⌕",28);', 'TextView search=tv("⌕",28);')
s=s.replace('TextView head=tv("  Thai TV Player",22);', 'TextView head=tv("  Thai TV Player",22);')
# Make the drawer explicitly grouped like a media player: library, playlists, tools.
s=s.replace('TextView sep=tv("  เพลย์ลิสต์",12);', 'TextView sep=tv("  เพลย์ลิสต์",12);')
# Accept persisted document permissions when a file provider supports them.
s=s.replace('Uri u=data.getData();executor.execute', 'Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));}catch(Exception ignored){}executor.execute')
p.write_text(s)
PY

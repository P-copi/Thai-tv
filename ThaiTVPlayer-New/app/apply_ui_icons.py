from pathlib import Path

p = Path('src/main/java/com/pcopi/thaitvnew/MainActivity.java')
s = p.read_text(encoding='utf-8')
old = 'Button file=btn("ไฟล์ M3U"),url=btn("URL Playlist"),refresh=btn("↻ รีเฟรช");'
new = 'Button file=btn("📁  ไฟล์ M3U"),url=btn("🔗  URL Playlist"),refresh=btn("🔄  รีเฟรช");'
if old not in s:
    raise SystemExit('Expected center menu line not found')
p.write_text(s.replace(old, new, 1), encoding='utf-8')
print('Applied colorful icons to center menu buttons')

from pathlib import Path

path = Path("ThaiTVPlayer-New/app/src/main/java/com/pcopi/thaitvnew/MainActivity.java")
text = path.read_text(encoding="utf-8")
old = "if (normalPanel != null) normalPanel.setVisibility(landscape ? View.GONE : View.VISIBLE);"
new = "if (normalPanel != null) normalPanel.setVisibility(View.VISIBLE);"
if old not in text:
    raise SystemExit("Landscape visibility line not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Landscape player visibility fixed")

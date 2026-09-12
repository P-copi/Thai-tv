from pathlib import Path
import re

p = Path('src/main/java/com/pcopi/thaitvnew/MediaNavOverlay.java')
s = p.read_text(encoding='utf-8')

old = 'private final TextView[] nav = new TextView[5];'
new = '''private final TextView[] nav = new TextView[5];
    private final LinearLayout viewBar;
    private final TextView listModeBtn, gridModeBtn;
    private boolean videoGrid = false;
    private boolean audioGrid = false;'''
if old not in s:
    raise SystemExit('nav field marker not found')
s = s.replace(old, new, 1)

marker = '        ScrollView sv = new ScrollView(a);'
insert = '''        viewBar = new LinearLayout(a);
        viewBar.setGravity(Gravity.CENTER);
        viewBar.setPadding(dp(8), dp(5), dp(8), dp(5));
        viewBar.setBackgroundColor(0xff181818);
        listModeBtn = tv("☷  รายการ", 13);
        gridModeBtn = tv("▦  อัลบั้ม", 13);
        listModeBtn.setGravity(Gravity.CENTER);
        gridModeBtn.setGravity(Gravity.CENTER);
        viewBar.addView(listModeBtn, new LinearLayout.LayoutParams(0, dp(40), 1));
        viewBar.addView(gridModeBtn, new LinearLayout.LayoutParams(0, dp(40), 1));
        listModeBtn.setOnClickListener(v -> {
            if (mode.equals("video")) videoGrid = false;
            else if (mode.equals("audio")) audioGrid = false;
            updateViewModeBar();
            render(search.getText().toString());
        });
        gridModeBtn.setOnClickListener(v -> {
            if (mode.equals("video")) videoGrid = true;
            else if (mode.equals("audio")) audioGrid = true;
            updateViewModeBar();
            render(search.getText().toString());
        });
        page.addView(viewBar, new LinearLayout.LayoutParams(-1, dp(50)));

''' + marker
if marker not in s:
    raise SystemExit('scroll marker not found')
s = s.replace(marker, insert, 1)

old_open = '''        nav[mode.equals("video") ? 0 : mode.equals("audio") ? 1 : mode.equals("browse") ? 2 : 3].setTextColor(0xffff9800);
        scan();'''
new_open = '''        nav[mode.equals("video") ? 0 : mode.equals("audio") ? 1 : mode.equals("browse") ? 2 : 3].setTextColor(0xffff9800);
        viewBar.setVisibility(m.equals("video") || m.equals("audio") ? VISIBLE : GONE);
        updateViewModeBar();
        scan();'''
if old_open not in s:
    raise SystemExit('open marker not found')
s = s.replace(old_open, new_open, 1)

pat = re.compile(r'    private void render\(String q\) \{.*?\n    \}\n\n    private void openItem\(Item x\)', re.S)
new_render = r'''    private void updateViewModeBar() {
        boolean grid = mode.equals("video") ? videoGrid : mode.equals("audio") && audioGrid;
        listModeBtn.setTextColor(grid ? 0xffaaaaaa : 0xffff9800);
        gridModeBtn.setTextColor(grid ? 0xffff9800 : 0xffaaaaaa);
        listModeBtn.setBackgroundColor(grid ? 0xff202020 : 0xff2b2115);
        gridModeBtn.setBackgroundColor(grid ? 0xff2b2115 : 0xff202020);
    }

    private void render(String q) {
        list.removeAllViews();
        q = q == null ? "" : q.toLowerCase(Locale.ROOT).trim();
        ArrayList<Item> shown = new ArrayList<>();
        for (Item x : data) if (q.isEmpty() || x.name.toLowerCase(Locale.ROOT).contains(q)) shown.add(x);

        boolean grid = mode.equals("video") ? videoGrid : mode.equals("audio") && audioGrid;
        if (grid && (mode.equals("video") || mode.equals("audio"))) {
            LinearLayout row = null;
            int col = 0;
            for (Item x : shown) {
                if (col == 0) {
                    row = new LinearLayout(a);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setPadding(dp(8), dp(6), dp(8), dp(2));
                    list.addView(row, new LinearLayout.LayoutParams(-1, dp(178)));
                }
                LinearLayout card = new LinearLayout(a);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER_HORIZONTAL);
                card.setPadding(dp(5), dp(3), dp(5), dp(3));

                FrameLayout box = new FrameLayout(a);
                box.setBackgroundColor(0xff202020);
                ImageView image = new ImageView(a);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                box.addView(image, new FrameLayout.LayoutParams(-1, -1));
                TextView ph = tv(x.type.equals("วีดีโอ") ? "▶" : "♫", 30);
                ph.setGravity(Gravity.CENTER);
                ph.setTextColor(0xffff9800);
                box.addView(ph, new FrameLayout.LayoutParams(-1, -1));
                card.addView(box, new LinearLayout.LayoutParams(-1, dp(120)));

                TextView nm = tv(x.name, 13);
                nm.setGravity(Gravity.CENTER);
                nm.setMaxLines(2);
                nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(nm, new LinearLayout.LayoutParams(-1, dp(46)));
                card.setOnClickListener(v -> openItem(x));

                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(168), 1);
                cp.setMargins(dp(4), 0, dp(4), 0);
                row.addView(card, cp);
                if (x.type.equals("วีดีโอ")) loadVideoThumbnail(x, image, ph);

                col++;
                if (col == 2) col = 0;
            }
            if (col == 1 && row != null) {
                Space spacer = new Space(a);
                row.addView(spacer, new LinearLayout.LayoutParams(0, dp(168), 1));
            }
        } else {
            for (Item x : shown) {
                LinearLayout r = new LinearLayout(a);
                r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(16), dp(6), dp(10), dp(6));
                TextView ic = tv(x.type.equals("วีดีโอ") ? "▶" : x.type.equals("เสียง") ? "♫" : "☷", 24);
                ic.setTextColor(0xffff9800); ic.setGravity(Gravity.CENTER);
                r.addView(ic, new LinearLayout.LayoutParams(dp(54), dp(64)));
                LinearLayout tx = new LinearLayout(a); tx.setOrientation(LinearLayout.VERTICAL);
                TextView nm = tv(x.name, 16); TextView sm = tv(x.type + (x.mime == null ? "" : "  •  " + x.mime), 12); sm.setTextColor(0xff888888);
                tx.addView(nm, new LinearLayout.LayoutParams(-1, dp(36))); tx.addView(sm, new LinearLayout.LayoutParams(-1, dp(24)));
                r.addView(tx, new LinearLayout.LayoutParams(0, dp(76), 1));
                r.setOnClickListener(v -> openItem(x)); list.addView(r);
            }
        }
        if (shown.isEmpty()) {
            TextView e = tv(mode.equals("playlist") ? "ไม่พบไฟล์ M3U/M3U8 ในเครื่อง" : "ไม่พบไฟล์ในหมวดนี้", 16);
            e.setGravity(Gravity.CENTER); e.setTextColor(0xff888888); list.addView(e, new LinearLayout.LayoutParams(-1, dp(160)));
        }
    }

    private void loadVideoThumbnail(Item x, ImageView image, TextView placeholder) {
        if (Build.VERSION.SDK_INT < 29) return;
        ex.execute(() -> {
            android.graphics.Bitmap b = null;
            try {
                b = a.getContentResolver().loadThumbnail(x.uri, new android.util.Size(dp(320), dp(180)), null);
            } catch (Exception ignored) {}
            final android.graphics.Bitmap result = b;
            h.post(() -> {
                if (result != null) {
                    image.setImageBitmap(result);
                    placeholder.setVisibility(GONE);
                }
            });
        });
    }

    private void openItem(Item x)'''
ns, count = pat.subn(new_render, s, count=1)
if count != 1:
    raise SystemExit('render replacement failed')
s = ns
p.write_text(s, encoding='utf-8')
print('Added selectable List/Album views for Video and Audio; video album loads thumbnails')

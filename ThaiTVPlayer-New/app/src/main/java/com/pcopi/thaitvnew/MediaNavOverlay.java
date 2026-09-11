package com.pcopi.thaitvnew;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.media3.common.MediaItem;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

public class MediaNavOverlay extends FrameLayout {
    private final Activity a;
    private final LinearLayout page, list;
    private final TextView title, count;
    private final EditText search;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final ArrayList<Item> data = new ArrayList<>();
    private String mode = "browse";
    private final TextView[] nav = new TextView[5];

    public MediaNavOverlay(Activity x) {
        super(x);
        a = x;
        setBackgroundColor(Color.TRANSPARENT);
        page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xff101010);
        page.setVisibility(GONE);
        page.setClickable(true);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, -1);
        pp.setMargins(0, dp(8), 0, dp(64));
        addView(page, pp);

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(0xff1c1c1c);
        TextView back = tv("‹", 32);
        back.setGravity(Gravity.CENTER);
        head.addView(back, new LinearLayout.LayoutParams(dp(52), dp(58)));
        back.setOnClickListener(v -> close());
        title = tv("เรียกดู", 19);
        title.setTypeface(null, 1);
        head.addView(title, new LinearLayout.LayoutParams(0, dp(58), 1));
        count = tv("", 13);
        count.setTextColor(0xffffb74d);
        head.addView(count, new LinearLayout.LayoutParams(dp(90), dp(58)));
        page.addView(head);

        search = new EditText(a);
        search.setSingleLine();
        search.setHint("ค้นหาในเครื่อง…");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xff888888);
        search.setBackgroundColor(0xff181818);
        search.setPadding(dp(16), 0, dp(16), 0);
        page.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int f) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { render(s.toString()); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        ScrollView sv = new ScrollView(a);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ScrollView.LayoutParams(-1, -2));
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = new LinearLayout(a);
        bar.setBackgroundColor(0xff181818);
        String[] n = {"▶\nวีดีโอ", "♫\nเสียง", "▦\nเรียกดู", "☷\nPlaylist", "•••\nอื่นๆ"};
        for (int i = 0; i < 5; i++) {
            final int j = i;
            nav[i] = tv(n[i], 11);
            nav[i].setGravity(Gravity.CENTER);
            bar.addView(nav[i], new LinearLayout.LayoutParams(0, dp(64), 1));
            nav[i].setOnClickListener(v -> choose(j));
        }
        addView(bar, new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM));
    }

    private TextView tv(String s, float z) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(z);
        t.setTextColor(0xffeeeeee);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private boolean portrait() { return getResources().getConfiguration().orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE; }
    public void setEnabledForOrientation(boolean on) { setVisibility(on && portrait() ? VISIBLE : GONE); if (!on) close(); }

    private void choose(int i) {
        if (i == 4) {
            new android.app.AlertDialog.Builder(a).setTitle("อื่นๆ")
                .setItems(new String[]{"รีเฟรช", "เปิดไฟล์ M3U ด้วยตัวเลือกไฟล์", "ปิด"}, (d, w) -> {
                    if (w == 0) scan();
                    else if (w == 1) {
                        Intent z = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        z.addCategory(Intent.CATEGORY_OPENABLE);
                        z.setType("*/*");
                        a.startActivityForResult(z, 7001);
                    } else close();
                }).show();
            return;
        }
        open(i == 0 ? "วีดีโอ" : i == 1 ? "เสียง" : i == 2 ? "เรียกดู" : "Playlist",
             i == 0 ? "video" : i == 1 ? "audio" : i == 2 ? "browse" : "playlist");
    }

    private void open(String t, String m) {
        mode = m;
        title.setText(t);
        search.setHint(m.equals("playlist") ? "ค้นหาไฟล์ M3U…" : "ค้นหาในเครื่อง…");
        page.setVisibility(VISIBLE);
        for (TextView v : nav) v.setTextColor(0xffeeeeee);
        nav[mode.equals("video") ? 0 : mode.equals("audio") ? 1 : mode.equals("browse") ? 2 : 3].setTextColor(0xffff9800);
        scan();
    }

    private void close() { page.setVisibility(GONE); search.setText(""); }

    private void scan() {
        data.clear(); list.removeAllViews(); count.setText("กำลังค้นหา…");
        if (mode.equals("video") && !okV()) { req(false); return; }
        if (mode.equals("audio") && !okA()) { req(true); return; }
        ex.execute(() -> {
            try {
                if (mode.equals("video")) video();
                else if (mode.equals("audio")) audio();
                else if (mode.equals("playlist")) playlist();
                else { video(); audio(); }
            } catch (Exception ignored) {}
            h.post(() -> { count.setText(data.size() + " รายการ"); render(search.getText().toString()); });
        });
    }

    private boolean okV() { return Build.VERSION.SDK_INT < 33 || a.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED || a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED; }
    private boolean okA() { return Build.VERSION.SDK_INT < 33 || a.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED || a.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED; }
    private void req(boolean audio) {
        if (Build.VERSION.SDK_INT >= 33) a.requestPermissions(new String[]{audio ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_MEDIA_VIDEO}, audio ? 9102 : 9101);
        else if (Build.VERSION.SDK_INT >= 23) a.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 9100);
        count.setText("กรุณาอนุญาตการเข้าถึงสื่อ แล้วกดเมนูอีกครั้ง");
    }

    private void video() {
        Cursor c = a.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.MIME_TYPE},
            null, null, MediaStore.Video.Media.DATE_ADDED + " DESC");
        if (c != null) try {
            while (c.moveToNext()) data.add(new Item(c.getString(1), ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0)), "วีดีโอ", c.getString(2)));
        } finally { c.close(); }
    }

    private void audio() {
        Cursor c = a.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.MIME_TYPE},
            null, null, MediaStore.Audio.Media.DATE_ADDED + " DESC");
        if (c != null) try {
            while (c.moveToNext()) data.add(new Item(c.getString(1), ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0)), "เสียง", c.getString(2)));
        } finally { c.close(); }
    }

    private void playlist() {
        Cursor c = a.getContentResolver().query(MediaStore.Files.getContentUri("external"),
            new String[]{MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MIME_TYPE},
            "LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE ? OR LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE ?",
            new String[]{"%.m3u", "%.m3u8"}, MediaStore.Files.FileColumns.DATE_ADDED + " DESC");
        if (c != null) try {
            while (c.moveToNext()) data.add(new Item(c.getString(1), ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), c.getLong(0)), "Playlist", c.getString(2)));
        } finally { c.close(); }
    }

    private void render(String q) {
        list.removeAllViews();
        q = q == null ? "" : q.toLowerCase(Locale.ROOT).trim();
        int n = 0;
        for (Item x : data) if (q.isEmpty() || x.name.toLowerCase(Locale.ROOT).contains(q)) {
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(16), dp(6), dp(10), dp(6));
            TextView ic = tv(x.type.equals("วีดีโอ") ? "▶" : x.type.equals("เสียง") ? "♫" : "☷", 24);
            ic.setTextColor(0xffff9800); ic.setGravity(Gravity.CENTER);
            r.addView(ic, new LinearLayout.LayoutParams(dp(54), dp(64)));
            LinearLayout tx = new LinearLayout(a); tx.setOrientation(LinearLayout.VERTICAL);
            TextView nm = tv(x.name, 16); TextView sm = tv(x.type + (x.mime == null ? "" : "  •  " + x.mime), 12); sm.setTextColor(0xff888888);
            tx.addView(nm, new LinearLayout.LayoutParams(-1, dp(36))); tx.addView(sm, new LinearLayout.LayoutParams(-1, dp(24)));
            r.addView(tx, new LinearLayout.LayoutParams(0, dp(76), 1));
            r.setOnClickListener(v -> openItem(x)); list.addView(r); n++;
        }
        if (n == 0) {
            TextView e = tv(mode.equals("playlist") ? "ไม่พบไฟล์ M3U/M3U8 ในเครื่อง" : "ไม่พบไฟล์ในหมวดนี้", 16);
            e.setGravity(Gravity.CENTER); e.setTextColor(0xff888888); list.addView(e, new LinearLayout.LayoutParams(-1, dp(160)));
        }
    }

    private void openItem(Item x) {
        if (x.type.equals("Playlist")) {
            ex.execute(() -> {
                try (InputStream in = a.getContentResolver().openInputStream(x.uri)) {
                    String s = read(in);
                    h.post(() -> invokeParse(s, x.name));
                } catch (Exception e) { h.post(() -> Toast.makeText(a, "เปิด Playlist ไม่ได้", Toast.LENGTH_SHORT).show()); }
            });
        } else invokePlay(x);
    }

    private String read(InputStream in) throws Exception {
        if (in == null) throw new IllegalStateException("ไฟล์เปิดไม่ได้");
        byte[] b = new byte[8192]; int n; java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        while ((n = in.read(b)) != -1) o.write(b, 0, n); return o.toString("UTF-8");
    }

    private void invokeParse(String s, String n) {
        try {
            Method m = a.getClass().getDeclaredMethod("parseAndShow", String.class, String.class);
            m.setAccessible(true); m.invoke(a, s, "Playlist: " + n); close();
        } catch (Exception e) { Toast.makeText(a, "เปิด Playlist ไม่ได้", Toast.LENGTH_SHORT).show(); }
    }

    private void invokePlay(Item x) {
        try {
            Field f = a.getClass().getDeclaredField("player");
            f.setAccessible(true);
            Object p = f.get(a);
            if (p == null) throw new IllegalStateException("player unavailable");
            MediaItem.Builder b = new MediaItem.Builder().setUri(x.uri);
            String mime = x.mime;
            if (mime == null || mime.isEmpty()) mime = guessMime(x.name, x.type);
            if (mime != null && !mime.isEmpty()) b.setMimeType(mime);
            MediaItem item = b.build();
            Method set = p.getClass().getMethod("setMediaItem", MediaItem.class);
            Method prepare = p.getClass().getMethod("prepare");
            Method play = p.getClass().getMethod("play");
            set.invoke(p, item); prepare.invoke(p); play.invoke(p);
            close();
        } catch (Exception e) {
            Toast.makeText(a, "เปิดไฟล์ไม่ได้: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private String guessMime(String name, String type) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (type.equals("วีดีโอ")) {
            if (n.endsWith(".mp4") || n.endsWith(".m4v")) return "video/mp4";
            if (n.endsWith(".mkv")) return "video/x-matroska";
            if (n.endsWith(".webm")) return "video/webm";
            if (n.endsWith(".avi")) return "video/avi";
            if (n.endsWith(".mov")) return "video/quicktime";
        } else {
            if (n.endsWith(".mp3")) return "audio/mpeg";
            if (n.endsWith(".m4a")) return "audio/mp4";
            if (n.endsWith(".aac")) return "audio/aac";
            if (n.endsWith(".wav")) return "audio/wav";
            if (n.endsWith(".ogg")) return "audio/ogg";
            if (n.endsWith(".flac")) return "audio/flac";
        }
        return null;
    }

    private static class Item {
        String name, type, mime; Uri uri;
        Item(String n, Uri u, String t, String m) { name = n == null ? "ไม่มีชื่อ" : n; uri = u; type = t; mime = m; }
    }
}

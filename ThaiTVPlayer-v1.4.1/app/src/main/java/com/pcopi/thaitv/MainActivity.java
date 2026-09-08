package com.pcopi.thaitv;

import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    static class Channel {
        String name, url, logo;
        Channel(String n, String u, String l){ name=n; url=u; logo=l; }
    }

    final ArrayList<Channel> channels = new ArrayList<>();
    LinearLayout root, listBox;
    FrameLayout playerBox;
    PlayerView playerView;
    ExoPlayer player;
    TextView status;
    Button closePlayer;
    int PICK_M3U = 1001;
    final Handler main = new Handler(Looper.getMainLooper());
    String lastPlaylistUrl = "";
    Uri lastFileUri = null;

    int dp(int n){ return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    TextView text(String s, float z){
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(z); t.setTextColor(Color.DKGRAY);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        return t;
    }
    Button button(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); return b; }

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi(); }

    void buildUi(){
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // Keep the simple v1.2 layout: title + three actions + channel list.
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(4), dp(3), dp(4), 0);
        TextView title = text("Thai TV Player", 19);
        title.setTextColor(Color.BLACK);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button file = button("ไฟล์ M3U");
        Button url = button("URL Playlist");
        Button refresh = button("รีเฟรช Playlist");
        bar.addView(file, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(url, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        status = text("เลือกไฟล์ M3U หรือ URL Playlist เพื่อเริ่มใช้งาน", 14);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        playerBox = new FrameLayout(this);
        playerBox.setBackgroundColor(Color.BLACK);
        playerBox.setVisibility(View.GONE);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerBox.addView(playerView, new FrameLayout.LayoutParams(-1, dp(220)));
        closePlayer = button("✕ ปิดจอ");
        closePlayer.setTextColor(Color.WHITE);
        closePlayer.setBackgroundColor(Color.argb(190,0,0,0));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(100), dp(48), Gravity.TOP|Gravity.END);
        cp.setMargins(0, dp(4), dp(4), 0);
        playerBox.addView(closePlayer, cp);
        root.addView(playerBox, new LinearLayout.LayoutParams(-1, dp(230)));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(listBox);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        file.setOnClickListener(v -> pickFile());
        url.setOnClickListener(v -> askUrl());
        refresh.setOnClickListener(v -> refreshPlaylist());
        closePlayer.setOnClickListener(v -> hidePlayer());
    }

    void pickFile(){
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "text/plain", "text/*", "application/octet-stream",
            "application/vnd.apple.mpegurl", "audio/x-mpegurl",
            "application/x-mpegURL", "application/w3c.mpegURL"
        });
        startActivityForResult(i, PICK_M3U);
    }

    @Override protected void onActivityResult(int r, int c, Intent d){
        super.onActivityResult(r,c,d);
        if(r != PICK_M3U || c != RESULT_OK || d == null || d.getData() == null) return;
        Uri uri = d.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(Exception ignored) {}
        lastFileUri = uri;
        lastPlaylistUrl = "";
        status.setText("กำลังอ่านไฟล์ M3U...");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String s = read(uri);
                main.post(() -> loadText(s, "ไฟล์ M3U"));
            } catch(Exception e) {
                main.post(() -> status.setText("เปิดไฟล์ไม่สำเร็จ: " + safeError(e)));
            }
        });
    }

    String read(Uri uri) throws Exception {
        try(InputStream in = getContentResolver().openInputStream(uri)) { return read(in); }
    }

    String read(InputStream in) throws Exception {
        if(in == null) throw new IOException("ไม่สามารถเปิดไฟล์ได้");
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384]; int n;
        while((n = in.read(b)) > 0) o.write(b,0,n);
        byte[] data = o.toByteArray();
        if(data.length == 0) return "";
        String s = new String(data, StandardCharsets.UTF_8);
        if(s.indexOf('\uFFFD') >= 0) {
            try { s = new String(data, "UTF-16"); } catch(Exception ignored) {}
        }
        return s.replace("\uFEFF", "");
    }

    void askUrl(){
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(33);
        e.setHint("https://.../playlist.m3u");
        new AlertDialog.Builder(this)
            .setTitle("URL Playlist")
            .setView(e)
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("โหลด", (d,w) -> fetchUrl(e.getText().toString().trim()))
            .show();
    }

    void fetchUrl(String u){
        if(u.isEmpty()) { status.setText("กรุณาใส่ URL Playlist"); return; }
        lastPlaylistUrl = u;
        lastFileUri = null;
        status.setText("กำลังโหลด Playlist...");
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(u);
                c = (HttpURLConnection)url.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) ThaiTVPlayer/1.5");
                c.setRequestProperty("Accept", "*/*");
                int code = c.getResponseCode();
                if(code < 200 || code >= 400) throw new IOException("HTTP " + code);
                String s = read(c.getInputStream());
                main.post(() -> loadText(s, "URL Playlist"));
            } catch(Exception e) {
                main.post(() -> status.setText("โหลด URL ไม่สำเร็จ: " + safeError(e)));
            } finally { if(c != null) c.disconnect(); }
        });
    }

    void refreshPlaylist(){
        if(lastFileUri != null){
            status.setText("กำลังรีเฟรชไฟล์ M3U...");
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    String s = read(lastFileUri);
                    main.post(() -> loadText(s, "ไฟล์ M3U"));
                } catch(Exception e){ main.post(() -> status.setText("รีเฟรชไฟล์ไม่สำเร็จ: " + safeError(e))); }
            });
        } else if(!lastPlaylistUrl.isEmpty()) {
            fetchUrl(lastPlaylistUrl);
        } else {
            status.setText("ยังไม่มี Playlist สำหรับรีเฟรช");
        }
    }

    String attr(String line, String key){
        String q = key + "=";
        int p = line.toLowerCase(Locale.ROOT).indexOf(q.toLowerCase(Locale.ROOT));
        if(p < 0) return "";
        p += q.length();
        if(p >= line.length()) return "";
        char quote = line.charAt(p);
        if(quote == '"' || quote == '\'') {
            int e = line.indexOf(quote, p+1);
            return e > p ? line.substring(p+1,e).trim() : "";
        }
        int e = line.indexOf(',', p);
        return (e > p ? line.substring(p,e) : line.substring(p)).trim();
    }

    String cleanName(String n){
        n = n == null ? "ช่องทีวี" : n.trim();
        if(n.isEmpty()) return "ช่องทีวี";
        return n;
    }

    boolean isStreamUrl(String line){
        String s = line.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("http://") || s.startsWith("https://") ||
               s.startsWith("rtmp://") || s.startsWith("rtsp://");
    }

    void loadText(String s, String source){
        channels.clear();
        if(s == null) s = "";
        s = s.replace("\u0000", "").replace("\r\n", "\n").replace('\r','\n').replace("\uFEFF", "");
        String pendingName = null, pendingLogo = "";
        String[] lines = s.split("\n", -1);

        for(String raw : lines){
            String line = raw.trim();
            if(line.isEmpty()) continue;
            if(line.startsWith("#EXTINF")){
                int comma = line.indexOf(',');
                pendingName = comma >= 0 ? cleanName(line.substring(comma+1)) : "ช่องทีวี";
                pendingLogo = attr(line, "tvg-logo");
                if(pendingName.contains(",")) pendingName = cleanName(pendingName.substring(pendingName.lastIndexOf(',')+1));
                continue;
            }
            if(line.startsWith("#")) continue;
            if(isStreamUrl(line)){
                if(pendingName == null) pendingName = "ช่องทีวี";
                channels.add(new Channel(pendingName, line, pendingLogo));
                pendingName = null;
                pendingLogo = "";
            }
        }

        renderList();
        if(channels.isEmpty()) {
            status.setText("ไม่พบช่องใน " + source + " (ตรวจสอบว่าเป็นไฟล์ M3U ที่มี #EXTINF และ URL)");
        } else {
            status.setText("พบ " + channels.size() + " ช่อง");
        }
    }

    void renderList(){
        listBox.removeAllViews();
        for(int i=0;i<channels.size();i++){
            final Channel ch = channels.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(2), dp(8), dp(2));
            ImageView icon = new ImageView(this);
            icon.setImageResource(android.R.drawable.ic_media_play);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            row.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(52)));
            TextView label = text(String.format(Locale.ROOT, "%02d  %s", i+1, ch.name), 16);
            label.setTextColor(Color.DKGRAY);
            row.addView(label, new LinearLayout.LayoutParams(0, dp(56), 1));
            row.setOnClickListener(v -> play(ch));
            listBox.addView(row, new LinearLayout.LayoutParams(-1, dp(62)));
            if(!ch.logo.isEmpty()) loadLogo(ch.logo, icon);
        }
    }

    void loadLogo(String u, ImageView target){
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection)new URL(u).openConnection();
                c.setConnectTimeout(5000); c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0");
                Bitmap b = BitmapFactory.decodeStream(c.getInputStream());
                if(b != null) main.post(() -> target.setImageBitmap(b));
            } catch(Exception ignored) {} finally { if(c != null) c.disconnect(); }
        });
    }

    void play(Channel ch){
        hidePlayer();
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerBox.setVisibility(View.VISIBLE);
        player.addListener(new ExoPlayer.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                status.setText("เปิดช่องไม่ได้: " + safeError(error));
            }
        });
        MediaItem.Builder mb = new MediaItem.Builder().setUri(Uri.parse(ch.url));
        String u = ch.url.toLowerCase(Locale.ROOT);
        if(u.contains(".m3u8") || u.contains("m3u8?")) mb.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(mb.build());
        player.prepare();
        player.play();
        status.setText("กำลังเปิด: " + ch.name);
    }

    String safeError(Throwable e){
        if(e == null) return "ไม่ทราบสาเหตุ";
        String m = e.getMessage();
        return (m == null || m.trim().isEmpty()) ? e.getClass().getSimpleName() : m;
    }

    void hidePlayer(){
        if(player != null){ player.stop(); player.release(); player = null; }
        if(playerView != null) playerView.setPlayer(null);
        if(playerBox != null) playerBox.setVisibility(View.GONE);
    }

    @Override public void onBackPressed(){ if(player != null){ hidePlayer(); return; } super.onBackPressed(); }
    @Override protected void onDestroy(){ if(player != null) player.release(); super.onDestroy(); }

    @Override public void onConfigurationChanged(android.content.res.Configuration c){
        super.onConfigurationChanged(c);
        if(playerBox == null) return;
        boolean land = c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        playerBox.getLayoutParams().height = land ? -1 : dp(230);
        playerBox.requestLayout();
        getWindow().getDecorView().setSystemUiVisibility(land ?
            (View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) : 0);
    }
}

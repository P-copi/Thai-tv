package com.pcopi.thaitvnew;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import java.util.*;

public class ThaiTVApplication extends Application {
    private Activity currentActivity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Activity, VolumeBrightnessOverlay> overlays = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Activity, MediaNavOverlay> mediaOverlays = Collections.synchronizedMap(new WeakHashMap<>());

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityResumed(Activity a) { currentActivity = a; installOverlay(a); installMediaNav(a); }
            public void onActivityCreated(Activity a, Bundle b) {}
            public void onActivityStarted(Activity a) {}
            public void onActivityPaused(Activity a) {}
            public void onActivityStopped(Activity a) {}
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            public void onActivityDestroyed(Activity a) {
                VolumeBrightnessOverlay o = overlays.remove(a);
                if (o != null) o.remove();
                MediaNavOverlay m = mediaOverlays.remove(a);
                if (m != null && m.getParent() instanceof ViewGroup) ((ViewGroup)m.getParent()).removeView(m);
                if (currentActivity == a) currentActivity = null;
            }
        });
        registerComponentCallbacks(new ComponentCallbacks() {
            public void onConfigurationChanged(Configuration c) {
                if (currentActivity != null) main.postDelayed(() -> {
                    installOverlay(currentActivity);
                    installMediaNav(currentActivity);
                }, 250);
            }
            public void onLowMemory() {}
        });
    }

    private void installOverlay(Activity a) {
        boolean land = a.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        VolumeBrightnessOverlay o = overlays.get(a);
        if (!land) {
            if (o != null) o.setOverlayEnabled(false);
            return;
        }
        if (o == null) {
            o = new VolumeBrightnessOverlay(a);
            overlays.put(a, o);
            View v = a.findViewById(android.R.id.content);
            if (v instanceof ViewGroup) ((ViewGroup)v).addView(o, new ViewGroup.LayoutParams(-1, -1));
            else ((ViewGroup)a.getWindow().getDecorView()).addView(o, new ViewGroup.LayoutParams(-1, -1));
        }
        o.setOverlayEnabled(true);
        o.bringToFront();
    }

    private void installMediaNav(Activity a) {
        boolean portrait = a.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
        MediaNavOverlay n = mediaOverlays.get(a);
        if (n == null) {
            n = new MediaNavOverlay(a);
            mediaOverlays.put(a, n);
            View v = a.findViewById(android.R.id.content);
            if (v instanceof ViewGroup) ((ViewGroup)v).addView(n, new ViewGroup.LayoutParams(-1, -1));
            else ((ViewGroup)a.getWindow().getDecorView()).addView(n, new ViewGroup.LayoutParams(-1, -1));
        }
        n.setEnabledForOrientation(portrait);
        n.bringToFront();
    }

    private static class VolumeBrightnessOverlay extends View {
        private final Activity activity;
        private final AudioManager audio;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private float downX, downY, startLevel, shownLevel = -1f;
        private boolean changing, volumeMode;
        private long hideAt;
        private final Runnable hide = () -> {
            if (System.currentTimeMillis() >= hideAt) { shownLevel = -1f; invalidate(); }
        };

        VolumeBrightnessOverlay(Activity a) {
            super(a);
            activity = a;
            audio = (AudioManager)a.getSystemService(Context.AUDIO_SERVICE);
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(true);
            setFocusable(false);
        }

        void setOverlayEnabled(boolean e) {
            setVisibility(e ? VISIBLE : GONE);
            if (!e) { shownLevel = -1f; handler.removeCallbacks(hide); }
        }
        void remove() { handler.removeCallbacks(hide); if (getParent() instanceof ViewGroup) ((ViewGroup)getParent()).removeView(this); }

        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (shownLevel < 0f || !isShown()) return;
            float w = getWidth(), h = getHeight();
            float cx = volumeMode ? w - dp(56) : dp(56);
            float barH = Math.min(h * .40f, dp(240));
            float barW = dp(8);
            float top = (h - barH) / 2f, bottom = top + barH;
            float boxHalf = dp(34);
            paint.setColor(0xC8000000);
            rect.set(cx - boxHalf, top - dp(42), cx + boxHalf, bottom + dp(42));
            c.drawRoundRect(rect, dp(16), dp(16), paint);
            paint.setColor(0x55FFFFFF);
            rect.set(cx - barW/2f, top, cx + barW/2f, bottom);
            c.drawRoundRect(rect, barW/2f, barW/2f, paint);
            paint.setColor(Color.WHITE);
            float fill = bottom - barH * Math.max(0f, Math.min(1f, shownLevel));
            rect.set(cx - barW/2f, fill, cx + barW/2f, bottom);
            c.drawRoundRect(rect, barW/2f, barW/2f, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(14));
            c.drawText(volumeMode ? "VOL" : "BRI", cx, top - dp(10), paint);
            paint.setTextSize(dp(13));
            c.drawText(Math.round(shownLevel * 100f) + "%", cx, bottom + dp(24), paint);
        }

        public boolean onTouchEvent(MotionEvent e) {
            if (getVisibility() != VISIBLE) return false;
            float edge = Math.min(dp(100), getWidth() * .22f);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX(); downY = e.getY(); changing = false;
                    // Keep the landscape close button at the top-right fully clickable.
                    if (downX >= getWidth() - edge && downY <= dp(82)) return false;
                    if (downX <= edge) volumeMode = false;
                    else if (downX >= getWidth() - edge) volumeMode = true;
                    else return false;
                    startLevel = volumeMode ? getVolumeLevel() : getBrightnessLevel();
                    shownLevel = startLevel;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dy = downY - e.getY();
                    if (!changing && Math.abs(dy) >= dp(8)) changing = true;
                    if (changing) {
                        float travel = Math.max(dp(260), getHeight() * .42f);
                        float level = Math.max(0f, Math.min(1f, startLevel + dy / travel));
                        if (volumeMode) setVolumeLevel(level); else setBrightnessLevel(level);
                        shownLevel = level;
                        hideAt = System.currentTimeMillis() + 1000;
                        handler.removeCallbacks(hide); handler.postDelayed(hide, 1050);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    hideAt = System.currentTimeMillis() + 600;
                    handler.removeCallbacks(hide); handler.postDelayed(hide, 650);
                    return true;
                default: return true;
            }
        }

        private float getVolumeLevel() {
            if (audio == null) return 0f;
            int m = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            return m > 0 ? audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float)m : 0f;
        }
        private void setVolumeLevel(float l) {
            if (audio != null) {
                int m = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(l * m), 0);
            }
        }
        private float getBrightnessLevel() {
            float b = activity.getWindow().getAttributes().screenBrightness;
            if (b >= 0f) return b;
            try { return Math.max(0f, Math.min(1f, Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f)); }
            catch (Exception e) { return .5f; }
        }
        private void setBrightnessLevel(float l) {
            Window w = activity.getWindow();
            WindowManager.LayoutParams p = w.getAttributes();
            p.screenBrightness = Math.max(.02f, Math.min(1f, l));
            w.setAttributes(p);
        }
        private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    }
}

package com.pcopi.thaitvnew;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.media3.ui.PlayerView;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class ThaiTVApplication extends Application {
    private Activity currentActivity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Activity, VolumeBrightnessOverlay> overlays = Collections.synchronizedMap(new WeakHashMap<>());

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                currentActivity = activity;
                installOverlay(activity);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {
                VolumeBrightnessOverlay o = overlays.remove(a);
                if (o != null) o.remove();
                if (currentActivity == a) currentActivity = null;
            }
        });
        registerComponentCallbacks(new ComponentCallbacks() {
            @Override public void onConfigurationChanged(Configuration newConfig) {
                if (currentActivity != null) {
                    main.postDelayed(() -> installOverlay(currentActivity), 350);
                }
            }
            @Override public void onLowMemory() {}
        });
    }

    private void installOverlay(Activity activity) {
        boolean landscape = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        VolumeBrightnessOverlay overlay = overlays.get(activity);
        if (!landscape) {
            if (overlay != null) overlay.setEnabled(false);
            return;
        }
        if (overlay == null) {
            overlay = new VolumeBrightnessOverlay(activity);
            overlays.put(activity, overlay);
            FrameLayout decor = findDecorContent(activity);
            if (decor != null) decor.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        }
        overlay.setEnabled(true);
        overlay.bringToFront();
    }

    private FrameLayout findDecorContent(Activity activity) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        for (int i = 0; i < decor.getChildCount(); i++) {
            View child = decor.getChildAt(i);
            if (child instanceof FrameLayout) return (FrameLayout) child;
        }
        return null;
    }

    private static class VolumeBrightnessOverlay extends View {
        private final Activity activity;
        private final AudioManager audio;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bar = new RectF();
        private float downX, downY, lastY;
        private float startLevel;
        private boolean changing;
        private boolean volumeMode;
        private float shownLevel;
        private long hideAt;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable hideRunnable = () -> { if (System.currentTimeMillis() >= hideAt) { shownLevel = -1f; invalidate(); } };

        VolumeBrightnessOverlay(Activity a) {
            super(a);
            activity = a;
            audio = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(false);
            setFocusable(false);
        }

        void setEnabled(boolean enabled) {
            setVisibility(enabled ? VISIBLE : GONE);
        }

        void remove() {
            ViewParentCompat.remove(this);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (shownLevel < 0f || !isShown()) return;
            float w = getWidth(), h = getHeight();
            float cx = volumeMode ? w * 0.78f : w * 0.22f;
            float barH = Math.min(h * 0.42f, dp(260));
            float barW = dp(10);
            float top = (h - barH) / 2f;
            float bottom = top + barH;

            paint.setColor(0xB8000000);
            bar.set(cx - dp(42), top - dp(58), cx + dp(42), bottom + dp(58));
            c.drawRoundRect(bar, dp(22), dp(22), paint);

            paint.setColor(0x66FFFFFF);
            bar.set(cx - barW / 2f, top, cx + barW / 2f, bottom);
            c.drawRoundRect(bar, barW / 2f, barW / 2f, paint);

            paint.setColor(Color.WHITE);
            float fillTop = bottom - barH * Math.max(0f, Math.min(1f, shownLevel));
            bar.set(cx - barW / 2f, fillTop, cx + barW / 2f, bottom);
            c.drawRoundRect(bar, barW / 2f, barW / 2f, paint);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(30));
            c.drawText(volumeMode ? "🔊" : "☀", cx, top - dp(16), paint);
            paint.setTextSize(dp(18));
            c.drawText(Math.round(shownLevel * 100f) + "%", cx, bottom + dp(32), paint);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (getVisibility() != VISIBLE) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = lastY = e.getY();
                    changing = false;
                    volumeMode = downX >= getWidth() / 2f;
                    startLevel = volumeMode ? getVolumeLevel() : getBrightnessLevel();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float totalDy = downY - e.getY();
                    if (!changing && Math.abs(totalDy) >= dp(18)) changing = true;
                    if (changing) {
                        float delta = (lastY - e.getY()) / Math.max(dp(260), getHeight() * 0.42f);
                        lastY = e.getY();
                        float level = Math.max(0f, Math.min(1f, startLevel + (downY - e.getY()) / Math.max(dp(260), getHeight() * 0.42f)));
                        if (volumeMode) setVolumeLevel(level); else setBrightnessLevel(level);
                        shownLevel = level;
                        hideAt = System.currentTimeMillis() + 900;
                        handler.removeCallbacks(hideRunnable);
                        handler.postDelayed(hideRunnable, 950);
                        invalidate();
                        return true;
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (changing) { hideAt = System.currentTimeMillis() + 700; handler.postDelayed(hideRunnable, 750); return true; }
                    return false;
                default: return false;
            }
        }

        private float getVolumeLevel() {
            if (audio == null) return 0f;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            return max > 0 ? current / (float) max : 0f;
        }

        private void setVolumeLevel(float level) {
            if (audio == null) return;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int value = Math.round(level * max);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
        }

        private float getBrightnessLevel() {
            Window w = activity.getWindow();
            float b = w.getAttributes().screenBrightness;
            if (b >= 0f) return b;
            try {
                int system = Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                return Math.max(0f, Math.min(1f, system / 255f));
            } catch (Exception ignored) { return 0.5f; }
        }

        private void setBrightnessLevel(float level) {
            Window w = activity.getWindow();
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.screenBrightness = Math.max(0.02f, Math.min(1f, level));
            w.setAttributes(lp);
        }

        private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    }

    private static class ViewParentCompat {
        static void remove(View v) {
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        }
    }
}

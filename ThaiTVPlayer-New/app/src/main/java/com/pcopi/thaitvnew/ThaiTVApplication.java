package com.pcopi.thaitvnew;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.media3.ui.PlayerView;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class ThaiTVApplication extends Application {
    private Activity currentActivity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<PlayerView, Boolean> installed = Collections.synchronizedMap(new WeakHashMap<>());

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                currentActivity = activity;
                installVolumeGesture(activity);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) { if (currentActivity == a) currentActivity = null; }
        });
        registerComponentCallbacks(new ComponentCallbacks() {
            @Override public void onConfigurationChanged(Configuration newConfig) {
                if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && currentActivity != null) {
                    main.postDelayed(() -> installVolumeGesture(currentActivity), 250);
                }
            }
            @Override public void onLowMemory() {}
        });
    }

    private void installVolumeGesture(Activity activity) {
        if (activity.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) return;
        PlayerView playerView = findPlayerView(activity.getWindow().getDecorView());
        if (playerView == null || installed.containsKey(playerView)) return;
        installed.put(playerView, Boolean.TRUE);
        playerView.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            float startVolume;
            boolean changingVolume;

            @Override public boolean onTouch(View v, MotionEvent event) {
                if (playerView.getPlayer() == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = event.getY();
                        startVolume = playerView.getPlayer().getVolume();
                        changingVolume = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        float dy = downY - event.getY();
                        if (Math.abs(dy) > 12f) changingVolume = true;
                        if (changingVolume) {
                            float change = dy / Math.max(1f, v.getHeight());
                            float volume = Math.max(0f, Math.min(1f, startVolume + change));
                            playerView.getPlayer().setVolume(volume);
                            return true;
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return changingVolume;
                    default:
                        return false;
                }
            }
        });
    }

    private PlayerView findPlayerView(View view) {
        if (view instanceof PlayerView) return (PlayerView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                PlayerView found = findPlayerView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
